package net.litetex.authback.server.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.litetex.authback.server.AuthBackServer;


public class AuthBackServerInitializer implements DedicatedServerModInitializer
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackServerInitializer.class);
	
	@Override
	public void onInitializeServer()
	{
		AuthBackServer.setInstance(new AuthBackServer());
		LOG.debug("Initialized");
	}
}
