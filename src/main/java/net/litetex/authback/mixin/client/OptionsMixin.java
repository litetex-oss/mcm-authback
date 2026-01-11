package net.litetex.authback.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;


@SuppressWarnings("checkstyle:VisibilityModifier")
@Mixin(Options.class)
public abstract class OptionsMixin
{
	@Inject(
		method = "load",
		at = @At("HEAD"),
		order = 999
	)
	void overrideDefaultValues(final CallbackInfo ci)
	{
		// Replace default values with ones that are not annoying (during debugging)
		this.onboardAccessibility = false;
		this.narratorHotkey.set(false);
		
		this.skipMultiplayerWarning = true;
		this.joinedFirstServer = true;
	}
	
	@Shadow
	public boolean onboardAccessibility;
	
	@Shadow
	public boolean skipMultiplayerWarning;
	
	@Shadow
	public boolean joinedFirstServer;
	
	@Shadow
	public OptionInstance<Boolean> narratorHotkey;
}
