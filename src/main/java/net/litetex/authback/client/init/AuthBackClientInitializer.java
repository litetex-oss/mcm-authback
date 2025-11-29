package net.litetex.authback.client.init;

import net.fabricmc.api.ClientModInitializer;
import net.litetex.authback.client.AuthBackClient;


public class AuthBackClientInitializer implements ClientModInitializer
{
	@Override
	public void onInitializeClient()
	{
		AuthBackClient.setInstance(new AuthBackClient());
	}
}
