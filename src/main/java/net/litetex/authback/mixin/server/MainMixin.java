package net.litetex.authback.mixin.server;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.litetex.authback.common.AuthBackCommon;
import net.litetex.authback.server.AuthBackServer;
import net.minecraft.server.Main;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.DedicatedServerSettings;


@Mixin(Main.class)
public abstract class MainMixin
{
	@WrapOperation(
		method = "main",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/dedicated/DedicatedServerSettings;getProperties()"
				+ "Lnet/minecraft/server/dedicated/DedicatedServerProperties;",
			ordinal = 0
		)
	)
	private static DedicatedServerProperties interceptDedicateServerSettings(
		final DedicatedServerSettings instance,
		final Operation<DedicatedServerProperties> original)
	{
		final DedicatedServerProperties properties = original.call(instance);
		// Global Public Keys are only needed for secure chat on a server
		AuthBackCommon.instance().globalPublicKeysFetchingEnabledAutoDetect(() ->
			AuthBackServer.instance().config().globalPublicKeysFetchingDisableIfPossible()
				? properties.enforceSecureProfile
				: null);
		return properties;
	}
}
