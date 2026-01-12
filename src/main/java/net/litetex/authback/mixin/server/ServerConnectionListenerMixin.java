package net.litetex.authback.mixin.server;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.litetex.authback.server.AuthBackServer;
import net.minecraft.server.MinecraftServer;


@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public abstract class ServerConnectionListenerMixin
{
	@WrapOperation(
		method = "initChannel",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;repliesToStatus()Z")
	)
	boolean disableLegacyQueryHandler(final MinecraftServer instance, final Operation<Boolean> original)
	{
		if(AuthBackServer.instance().isDisableLegacyQueryHandler())
		{
			return false;
		}
		
		return original.call(instance);
	}
}
