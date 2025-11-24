package net.litetex.authback.shared;

import java.nio.file.Path;

import net.litetex.authback.shared.config.Configuration;
import net.litetex.authback.shared.io.AuthBackDirEnsurer;


public abstract class AuthBack
{
	protected final Configuration config;
	protected final Path authbackDir;
	
	public AuthBack(final String envType)
	{
		this.config = Configuration.standard(envType);
		this.authbackDir = AuthBackDirEnsurer.ensureAuthbackDir(this.config, envType);
	}
}
