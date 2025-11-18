package net.litetex.authback.mixin.server;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

import net.litetex.authback.server.AuthBackServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;


@Mixin(targets = "net/minecraft/server/network/ServerLoginPacketListenerImpl$1")
public abstract class ServerLoginPacketListenerImplUserAuthenticatorMixin
{
	@Unique
	private static final Logger LOG =
		LoggerFactory.getLogger(ServerLoginPacketListenerImplUserAuthenticatorMixin.class);
	
	@Accessor("field_14176")
	abstract ServerLoginPacketListenerImpl serverLoginPacketListener();
	
	@Redirect(
		method = "run",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;startClientVerification"
				+ "(Lcom/mojang/authlib/GameProfile;)V",
			ordinal = 0
		)
	)
	void handleHasJoinedSuccess(final ServerLoginPacketListenerImpl instance, final GameProfile gameProfile)
	{
		instance.startClientVerification(gameProfile);
		
		AuthBackServer.instance().handleJoinSuccess(gameProfile);
	}
	
	@Inject(
		method = "run",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;disconnect"
				+ "(Lnet/minecraft/network/chat/Component;)V",
			ordinal = 0
		),
		cancellable = true
	)
	void handleNotJoined(final CallbackInfo ci)
	{
		if(!AuthBackServer.instance().isAlwaysAllowFallbackAuth())
		{
			return;
		}
		
		this.doFallbackAuth(
			"UnverifiedClient",
			loginPacketListener -> {
				loginPacketListener.disconnect(Component.translatable("multiplayer.disconnect.unverified_username"));
				LOG.error("Couldn't verify username");
			});
		
		ci.cancel();
	}
	
	@Inject(
		method = "run",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;disconnect"
				+ "(Lnet/minecraft/network/chat/Component;)V",
			ordinal = 1
		),
		cancellable = true
	)
	void handleAuthServersFailed(final CallbackInfo ci)
	{
		this.doFallbackAuth(
			"AuthServersDown",
			loginPacketListener -> {
				loginPacketListener.disconnect(Component.translatable("multiplayer.disconnect.authservers_down"));
				LOG.error("Couldn't verify username because auth servers are unavailable");
			});
		
		ci.cancel();
	}
	
	@Unique
	private void doFallbackAuth(
		final String variant,
		final Consumer<ServerLoginPacketListenerImpl> defaultDisconnectAction
	)
	{
		final ServerLoginPacketListenerImpl loginPacketListener = this.serverLoginPacketListener();
		AuthBackServer.instance().doFallbackAuth(
			loginPacketListener,
			() -> defaultDisconnectAction.accept(loginPacketListener),
			msg -> {
				loginPacketListener.disconnect(Component.literal("[AuthBack] " + msg));
				LOG.info("[AuthBack/{}] Disconnected client: {}", variant, msg);
			},
			gameProfile -> {
				loginPacketListener.startClientVerification(gameProfile);
				LOG.info(
					"[AuthBack/{}] Fallback was successful - Proceeding with login of player[name={},uuid={}]",
					variant,
					gameProfile.name(),
					gameProfile.id());
			}
		);
	}
}
