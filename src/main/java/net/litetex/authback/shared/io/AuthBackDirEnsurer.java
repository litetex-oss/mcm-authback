package net.litetex.authback.shared.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.litetex.authback.shared.config.Configuration;


public final class AuthBackDirEnsurer
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackDirEnsurer.class);
	
	public static Path ensureAuthbackDir(final Configuration config, final String variant)
	{
		// Migration logic can be removed after around 2026-03
		final AtomicBoolean usedDefault = new AtomicBoolean(false);
		
		final Path dir = Optional.ofNullable(config.getString("dir", null))
			.map(Paths::get)
			.orElseGet(() -> {
				usedDefault.set(true);
				return FabricLoader.getInstance().getGameDir()
					.resolve(".mods")
					.resolve("authback")
					.resolve(variant);
			});
		if(!Files.exists(dir))
		{
			try
			{
				Files.createDirectories(dir);
				
				if(usedDefault.get())
				{
					migrateLegacyDir(variant, dir);
				}
			}
			catch(final IOException e)
			{
				throw new UncheckedIOException(e);
			}
		}
		return dir;
	}
	
	private static void migrateLegacyDir(final String variant, final Path dir) throws IOException
	{
		final Path legacyRootDir = FabricLoader.getInstance().getGameDir().resolve(".authback");
		final Path legacyDir = legacyRootDir.resolve(variant);
		if(Files.exists(legacyDir))
		{
			Files.move(legacyDir, dir, StandardCopyOption.REPLACE_EXISTING);
			LOG.info("Migrated legacy directory: {} -> {}", legacyDir, dir);
			
			if(isDirectoryEmpty(legacyRootDir))
			{
				Files.delete(legacyRootDir);
				LOG.info("Deleted legacy root directory: {}", legacyRootDir);
			}
		}
	}
	
	private static boolean isDirectoryEmpty(final Path dirPath) throws IOException
	{
		try(final DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dirPath))
		{
			return !directoryStream.iterator().hasNext();
		}
	}
	
	private AuthBackDirEnsurer()
	{
	}
}
