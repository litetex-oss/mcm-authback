package net.litetex.authback.client.init;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import net.fabricmc.loader.api.FabricLoader;
import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.client.config.compat.AuthBackClientConfigCompat;
import net.litetex.authback.shared.init.Initializer;


public class AuthBackClientPreInitializer extends Initializer
{
	public void onInitializeClient()
	{
		if(AuthBackClient.instance() != null)
		{
			return;
		}
		
		this.doInit(() -> {
			final FabricLoader fabricLoader = FabricLoader.getInstance();
			AuthBackClient.setInstance(new AuthBackClient(
				this.findAndCreateConfigCompats(fabricLoader)
			));
		});
	}
	
	// Classes cannot be referenced directly as this might lead to a class loading crash when
	// the corresponding dependencies are not present
	private @NonNull List<AuthBackClientConfigCompat> findAndCreateConfigCompats(final FabricLoader fabricLoader)
	{
		return Stream.of(
				"modmenu.ModMenuAuthBackClientConfigCompat")
			.filter(compatClass -> fabricLoader.isModLoaded(compatClass.split("\\.")[0]))
			// to full classname
			.map(compatClass -> "net.litetex.authback.client.config.compat." + compatClass)
			.map(clazz -> {
				try
				{
					return Class.forName(clazz);
				}
				catch(final Exception ex)
				{
					return null;
				}
			})
			.filter(Objects::nonNull)
			.map(cl -> {
				try
				{
					return (AuthBackClientConfigCompat)cl.getDeclaredConstructor().newInstance();
				}
				catch(final Exception ex)
				{
					return null;
				}
			})
			.toList();
	}
}
