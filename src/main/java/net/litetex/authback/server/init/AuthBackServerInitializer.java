package net.litetex.authback.server.init;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.litetex.authback.server.AuthBackServer;


public class AuthBackServerInitializer implements DedicatedServerModInitializer
{
	@Override
	public void onInitializeServer()
	{
		AuthBackServer.setInstance(new AuthBackServer());
	}
}
