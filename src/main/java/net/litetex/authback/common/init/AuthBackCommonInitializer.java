package net.litetex.authback.common.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.litetex.authback.common.AuthBackCommon;


public class AuthBackCommonInitializer implements PreLaunchEntrypoint
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackCommonInitializer.class);
	
	@Override
	public void onPreLaunch()
	{
		final String envType = FabricLoader.getInstance().getEnvironmentType().name().toLowerCase();
		AuthBackCommon.setInstance(new AuthBackCommon(envType));
		
		LOG.debug("Initialized");
	}
}
