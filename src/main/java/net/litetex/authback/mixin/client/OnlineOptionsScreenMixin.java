package net.litetex.authback.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.litetex.authback.client.menu.ConfigScreen;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;


@Mixin(OnlineOptionsScreen.class)
public abstract class OnlineOptionsScreenMixin extends OptionsSubScreen
{
	protected OnlineOptionsScreenMixin(
		final Screen screen,
		final Options options,
		final Component component)
	{
		super(screen, options, component);
	}
	
	@Unique
	private void openConfigScreen()
	{
		this.minecraft.setScreenAndShow(
			new ConfigScreen(
				this,
				this.options
			));
	}
	
	@Override
	protected void addFooter()
	{
		final LinearLayout footerLayout = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
		
		// To individual volume options screen
		addLayoutButton(
			footerLayout,
			Component.literal("AuthBack..."),
			_ -> this.openConfigScreen());
		addLayoutButton(footerLayout, CommonComponents.GUI_DONE, b -> this.onClose());
	}
	
	@Unique
	private static void addLayoutButton(final LinearLayout layout, final Component text, final Button.OnPress onPress)
	{
		layout.addChild(Button.builder(text, onPress).build());
	}
}
