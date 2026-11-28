package net.litetex.authback.shared;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.litetex.authback.shared.config.Configuration;
import net.litetex.authback.shared.io.AuthBackDirEnsurer;


public abstract class AuthBack
{
	private static final Map<String, Configuration> CONFIG_DEDUPLICATION = new ConcurrentHashMap<>(1);
	
	protected final Configuration lowLevelConfig;
	protected final Path authbackDir;
	
	public AuthBack(final String envType)
	{
		this.lowLevelConfig = CONFIG_DEDUPLICATION.computeIfAbsent(envType, Configuration::standard);
		this.authbackDir = AuthBackDirEnsurer.ensureAuthbackDir(this.lowLevelConfig, envType);
	}
}
