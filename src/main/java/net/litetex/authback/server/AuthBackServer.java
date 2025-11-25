package net.litetex.authback.server;

import java.security.PublicKey;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.litetex.authback.common.AuthBackCommon;
import net.litetex.authback.common.gameprofile.GameProfileCacheManager;
import net.litetex.authback.server.command.FallbackCommand;
import net.litetex.authback.server.fallbackauth.FallbackAuthRateLimiter;
import net.litetex.authback.server.fallbackauth.FallbackUserAuthenticationAdapter;
import net.litetex.authback.server.keys.ServerProfilePublicKeysManager;
import net.litetex.authback.shared.AuthBack;
import net.litetex.authback.shared.crypto.Ed25519KeyDecoder;
import net.litetex.authback.shared.crypto.Ed25519Signature;
import net.litetex.authback.shared.crypto.SecureRandomByteArrayCreator;
import net.litetex.authback.shared.network.configuration.ConfigurationRegistrySetup;
import net.litetex.authback.shared.network.configuration.SyncPayloadC2S;
import net.litetex.authback.shared.network.configuration.SyncPayloadS2C;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;


public class AuthBackServer extends AuthBack
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackServer.class);
	
	private static AuthBackServer instance;
	
	public static AuthBackServer instance()
	{
		return instance;
	}
	
	public static void setInstance(final AuthBackServer instance)
	{
		AuthBackServer.instance = instance;
	}
	
	private final ServerProfilePublicKeysManager serverProfilePublicKeysManager;
	private final GameProfileCacheManager gameProfileCacheManager;
	private final FallbackUserAuthenticationAdapter fallbackUserAuthenticationAdapter;
	
	// Temporarily marks connections that should not execute an up-to-date check
	// This is the case when a connection did log in using fallback auth
	private final Set<Connection> connectionsToSkipUpToDateCheck =
		Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
	
	// Also allow fallback auth when authServers are available for the server
	private final boolean alwaysAllowFallbackAuth;
	
	// Requires calling the API
	// Only needed when updating extremely ancient server versions (before 1.7.6 - 2014-04)
	private final boolean skipOldUserConversion;
	
	@SuppressWarnings("checkstyle:MagicNumber")
	public AuthBackServer()
	{
		super("server");
		
		this.serverProfilePublicKeysManager = new ServerProfilePublicKeysManager(
			this.authbackDir.resolve("profiles-public-keys.json"),
			this.config.getInteger("keys.max-keys-per-user", 5),
			// When a player changes their username the name will be unavailable for 37 days
			Duration.ofDays(this.config.getInteger("keys.delete-after-unused-days", 36))
		);
		this.gameProfileCacheManager = AuthBackCommon.instance().gameProfileCacheManager();
		this.fallbackUserAuthenticationAdapter = new FallbackUserAuthenticationAdapter(
			this.serverProfilePublicKeysManager,
			this.gameProfileCacheManager,
			FallbackAuthRateLimiter.create(this.config)
		);
		this.alwaysAllowFallbackAuth = this.config.getBoolean("fallback-auth.allow-always", true);
		
		this.skipOldUserConversion = this.config.getBoolean("skip-old-user-conversion", true);
		
		this.setupProtoConfiguration();
		
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
			new FallbackCommand(this.serverProfilePublicKeysManager, this.gameProfileCacheManager)
				.register(dispatcher));
		
		LOG.debug("Initialized");
	}
	
	private void setupProtoConfiguration()
	{
		ConfigurationRegistrySetup.setup();
		
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			final GameProfile profile = handler.getOwner();
			
			if(this.connectionsToSkipUpToDateCheck.remove(handler.connection))
			{
				LOG.debug("Skipping up-to-date check for {}", profile.id());
				return;
			}
			
			if(!ServerConfigurationNetworking.canSend(handler, SyncPayloadS2C.ID))
			{
				LOG.debug("Unable to send {} to {}", SyncPayloadS2C.ID, profile.id());
				return;
			}
			
			final byte[] challenge = SecureRandomByteArrayCreator.create(4);
			
			this.registerUpToDateCheckPacketReceiver(handler, challenge, profile);
			
			handler.send(new ClientboundCustomPayloadPacket(new SyncPayloadS2C(challenge)));
		});
	}
	
	private void registerUpToDateCheckPacketReceiver(
		final ServerConfigurationPacketListenerImpl originalHandler,
		final byte[] challenge,
		final GameProfile profile)
	{
		ServerConfigurationNetworking.registerReceiver(
			originalHandler,
			SyncPayloadC2S.ID,
			(payload, context) -> {
				final PublicKey publicKey = new Ed25519KeyDecoder().decodePublic(payload.publicKey());
				
				if(!Ed25519Signature.isValidSignature(challenge, payload.signature(), publicKey))
				{
					LOG.debug("Received invalid signature from {}", profile.id());
					return;
				}
				
				this.serverProfilePublicKeysManager.add(profile.id(), payload.publicKey(), publicKey);
			}
		);
	}
	
	public void handleJoinSuccess(final GameProfile profile)
	{
		this.gameProfileCacheManager.add(profile);
	}
	
	public void doFallbackAuth(
		final ServerLoginPacketListenerImpl loginPacketListener,
		final Runnable defaultAction,
		final Consumer<String> customDisconnectAction,
		final Consumer<GameProfile> successAction)
	{
		this.fallbackUserAuthenticationAdapter.doFallbackAuth(
			loginPacketListener,
			defaultAction,
			customDisconnectAction,
			profile -> {
				this.connectionsToSkipUpToDateCheck.add(loginPacketListener.connection);
				successAction.accept(profile);
			});
	}
	
	public boolean isAlwaysAllowFallbackAuth()
	{
		return this.alwaysAllowFallbackAuth;
	}
	
	public boolean isSkipOldUserConversion()
	{
		return this.skipOldUserConversion;
	}
}
