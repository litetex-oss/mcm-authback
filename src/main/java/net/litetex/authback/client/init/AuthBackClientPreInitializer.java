package net.litetex.authback.client.init;

import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.shared.init.Initializer;


public class AuthBackClientPreInitializer extends Initializer
{
	public void onInitializeClient()
	{
		this.doInit(AuthBackClient::ensureInstance);
	}
}
