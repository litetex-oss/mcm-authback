package net.litetex.authback.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.litetex.authback.client.AuthBackClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;


@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin
{
	@Shadow
	@Final
	private Minecraft minecraft;
	
	@Inject(
		method = "tick",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"),
		// Improve compatibility
		order = 1042,
		expect = 0
	)
	void fastGetRidOfOverlay(final CallbackInfo ci)
	{
		if(AuthBackClient.instance().config().immediatelyShowScreens().value())
		{
			this.minecraft.gui.setOverlay(null);
		}
	}
}
