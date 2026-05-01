package net.litetex.authback.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.litetex.authback.client.menu.ConfigScreen;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
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
	
	@Inject(
		method = "addOptions",
		at = @At("RETURN")
	)
	void addOptions(final CallbackInfo ci)
	{
		this.list.addSmall(List.of(
			Button.builder(
					Component.literal("AuthBack..."),
					ignored -> this.minecraft.setScreen(
						new ConfigScreen(
							this,
							this.options
						)))
				.build()));
	}
}
