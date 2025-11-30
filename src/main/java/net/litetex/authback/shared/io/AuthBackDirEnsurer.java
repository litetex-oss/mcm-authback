package net.litetex.authback.shared.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.litetex.authback.shared.config.Configuration;


public final class AuthBackDirEnsurer
{
	public static Path ensureAuthbackDir(final Configuration config, final String variant)
	{
		final Path dir = Optional.ofNullable(config.getString("dir", null))
			.map(Paths::get)
			.orElseGet(() -> FabricLoader.getInstance().getGameDir().resolve(".authback").resolve(variant));
		if(!Files.exists(dir))
		{
			try
			{
				Files.createDirectories(dir);
			}
			catch(final IOException e)
			{
				throw new UncheckedIOException(e);
			}
		}
		return dir;
	}
	
	private AuthBackDirEnsurer()
	{
	}
}
