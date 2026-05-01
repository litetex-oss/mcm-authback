package net.litetex.authback.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.litetex.authback.client.menu.ConfigScreen;
import net.minecraft.client.Minecraft;


public class ModMenuCompatibility implements ModMenuApi
{
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory()
	{
		return s -> new ConfigScreen(s, Minecraft.getInstance().options);
	}
}
