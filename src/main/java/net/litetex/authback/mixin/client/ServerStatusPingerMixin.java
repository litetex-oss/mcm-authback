package net.litetex.authback.mixin.client;

import java.net.InetSocketAddress;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
	@Unique
	private final boolean preventLegacyServerPing;
	
	public ServerStatusPingerMixin()
	{
		this.preventLegacyServerPing = AuthBackClient.instance().config().preventLegacyServerPing();
	}
	
	@Inject(
		method = "pingLegacyServer",
		at = @At("HEAD"),
		cancellable = true,
		// Prevent conflicts with other mods
		order = 999,
		require = 0
	)
	void pingLegacyServer(
		final InetSocketAddress inetSocketAddress,
		final ServerAddress serverAddress,
		final ServerData serverData,
		final EventLoopGroupHolder eventLoopGroupHolder,
		final CallbackInfo ci)
	{
		if(this.preventLegacyServerPing)
		{
			ci.cancel();
		}
	}
}
