package net.litetex.authback.mixin.client;

import java.net.InetSocketAddress;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.litetex.authback.client.AuthBackClient;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.EventLoopGroupHolder;


@Mixin(ServerStatusPinger.class)
public abstract class ServerStatusPingerMixin
{
	@Inject(
		method = "pingLegacyServer",
		at = @At("HEAD"),
		cancellable = true,
		// Prevent conflicts with other mods
		order = 999,
		require = 0
	)
	void pingLegacyServer(
		final InetSocketAddress resolvedAddress,
		final ServerAddress rawAddress,
		final ServerData data,
		final EventLoopGroupHolder eventLoopGroupHolder,
		final CallbackInfo ci)
	{
		if(AuthBackClient.instance().config().preventLegacyServerPing().value())
		{
			ci.cancel();
		}
	}
}
