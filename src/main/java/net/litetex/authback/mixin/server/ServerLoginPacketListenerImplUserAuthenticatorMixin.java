package net.litetex.authback.mixin.server;

import org.spongepowered.asm.mixin.Mixin;


@Mixin(targets = "net/minecraft/server/network/ServerLoginPacketListenerImpl$1")
public abstract class ServerLoginPacketListenerImplUserAuthenticatorMixin
{
	// @Inject(
	// 	method = "run",
	// 	at = @At(
	// 		value = "INVOKE",
	// 		target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;disconnect
	// 		(Lnet/minecraft/network/chat/Component;)V",
	// 		slice = ""
	// 	)
	// )
	// void stuff()
	// {
	//
	// }
}
