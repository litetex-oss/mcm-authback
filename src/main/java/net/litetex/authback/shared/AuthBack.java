package net.litetex.authback.shared;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.litetex.authback.shared.config.Configuration;
import net.litetex.authback.shared.io.AuthBackDirEnsurer;


public abstract class AuthBack
{
	private static final Map<String, Configuration> CONFIG_DEDUPLICATION =
		Collections.synchronizedMap(new HashMap<>());
	
	protected final Configuration lowLevelConfig;
	protected final Path authbackDir;
	
	public AuthBack(final String envType)
	{
		this.lowLevelConfig = CONFIG_DEDUPLICATION.computeIfAbsent(envType, Configuration::standard);
		this.authbackDir = AuthBackDirEnsurer.ensureAuthbackDir(this.lowLevelConfig, envType);
	}
}
