package net.litetex.authback.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.litetex.authback.client.config.AuthBackClientConfig;
import net.litetex.authback.client.keys.ClientKeysManager;
import net.litetex.authback.client.network.AuthBackClientNetworking;
import net.litetex.authback.shared.AuthBack;
import net.litetex.authback.shared.external.org.apache.commons.codec.binary.Hex;


public class AuthBackClient extends AuthBack
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackClient.class);
	
	private static AuthBackClient instance;
	
	public static AuthBackClient instance()
	{
		return instance;
	}
	
	public static AuthBackClient ensureInstance()
	{
		if(AuthBackClient.instance == null)
		{
			AuthBackClient.instance = new AuthBackClient();
		}
		return AuthBackClient.instance;
	}
	
	private final ClientKeysManager clientKeysManager;
	private final AuthBackClientConfig config;
	
	protected AuthBackClient()
	{
		super("client");
		this.clientKeysManager = new ClientKeysManager(this.authbackDir);
		
		this.config = new AuthBackClientConfig(this.lowLevelConfig);
		
		LOG.debug("Created");
	}
	
	public void initialize()
	{
		// Create and setup
		new AuthBackClientNetworking(this.clientKeysManager);
		
		LOG.debug("Initialized");
	}
	
	public AuthBackClientConfig config()
	{
		return this.config;
	}
	
	public String currentPublicKeyHex()
	{
		return Hex.encodeHexString(this.clientKeysManager.currentKeyPair().getPublic().getEncoded());
	}
	
	public void regenerateKeys()
	{
		this.clientKeysManager.regenerate();
	}
}
