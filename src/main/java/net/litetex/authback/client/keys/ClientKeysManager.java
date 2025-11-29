package net.litetex.authback.client.keys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.litetex.authback.shared.json.JSONSerializer;
import net.minecraft.client.Minecraft;


public class ClientKeysManager
{
	private static final Logger LOG = LoggerFactory.getLogger(ClientKeysManager.class);
	
	private final Path keyStatesFile;
	
	private KeyStates keyStates;
	private KeyPair keyPair;
	
	public ClientKeysManager(final Path authbackDir)
	{
		this.keyStatesFile = authbackDir.resolve("key-states.json");
		
		CompletableFuture.runAsync(this::init);
	}
	
	private void init()
	{
		final long startMs = System.currentTimeMillis();
		
		try
		{
			this.readKeyStatesFromFile();
			this.loadCurrentKeypair();
			this.saveKeyStatesFileAsync();
		}
		catch(final Exception ex)
		{
			LOG.error("Initialization failed", ex);
		}
		finally
		{
			LOG.debug("Initialization finished, took {}ms", System.currentTimeMillis() - startMs);
		}
	}
	
	public KeyPair currentKeyPair()
	{
		return Objects.requireNonNull(this.keyPair, "No keypair");
	}
	
	public void regenerate()
	{
		this.keyStates.getV1().remove(Minecraft.getInstance().getUser().getProfileId().toString());
		this.loadCurrentKeypair();
		this.saveKeyStatesFileAsync();
	}
	
	private void loadCurrentKeypair()
	{
		this.keyPair = new KeyPairReaderOrCreator(
			this.keyStates.getV1(),
			Minecraft.getInstance().getUser().getProfileId().toString())
			.getOrCreateKeyPair();
	}
	
	private void readKeyStatesFromFile()
	{
		if(!Files.exists(this.keyStatesFile))
		{
			this.keyStates = new KeyStates();
			return;
		}
		
		try
		{
			this.keyStates = JSONSerializer.GSON.fromJson(Files.readString(this.keyStatesFile), KeyStates.class);
		}
		catch(final Exception ex)
		{
			LOG.warn("Failed to read keyStatesFile['{}']", this.keyStatesFile, ex);
			this.keyStates = new KeyStates();
		}
	}
	
	private void saveKeyStatesFileAsync()
	{
		CompletableFuture.runAsync(this::saveKeyStatesFile);
	}
	
	private void saveKeyStatesFile()
	{
		try
		{
			Files.writeString(this.keyStatesFile, JSONSerializer.GSON.toJson(this.keyStates));
		}
		catch(final Exception ex)
		{
			LOG.warn("Failed to write keyStatesFile['{}']", this.keyStatesFile, ex);
		}
	}
	
	static class KeyStates
	{
		private Map<String, KeyState> v1 = new HashMap<>();
		
		public Map<String, KeyState> getV1()
		{
			return this.v1;
		}
		
		public void setV1(final Map<String, KeyState> v1)
		{
			this.v1 = v1;
		}
	}
}
