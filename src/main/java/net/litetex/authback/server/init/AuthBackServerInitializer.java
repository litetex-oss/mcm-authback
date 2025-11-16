package net.litetex.authback.server.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.litetex.authback.server.AuthBackServer;
import net.litetex.authback.shared.config.Configuration;
import net.litetex.authback.shared.config.FileConfiguration;
import net.litetex.authback.shared.config.RuntimeConfiguration;


public class AuthBackServerInitializer implements DedicatedServerModInitializer
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackServerInitializer.class);
	
	@Override
	public void onInitializeServer()
	{
		AuthBackServer.setInstance(new AuthBackServer(
			Configuration.combining(
				RuntimeConfiguration.environmentVariables("AUTHBACK_"),
				RuntimeConfiguration.systemProperties("authback"),
				new FileConfiguration(FabricLoader.getInstance().getConfigDir().resolve("authback-server.json"))
			)
		));
		LOG.debug("Initialized");
	}
}
