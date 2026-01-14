package net.litetex.authback.mixin.server;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.litetex.authback.server.AuthBackServer;
import net.litetex.authback.shared.mixin.log.MixinLogger;
import net.minecraft.server.dedicated.DedicatedServerProperties;


@Mixin(DedicatedServerProperties.class)
public abstract class DedicatedServerPropertiesMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.server("DedicatedServerPropertiesMixin");
	
	@WrapOperation(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/dedicated/DedicatedServerProperties;get(Ljava/lang/String;Z)Z")
	)
	boolean get(
		final DedicatedServerProperties instance,
		final String key,
		final boolean defaultValue,
		final Operation<Boolean> original)
	{
		final boolean resolvedValue = original.call(instance, key, defaultValue);
		if("enforce-secure-profile".equals(key)
			&& resolvedValue
			&& AuthBackServer.instance().config().forceDisableEnforceSecureProfile())
		{
			LOG.info("enforce-secure-profile was enabled - force disabling it. "
				+ "You should disable it in the server.properties!");
			return false;
		}
		return resolvedValue;
	}
}
