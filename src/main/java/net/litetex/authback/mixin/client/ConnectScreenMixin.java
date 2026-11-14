package net.litetex.authback.mixin.client;

import java.net.InetSocketAddress;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.litetex.authback.client.AuthBackClient;


@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
public abstract class ConnectScreenMixin
{
	@Unique
	private final boolean enabled;
	
	public ConnectScreenMixin()
	{
		// Enabled when blockAddressCheck == true to prevent delay during login
		// that would otherwise not be present because it already happened in the server list
		this.enabled = AuthBackClient.instance().config().blockAddressCheck().value();
	}
	
	@WrapOperation(
		method = "run",
		at = @At(value = "INVOKE", target = "Ljava/net/InetSocketAddress;getHostName()Ljava/lang/String;"))
	String getHostName(final InetSocketAddress instance, final Operation<String> original)
	{
		return this.enabled
			? instance.getHostString() // Same as getHostName but does not execute reverse DNS lookup (on literal IPs)
			: original.call(instance);
	}
}
