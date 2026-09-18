package net.litetex.authback.common.config;

import net.litetex.authback.shared.config.ConfigValueContainer;
import net.litetex.authback.shared.config.Configuration;


public record AuthBackCommonConfig(
	ConfigValueContainer<Boolean> skipExtractProfileActionTypes
)
{
	public AuthBackCommonConfig(final Configuration config)
	{
		this(
			ConfigValueContainer.bool(config, "skip-extract-profile-action-types", false));
	}
}
