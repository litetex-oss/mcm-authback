package net.litetex.authback.mixin.client.lan_server_detect;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.shared.mixin.log.MixinLogger;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.server.LanServerDetection;


@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.client("JoinMultiplayerScreenMixin");
	
	@WrapOperation(
		method = "init",
		at = @At(
			value = "NEW",
			target = "(Lnet/minecraft/client/server/LanServerDetection$LanServerList;)"
				+ "Lnet/minecraft/client/server/LanServerDetection$LanServerDetector;")
	)
	@SuppressWarnings("checkstyle:IllegalIdentifierName")
	LanServerDetection.LanServerDetector newLanServerDetector(
		final LanServerDetection.LanServerList serverList,
		final Operation<LanServerDetection.LanServerDetector> original)
	{
		if(isDisabled())
		{
			LOG.debug("LAN Server Detection is disabled");
			return null;
		}
		
		return original.call(serverList);
	}
	
	@WrapOperation(
		method = "init",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/server/LanServerDetection$LanServerDetector;start()V")
	)
	void startLanServerDetector(final LanServerDetection.LanServerDetector instance, final Operation<Void> original)
	{
		if(isDisabled())
		{
			return;
		}
		original.call(instance);
	}
	
	@Unique
	private static boolean isDisabled()
	{
		return AuthBackClient.instance().config().disableLANServerDetection().value();
	}
}
