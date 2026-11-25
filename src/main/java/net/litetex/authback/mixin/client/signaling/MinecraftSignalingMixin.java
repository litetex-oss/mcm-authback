package net.litetex.authback.mixin.client.signaling;

import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.client.config.SignalingState;
import net.litetex.authback.shared.mixin.log.MixinLogger;
import net.minecraft.client.Minecraft;


@Mixin(Minecraft.class)
public abstract class MinecraftSignalingMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.client("MinecraftSignalingMixin");
	
	@WrapOperation(
		method = "<init>",
		at = @At(
			value = "FIELD",
			target = "Lnet/minecraft/client/Minecraft;offlineDeveloperMode:Z",
			opcode = Opcodes.GETFIELD),
		slice = @Slice(
			from = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/client/telemetry/ClientTelemetryManager;getOutsideSessionSender()"
					+ "Lnet/minecraft/client/telemetry/TelemetryEventSender;")
		)
	)
	boolean disableSignalingClient(
		final Minecraft instance,
		final Operation<Boolean> original)
	{
		final String signalingNotCreatedReason = this.signalingNotCreatedReason();
		if(signalingNotCreatedReason != null)
		{
			LOG.info("Signaling client will not be created: {}", signalingNotCreatedReason);
			return true;
		}
		
		return original.call(instance);
	}
	
	@Unique
	private String signalingNotCreatedReason()
	{
		final SignalingState signalingState =
			AuthBackClient.instance().config().signalingState().value();
		if(signalingState == SignalingState.DISABLE)
		{
			return "Explicitly disabled";
		}
		if(signalingState == SignalingState.AUTO_DISABLE)
		{
			final String detail = this.signalingNotCreatedAutoDetectReason();
			return detail != null
				? "[Auto] " + detail
				: null;
		}
		return null;
	}
	
	@Unique
	private String signalingNotCreatedAutoDetectReason()
	{
		if(!this.friendsEnabled())
		{
			return "Friends list is not enabled";
		}
		return null;
	}
	
	@Shadow
	public abstract boolean friendsEnabled();
}
