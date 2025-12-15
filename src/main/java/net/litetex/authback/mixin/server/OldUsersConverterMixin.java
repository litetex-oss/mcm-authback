package net.litetex.authback.mixin.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.litetex.authback.server.AuthBackServer;
import net.minecraft.server.players.OldUsersConverter;


@Mixin(OldUsersConverter.class)
public abstract class OldUsersConverterMixin
{
	@Unique
	private static final Logger LOG = LoggerFactory.getLogger(OldUsersConverterMixin.class);
	
	@Inject(
		method = "serverReadyAfterUserconversion",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void skipServerReadyAfterUserconversion(final CallbackInfoReturnable<Boolean> cir)
	{
		if(AuthBackServer.instance().isSkipOldUserConversion())
		{
			LOG.debug("Skipping server ready after userconversion");
			cir.setReturnValue(true);
		}
	}
}
