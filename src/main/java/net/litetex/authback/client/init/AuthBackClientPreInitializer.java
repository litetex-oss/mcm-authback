package net.litetex.authback.client.init;

import java.util.Map;
import java.util.function.Supplier;

import net.fabricmc.loader.api.FabricLoader;
import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.client.config.compat.AuthBackClientConfigCompat;
import net.litetex.authback.client.config.compat.ModMenuAuthBackClientConfigCompat;
import net.litetex.authback.shared.init.Initializer;


public class AuthBackClientPreInitializer extends Initializer
{
	public void onInitializeClient()
	{
		this.doInit(() -> {
			final FabricLoader fabricLoader = FabricLoader.getInstance();
			AuthBackClient.setInstance(new AuthBackClient(
				Map.<String, Supplier<AuthBackClientConfigCompat>>of(
						"modmenu", ModMenuAuthBackClientConfigCompat::new)
					.entrySet()
					.stream()
					.filter(e -> fabricLoader.isModLoaded(e.getKey()))
					.map(e -> e.getValue().get())
					.toList()
			));
		});
	}
}
