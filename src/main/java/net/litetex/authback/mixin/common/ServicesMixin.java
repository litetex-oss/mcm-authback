package net.litetex.authback.mixin.common;

import java.io.File;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;

import net.litetex.authback.common.AuthBackCommon;
import net.minecraft.server.Services;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.server.players.UserNameToIdResolver;


@Mixin(Services.class)
public abstract class ServicesMixin
{
	// Can't replace CachedUserNameToIdResolver because it's not using the underlying interface
	// -> Replace entire method
	@Inject(
		method = "create",
		at = @At(value = "HEAD"),
		cancellable = true
	)
	private static void create(
		final MinecraftServicesDiscoveryService serviceAccess,
		final File nameCacheDir,
		final CallbackInfoReturnable<Services> cir)
	{
		final SessionService minecraftSessionService = serviceAccess.createMinecraftSessionService();
		final GameProfileRepository gameProfileRepository = serviceAccess.createProfileRepository();
		final UserNameToIdResolver userNameToIdResolver =
			AuthBackCommon.instance().createUserNameToIdResolver(gameProfileRepository, nameCacheDir);
		final ProfileResolver profileResolver = new ProfileResolver.Cached(
			minecraftSessionService,
			userNameToIdResolver);
		cir.setReturnValue(new Services(
			minecraftSessionService,
			serviceAccess.getServicesKeySet(),
			gameProfileRepository,
			userNameToIdResolver,
			profileResolver));
	}
}
