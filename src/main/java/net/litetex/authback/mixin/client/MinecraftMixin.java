package net.litetex.authback.mixin.client;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.client.init.AuthBackClientPreInitializer;
import net.litetex.authback.shared.mixin.log.MixinLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;


@Mixin(Minecraft.class)
public abstract class MinecraftMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.client("MinecraftMixin");
	
	@Inject(
		method = "createUserApiService",
		at = @At("HEAD"),
		cancellable = true
	)
	void createUserApiService(
		final YggdrasilAuthenticationService yggdrasilAuthenticationService,
		final GameConfig gameConfig,
		final CallbackInfoReturnable<UserApiService> cir)
	{
		// Otherwise this will be done too late and config will not be available
		new AuthBackClientPreInitializer().onInitializeClient();
		
		if(AuthBackClient.instance().config().userAPIConfig().dummyMode().value())
		{
			LOG.debug("UserAPI is running in dummy mode");
			cir.setReturnValue(UserApiService.OFFLINE);
		}
	}
}
