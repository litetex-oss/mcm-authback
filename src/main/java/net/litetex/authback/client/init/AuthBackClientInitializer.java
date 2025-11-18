package net.litetex.authback.client.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.litetex.authback.client.AuthBackClient;


public class AuthBackClientInitializer implements ClientModInitializer
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackClientInitializer.class);
	
	@Override
	public void onInitializeClient()
	{
		AuthBackClient.setInstance(new AuthBackClient());
		
		LOG.debug("Initialized");
	}
}
