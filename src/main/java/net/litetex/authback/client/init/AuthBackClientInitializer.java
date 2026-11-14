package net.litetex.authback.client.init;

import net.fabricmc.api.ClientModInitializer;
import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.shared.init.Initializer;


public class AuthBackClientInitializer extends Initializer implements ClientModInitializer
{
	@Override
	public void onInitializeClient()
	{
		this.doInit(() -> AuthBackClient.ensureInstance().initialize());
	}
}
