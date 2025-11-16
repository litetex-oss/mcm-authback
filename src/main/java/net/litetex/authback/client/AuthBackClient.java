package net.litetex.authback.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.litetex.authback.client.keys.KeyPairManager;
import net.litetex.authback.client.state.KeyStates;
import net.litetex.authback.shared.config.Configuration;
import net.litetex.authback.shared.crypto.Ed25519Signature;
import net.litetex.authback.shared.json.JSONSerializer;
import net.litetex.authback.shared.network.configuration.ConfigurationRegistrySetup;
import net.litetex.authback.shared.network.configuration.SyncPayloadC2S;
import net.litetex.authback.shared.network.configuration.SyncPayloadS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;


public class AuthBackClient
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackClient.class);
	
	private static AuthBackClient instance;
	
	public static AuthBackClient instance()
	{
		return instance;
	}
	
	public static void setInstance(final AuthBackClient instance)
	{
		AuthBackClient.instance = instance;
	}
	
	private final Configuration config;
	
	private final KeyPair keyPair;
	
	public AuthBackClient(final Configuration config)
	{
		this.config = config;
		
		final Path authbackDir = this.ensureAuthbackDir(config);
		
		final Path keyStatesFile = authbackDir.resolve("key-states.json");
		final KeyStates keyStates = this.readKeyStatesFile(keyStatesFile);
		
		this.keyPair = new KeyPairManager(
			keyStates.getV1(),
			Minecraft.getInstance().getUser().getProfileId().toString())
			.getOrCreateKeyPair();
		
		this.saveKeyStatesFile(keyStatesFile, keyStates);
		
		ConfigurationRegistrySetup.setup();
		
		ClientConfigurationNetworking.registerGlobalReceiver(
			SyncPayloadS2C.ID,
			(payload, context) -> {
				if(!ClientConfigurationNetworking.canSend(SyncPayloadC2S.ID))
				{
					LOG.debug("Unable to send {}", SyncPayloadC2S.ID);
					return;
				}
				
				context.networkHandler().send(new ServerboundCustomPayloadPacket(new SyncPayloadC2S(
					Ed25519Signature.createSignature(payload.challenge(), this.keyPair.getPrivate()),
					this.keyPair.getPublic().getEncoded()
				)));
			});
		
		LOG.debug("Done");
	}
	
	private Path ensureAuthbackDir(final Configuration config)
	{
		final Path dir = Optional.ofNullable(config.getString("authback-client-dir", null))
			.map(Paths::get)
			.orElseGet(() -> FabricLoader.getInstance().getGameDir().resolve(".authback-client"));
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
	
	private KeyStates readKeyStatesFile(final Path keyStatesFile)
	{
		if(!Files.exists(keyStatesFile))
		{
			return new KeyStates();
		}
		
		try
		{
			return JSONSerializer.GSON.fromJson(Files.readString(keyStatesFile), KeyStates.class);
		}
		catch(final Exception ex)
		{
			LOG.warn("Failed to read keyStatesFile['{}']", keyStatesFile, ex);
			return new KeyStates();
		}
	}
	
	private void saveKeyStatesFile(final Path keyStatesFile, final KeyStates keyStates)
	{
		try
		{
			Files.writeString(keyStatesFile, JSONSerializer.GSON.toJson(keyStates));
		}
		catch(final Exception ex)
		{
			LOG.warn("Failed to write keyStatesFile['{}']", keyStatesFile, ex);
		}
	}
}
