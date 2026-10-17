package net.litetex.authback.common.init;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.litetex.authback.common.AuthBackCommon;
import net.litetex.authback.shared.init.Initializer;


public class AuthBackCommonInitializer extends Initializer implements PreLaunchEntrypoint
{
	@Override
	public void onPreLaunch()
	{
		this.doInit(() -> {
			final String envType = FabricLoader.getInstance().getEnvironmentType().name().toLowerCase();
			AuthBackCommon.setInstance(new AuthBackCommon(envType));
		});
	}
}
