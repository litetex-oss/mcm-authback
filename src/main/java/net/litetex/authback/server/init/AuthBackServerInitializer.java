package net.litetex.authback.server.init;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.litetex.authback.server.AuthBackServer;
import net.litetex.authback.shared.init.Initializer;


public class AuthBackServerInitializer extends Initializer implements DedicatedServerModInitializer
{
	@Override
	public void onInitializeServer()
	{
		this.doInit(() -> AuthBackServer.setInstance(new AuthBackServer()));
	}
}
