package net.litetex.authback.server.config;

import net.litetex.authback.shared.config.Configuration;


public record AuthBackServerConfig(
	// Also allow fallback auth when authServers are available for the server
	boolean alwaysAllowFallbackAuth,
	// Force disable enforce-secure-profile
	boolean forceDisableEnforceSecureProfile,
	// Requires calling the API
	// Only needed when updating extremely ancient server versions (before 1.7.6 - 2014-04)
	boolean skipOldUserConversion,
	// Disables the legacy/pre 1.7 (released 2013-10) query/ping handler
	boolean disableLegacyQueryHandler,
	// Logs all remote IPs that initialize/start a connection to level INFO
	boolean logConnectionInitIPs
)
{
	public AuthBackServerConfig(final Configuration conf)
	{
		this(
			conf.getBoolean("fallback-auth.allow-always", true),
			conf.getBoolean("force-disable-enforce-secure-profile", true),
			conf.getBoolean("skip-old-user-conversion", true),
			conf.getBoolean("disable-legacy-query-handler", true),
			conf.getBoolean("log-connection-init-ips", false)
		);
	}
}
