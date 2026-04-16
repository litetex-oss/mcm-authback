package net.litetex.authback.client.config;

import net.litetex.authback.shared.config.ConfigValueContainer;
import net.litetex.authback.shared.config.Configuration;


public record AuthBackClientConfig(
	// Replaces the server blocklist check with a dummy that will do no initial fetching
	ConfigValueContainer<Boolean> blockAddressCheck,
	// Block fetching of profile/chat-signing keys
	// This will result in not being able to join servers that have enforce-secure-profile set to true
	ConfigValueContainer<Boolean> blockFetchingProfileKeys,
	// Blocks initial fetching of Realms news, notifications, etc
	ConfigValueContainer<Boolean> blockRealmsFetching,
	// Suppresses all joinServer errors
	// WARNING: Allows to join servers with possibly invalid session data
	ConfigValueContainer<Boolean> suppressAllServerJoinErrors,
	UserAPIConfig userAPIConfig,
	// Disables sending a legacy ping (for servers running 1.6.4 or lower) in the server list
	// when the normal ping fails or times out
	boolean preventLegacyServerPing
)
{
	public AuthBackClientConfig(final Configuration config)
	{
		this(
			ConfigValueContainer.bool(config, "block-address-check", true),
			ConfigValueContainer.bool(config, "block-profile-keys-fetching", false),
			ConfigValueContainer.bool(config, "block-realms-fetching", false),
			ConfigValueContainer.bool(config, "suppress-all-server-join-errors", false),
			new UserAPIConfig(config),
			config.getBoolean("prevent-legacy-server-ping", true)
		);
	}
	
	public record UserAPIConfig(
		// Simulates that the user API is in offline/dummy mode. This means that
		// * No user properties (like SERVERS_ALLOWED, CHAT_ALLOWED, TELEMETRY_ENABLED) will be fetched.
		// Instead the defaults will be used.
		// * The player blocklist will not work
		// * No telemetry will be available
		// * No keypair will be available (see blockFetchingProfileKeys)
		// * Abuse reporting will not be available
		ConfigValueContainer<Boolean> dummyMode,
		ConfigValueContainer<Boolean> blockFetchProperties,
		ConfigValueContainer<Boolean> blockFetchBlocklist,
		ConfigValueContainer<Boolean> blockTelemetry,
		ConfigValueContainer<Boolean> blockReportAbuse
	)
	{
		public UserAPIConfig(final Configuration config)
		{
			this(
				ConfigValueContainer.bool(config, "userapi-dummy-mode", false),
				ConfigValueContainer.bool(config, "userapi-block-fetch-properties", false),
				ConfigValueContainer.bool(config, "userapi-block-fetch-blocklist", false),
				ConfigValueContainer.bool(config, "userapi-block-telemetry", false),
				ConfigValueContainer.bool(config, "userapi-block-report-abuse", false)
			);
		}
	}
}
