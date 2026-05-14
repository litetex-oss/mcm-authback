package net.litetex.authback.mixin.server;

import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.netty.channel.Channel;
import net.litetex.authback.server.AuthBackServer;
import net.litetex.authback.shared.mixin.log.MixinLogger;
import net.minecraft.server.MinecraftServer;


@SuppressWarnings("PMD.MoreThanOneLogger") // Expected
@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public abstract class ServerConnectionListenerMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.server("ServerConnectionListenerMixin");
	
	@Unique
	private static final Logger LOG_IP = MixinLogger.server("LogConnectionInitIPs");
	
	@WrapOperation(
		method = "initChannel",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;repliesToStatus()Z")
	)
	boolean disableLegacyQueryHandler(final MinecraftServer instance, final Operation<Boolean> original)
	{
		if(AuthBackServer.instance().config().disableLegacyQueryHandler())
		{
			LOG.debug("Disabled legacy query handler");
			return false;
		}
		
		return original.call(instance);
	}
	
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
