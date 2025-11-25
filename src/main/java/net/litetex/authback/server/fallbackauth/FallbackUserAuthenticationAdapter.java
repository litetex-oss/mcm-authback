package net.litetex.authback.server.fallbackauth;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.function.Consumer;

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
import net.litetex.authback.shared.network.login.LoginCompatibility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;


public class FallbackUserAuthenticationAdapter
{
	private static final Logger LOG = LoggerFactory.getLogger(FallbackUserAuthenticationAdapter.class);
	
	private final ServerProfilePublicKeysManager serverProfilePublicKeysManager;
	private final GameProfileCacheManager gameProfileCacheManager;
	@Nullable
	private final FallbackAuthRateLimiter rateLimiter;
	
	public FallbackUserAuthenticationAdapter(
		final ServerProfilePublicKeysManager serverProfilePublicKeysManager,
		final GameProfileCacheManager gameProfileCacheManager,
		@Nullable final FallbackAuthRateLimiter rateLimiter)
	{
		this.serverProfilePublicKeysManager = serverProfilePublicKeysManager;
		this.gameProfileCacheManager = gameProfileCacheManager;
		this.rateLimiter = rateLimiter;
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
		LOG.debug("Trying fallback auth for username={}", requestedUsername);
		
		final GameProfile gameProfile = this.gameProfileCacheManager.findByName(requestedUsername);
		if(gameProfile == null)
		{
			LOG.debug("Unable to find matching profile");
			defaultAction.run();
			return;
		}
		if(!this.serverProfilePublicKeysManager.hasAnyKeyQuickCheck(gameProfile.id()))
		{
			LOG.debug("No public key");
			defaultAction.run();
			return;
		}
		
		final byte[] challenge = SecureRandomByteArrayCreator.create(4);
		
		final FriendlyByteBuf requestBuf = new FriendlyByteBuf(Unpooled.buffer());
		requestBuf.writeInt(LoginCompatibility.S2C);
		requestBuf.writeByteArray(challenge);
		
		ServerLoginNetworking.registerReceiver(
			loginPacketListener,
			ChannelNames.FALLBACK_AUTH,
			(server, handler, understood, buf, synchronizer, responseSender) -> {
				if(!understood)
				{
					LOG.debug("Client did not understand fallback auth - disconnecting");
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
	
	private boolean rateLimitExceeded(
		final ServerLoginPacketListenerImpl loginPacketListener,
		final Runnable defaultAction,
		final Consumer<String> customDisconnectAction)
	{
		if(this.rateLimiter == null)
		{
			// Ratelimiter is disabled
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
		final int clientVersion = buf.readInt();
		if(clientVersion != LoginCompatibility.C2S)
		{
			customDisconnectAction.accept(
				"Compatibility mismatch server=" + LoginCompatibility.C2S + ", client=" + clientVersion);
			return;
		}
		
		final byte[] signature = buf.readByteArray();
		final byte[] publicKeyEncoded = buf.readByteArray();
		
		final PublicKey publicKey =
			this.serverProfilePublicKeysManager.find(gameProfile.id(), publicKeyEncoded);
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
