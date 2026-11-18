package net.litetex.authback.mixin.server;

import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.channel.Channel;
import net.litetex.authback.server.AuthBackServer;
import net.litetex.authback.shared.mixin.log.MixinLogger;


@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public abstract class ServerConnectionListenerMixin
{
	@Unique
	private static final Logger LOG_IP = MixinLogger.server("LogConnectionInitIPs");
	
	@Inject(
		method = "initChannel",
		at = @At("HEAD")
	)
	void reportConnectionInit(final Channel channel, final CallbackInfo ci)
	{
		LOG_IP.atLevel(AuthBackServer.instance().config().logConnectionInitIPs() ? Level.INFO : Level.DEBUG)
			.log("Channel {} initialized from {}", channel.id(), channel.remoteAddress());
	}
}
