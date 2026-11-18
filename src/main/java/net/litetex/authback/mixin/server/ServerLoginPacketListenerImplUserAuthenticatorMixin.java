package net.litetex.authback.mixin.server;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;

import net.litetex.authback.server.AuthBackServer;
import net.litetex.authback.shared.mixin.log.MixinLogger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.crypto.SessionKeys;


@Mixin(targets = "net/minecraft/server/network/ServerLoginPacketListenerImpl$1")
public abstract class ServerLoginPacketListenerImplUserAuthenticatorMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.server("ServerLoginPacketListenerImplUserAuthenticatorMixin");
	
	@Accessor("this$0")
	abstract ServerLoginPacketListenerImpl serverLoginPacketListener();
	
	@WrapOperation(
		method = "run",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;applyConnectionEncryption"
				+ "(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/util/crypto/SessionKeys;)V",
			ordinal = 0
		)
	)
	void handleHasJoinedSuccess(
		final ServerLoginPacketListenerImpl instance,
		final GameProfile authenticatedGameProfile,
		final SessionKeys secretKey,
		final Operation<Void> original)
	{
		original.call(instance, authenticatedGameProfile, secretKey);
		
		AuthBackServer.instance().handleJoinSuccess(authenticatedGameProfile);
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
	void handleNotJoined(final CallbackInfo ci, @Local(name = "sessionKeys") final SessionKeys sessionKeys)
	{
		if(!AuthBackServer.instance().config().alwaysAllowFallbackAuth())
		{
			LOG.debug("Always allow fallback auth is disabled");
			return;
		}
		
		this.doFallbackAuth(
			"UnverifiedClient",
			sessionKeys,
			loginPacketListener -> {
				loginPacketListener.disconnect(Component.translatable("multiplayer.disconnect.unverified_username"));
				LOG.warn("Couldn't verify username '{}'", loginPacketListener.getUserName());
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
	void handleAuthServersFailed(final CallbackInfo ci, @Local(name = "sessionKeys") final SessionKeys sessionKeys)
	{
		this.doFallbackAuth(
			"AuthServersDown",
			sessionKeys,
			loginPacketListener -> {
				loginPacketListener.disconnect(Component.translatable("multiplayer.disconnect.authservers_down"));
				LOG.warn(
					"Couldn't verify username '{}' because auth servers are unavailable",
					loginPacketListener.getUserName());
			});
		
		ci.cancel();
	}
	
	@Unique
	private void doFallbackAuth(
		final String variant,
		final SessionKeys sessionKeys,
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
				loginPacketListener.applyConnectionEncryption(gameProfile, sessionKeys);
				LOG.info(
					"[AuthBack/{}] Fallback was successful - Proceeding with login of player[name={},uuid={}]",
					variant,
					gameProfile.name(),
					gameProfile.id());
			}
		);
	}
}
