package net.litetex.authback.mixin.common;

import java.net.URL;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;

import net.litetex.authback.common.AuthBackCommon;
import net.litetex.authback.shared.mixin.log.MixinLogger;


@Mixin(value = MinecraftServicesDiscoveryService.class, remap = false)
public abstract class MinecraftServiceDiscoveryServiceMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.common("MinecraftServiceDiscoveryServiceMixin");
	
	@WrapOperation(
		method = "lambda$createDiscoverySupplier$1",
		at = @At(value = "INVOKE",
			target = "Lcom/mojang/authlib/minecraft/client/MinecraftClient;get(Ljava/net/URL;"
				+ "Ljava/lang/Class;)Ljava/lang/Object;",
			remap = false),
		remap = false)
	private static <T> T get(
		final MinecraftClient instance, final URL url, final Class<T> responseClass, final Operation<T> original)
	{
		return AuthBackCommon.instance().discoveryCache().handleGetCall(LOG, instance, url, responseClass, original);
	}
}
