package net.litetex.authback.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.litetex.authback.server.keys.ServerProfilePublicKeysManager;
import net.litetex.authback.shared.config.Configuration;
import net.litetex.authback.shared.crypto.Ed25519KeyDecoder;
import net.litetex.authback.shared.crypto.Ed25519Signature;
import net.litetex.authback.shared.crypto.SecureRandomByteArrayCreator;
import net.litetex.authback.shared.network.configuration.ConfigurationRegistrySetup;
import net.litetex.authback.shared.network.configuration.SyncPayloadC2S;
import net.litetex.authback.shared.network.configuration.SyncPayloadS2C;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;


public class AuthBackServer
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
	
	private final Configuration config;
	private final ServerProfilePublicKeysManager serverProfilePublicKeysManager;
	
	public AuthBackServer(final Configuration config)
	{
		this.config = config;
		
		final Path authbackDir = this.ensureAuthbackDir(config);
		this.serverProfilePublicKeysManager = new ServerProfilePublicKeysManager(
			authbackDir.resolve("profile-keys.json"),
			config.getInteger("keys.max-keys-per-user", 5),
			Duration.ofDays(config.getInteger("keys.delete-after-unused-days", 90))
		);
		
		// ServerLoginNetworking.registerGlobalReceiver(
		// 	ResourceLocation.tryBuild("authback", "info"),
		// 	(server, handler, understood, buf, synchronizer, responseSender) -> {
		// 		if(!understood)
		// 		{
		// 			return;
		// 		}
		// 	});
		
		ConfigurationRegistrySetup.setup();
		
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			final GameProfile profile = handler.getOwner();
			
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
				
				this.serverProfilePublicKeysManager.syncFromClient(profile, payload.publicKey(), publicKey);
			}
		);
	}
	
	private Path ensureAuthbackDir(final Configuration config)
	{
		final Path dir = Optional.ofNullable(config.getString("authback-dir", null))
			.map(Paths::get)
			.orElseGet(() -> FabricLoader.getInstance().getGameDir().resolve(".authback"));
		if(!Files.exists(dir))
		{
			try
			{
				Files.createDirectories(dir);
			}
			catch(final IOException e)
			{
				throw new UncheckedIOException(e);
			}
		}
		return dir;
	}
}
