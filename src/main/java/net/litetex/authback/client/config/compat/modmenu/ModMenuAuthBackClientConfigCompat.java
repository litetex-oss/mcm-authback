package net.litetex.authback.client.config.compat.modmenu;

import com.terraformersmc.modmenu.config.ModMenuConfig;
import com.terraformersmc.modmenu.config.ModMenuConfigManager;

import net.litetex.authback.client.config.compat.AuthBackClientConfigCompat;


public class ModMenuAuthBackClientConfigCompat implements AuthBackClientConfigCompat
{
	@Override
	public void onCompatTitleScreenSetIntoConfig(final boolean val)
	{
		if(val && ModMenuConfig.MODS_BUTTON_STYLE.getValue() == ModMenuConfig.TitleMenuButtonStyle.ICON)
		{
			ModMenuConfig.MODS_BUTTON_STYLE.setValue(ModMenuConfig.TitleMenuButtonStyle.REPLACE_REALMS);
			ModMenuConfigManager.save();
		}
	}
}
