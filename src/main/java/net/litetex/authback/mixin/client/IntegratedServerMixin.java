package net.litetex.authback.mixin.client;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.litetex.authback.client.AuthBackClient;
import net.minecraft.client.server.IntegratedServer;


@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin
{
	@WrapMethod(
		method = "enforceSecureProfile",
		order = 1101 // Lower priority in case mods like no-chat-reports will handle this in the future
	)
	boolean enforceSecureProfile(final Operation<Boolean> original)
	{
		if(AuthBackClient.instance().config().integratedServerDisableEnforceSecureProfile().value())
		{
			return false;
		}
		
		return original.call();
	}
}
