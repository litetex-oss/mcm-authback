package net.litetex.authback.common.init;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.litetex.authback.common.AuthBackCommon;


public class AuthBackCommonInitializer implements PreLaunchEntrypoint
{
	@Override
	public void onPreLaunch()
	{
		final String envType = FabricLoader.getInstance().getEnvironmentType().name().toLowerCase();
		AuthBackCommon.setInstance(new AuthBackCommon(envType));
	}
}
