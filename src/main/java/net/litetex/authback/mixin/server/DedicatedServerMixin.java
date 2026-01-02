package net.litetex.authback.mixin.server;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.litetex.authback.server.AuthBackServer;
import net.litetex.authback.shared.mixin.log.MixinLogger;
import net.minecraft.server.dedicated.DedicatedServer;


@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.server("DedicatedServerMixin");
	
	@Inject(
		method = "convertOldUsers",
		at = @At("HEAD"),
		cancellable = true
	)
	void skipConvertOldUsers(final CallbackInfoReturnable<Boolean> cir)
	{
		if(AuthBackServer.instance().isSkipOldUserConversion())
		{
			LOG.debug("Skipping old user conversion");
			cir.setReturnValue(false);
		}
	}
}
