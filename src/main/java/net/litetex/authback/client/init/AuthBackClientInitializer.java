package net.litetex.authback.client.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.shared.config.Configuration;
import net.litetex.authback.shared.config.FileConfiguration;


public class AuthBackClientInitializer implements ClientModInitializer
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackClientInitializer.class);
	
	@Override
	public void onInitializeClient()
	{
		AuthBackClient.setInstance(new AuthBackClient(
			Configuration.combining(
				new FileConfiguration(FabricLoader.getInstance().getConfigDir().resolve("authback-client.json"))
			)
		));
		
		LOG.debug("Initialized");
	}
}
