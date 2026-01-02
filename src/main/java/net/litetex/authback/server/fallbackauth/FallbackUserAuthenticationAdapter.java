package net.litetex.authback.server.fallbackauth;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.litetex.authback.common.gameprofile.GameProfileCacheManager;
import net.litetex.authback.server.keys.ServerProfilePublicKeysManager;
import net.litetex.authback.shared.crypto.Ed25519Signature;
import net.litetex.authback.shared.crypto.SecureRandomByteArrayCreator;
import net.litetex.authback.shared.network.ChannelNames;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.StringUtil;


public class FallbackUserAuthenticationAdapter
{
	private static final Logger LOG = LoggerFactory.getLogger(FallbackUserAuthenticationAdapter.class);
	
	private final Supplier<ServerProfilePublicKeysManager> serverProfilePublicKeysManagerSupplier;
	private final Supplier<GameProfileCacheManager> gameProfileCacheManagerSupplier;
	@Nullable
	private final FallbackAuthRateLimiter rateLimiter;
	
	public FallbackUserAuthenticationAdapter(
		final Supplier<ServerProfilePublicKeysManager> serverProfilePublicKeysManagerSupplier,
		final Supplier<GameProfileCacheManager> gameProfileCacheManagerSupplier,
		@Nullable final FallbackAuthRateLimiter rateLimiter)
	{
		this.serverProfilePublicKeysManagerSupplier = serverProfilePublicKeysManagerSupplier;
		this.gameProfileCacheManagerSupplier = gameProfileCacheManagerSupplier;
		this.rateLimiter = rateLimiter;
	}
	
	private ServerProfilePublicKeysManager serverProfilePublicKeysManager()
	{
		return this.serverProfilePublicKeysManagerSupplier.get();
	}
	
	private GameProfileCacheManager gameProfileCacheManager()
	{
		return this.gameProfileCacheManagerSupplier.get();
	}
	
	public void doFallbackAuth(
		final ServerLoginPacketListenerImpl loginPacketListener,
		final Runnable defaultAction,
		final Consumer<String> customDisconnectAction,
		final Consumer<GameProfile> successAction)
	{
		try
		{
			this.handleFallbackAuthS2C(loginPacketListener, defaultAction, customDisconnectAction, successAction);
		}
		catch(final Exception ex)
		{
			LOG.error("Unexpected error during fallback auth S2C process", ex);
			customDisconnectAction.accept("Internal fallback error");
		}
	}
	
	private void handleFallbackAuthS2C(
		final ServerLoginPacketListenerImpl loginPacketListener,
		final Runnable defaultAction,
		final Consumer<String> customDisconnectAction,
		final Consumer<GameProfile> successAction)
	{
		if(this.rateLimitExceeded(loginPacketListener, defaultAction, customDisconnectAction))
		{
			return;
		}
		
		final String requestedUsername = loginPacketListener.requestedUsername;
		LOG.info("Trying fallback auth for username={}", requestedUsername);
		if(requestedUsername == null || requestedUsername.isEmpty() || !StringUtil.isValidPlayerName(requestedUsername))
		{
			LOG.info("Aborting due to invalid username={}", requestedUsername);
			defaultAction.run();
			return;
		}
		
		final GameProfile gameProfile = this.getGameProfileFor(loginPacketListener, requestedUsername);
		if(gameProfile == null)
		{
			LOG.info("Unable to find matching profile for username={}", requestedUsername);
			defaultAction.run();
			return;
		}
		if(!this.serverProfilePublicKeysManager().hasAnyKeyQuickCheck(gameProfile.id()))
		{
			LOG.info("No public key for {}", gameProfile.id());
			defaultAction.run();
			return;
		}
		
		final byte[] challenge = SecureRandomByteArrayCreator.create(4);
		
		final FriendlyByteBuf requestBuf = new FriendlyByteBuf(Unpooled.buffer());
		requestBuf.writeByteArray(challenge);
		
		ServerLoginNetworking.registerReceiver(
			loginPacketListener,
			ChannelNames.FALLBACK_AUTH,
			(server, handler, understood, buf, synchronizer, responseSender) -> {
				if(!understood)
				{
					LOG.info("Client[id={}] did not understand fallback auth - disconnecting", gameProfile.id());
					defaultAction.run();
					return;
				}
				
				try
				{
					this.handleFallbackAuthC2S(customDisconnectAction, successAction, buf, gameProfile, challenge);
				}
				catch(final Exception ex)
				{
					LOG.error("Unexpected error during fallback auth C2S process", ex);
					customDisconnectAction.accept("Internal fallback error");
				}
			});
		
		ServerNetworkingImpl.getAddon(loginPacketListener)
			.sendPacket(ChannelNames.FALLBACK_AUTH, requestBuf);
	}
	
	private GameProfile getGameProfileFor(
		final ServerLoginPacketListenerImpl loginPacketListener,
		final String requestedUsername)
	{
		final GameProfile profile = this.gameProfileCacheManager().findByName(requestedUsername);
		if(profile != null)
		{
			return profile;
		}
		
		LOG.debug("Failed to find internally cached game profile[name={}], trying nameToIdCache", requestedUsername);
		
		return loginPacketListener.server.services().nameToIdCache().get(requestedUsername)
			// nameToIdCache Lookup is case-insensitive
			// -> Ensure that the requestUsername EXACTLY matches to prevent conflicts
			.filter(nameAndId -> nameAndId.name().equals(requestedUsername))
			.map(nameAndId -> {
				LOG.warn(
					"Failed to find cached game profile[name={}], but was able to use nameToIdCache. "
						+ "Creating temporary profile[uuid={}] without profile properties!",
					requestedUsername,
					nameAndId.id());
				return new GameProfile(nameAndId.id(), nameAndId.name());
			})
			.orElse(null);
	}
	
	private boolean rateLimitExceeded(
		final ServerLoginPacketListenerImpl loginPacketListener,
		final Runnable defaultAction,
		final Consumer<String> customDisconnectAction)
	{
		if(this.rateLimiter == null)
		{
			// Rate Limiter is disabled
			return false;
		}
		
		final SocketAddress remoteSocketAddress = loginPacketListener.connection.getRemoteAddress();
		if(!(remoteSocketAddress instanceof final InetSocketAddress inetSocketAddr))
		{
			LOG.warn("Failed handle type of remoteAddress {}", remoteSocketAddress);
			defaultAction.run();
			return true;
		}
		
		final InetAddress address = inetSocketAddr.getAddress();
		if(this.rateLimiter.isAddressRateLimited(address))
		{
			LOG.debug("Address exceeded rate limit: {}", address);
			customDisconnectAction.accept("Too many requests");
			return true;
		}
		return false;
	}
	
	private void handleFallbackAuthC2S(
		final Consumer<String> customDisconnectAction,
		final Consumer<GameProfile> successAction,
		final FriendlyByteBuf buf,
		final GameProfile gameProfile,
		final byte[] challenge)
	{
		final byte[] signature = buf.readByteArray();
		final byte[] publicKeyEncoded = buf.readByteArray();
		
		final PublicKey publicKey =
			this.serverProfilePublicKeysManager().find(gameProfile.id(), publicKeyEncoded);
		if(publicKey == null)
		{
			customDisconnectAction.accept("Received invalid public key");
			return;
		}
		
		if(!Ed25519Signature.isValidSignature(challenge, signature, publicKey))
		{
			customDisconnectAction.accept("Received invalid signature");
			return;
		}
		successAction.accept(gameProfile);
	}
}
