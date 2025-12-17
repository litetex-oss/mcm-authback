package net.litetex.authback.mixin.client;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.realmsclient.client.RealmsClient;

import net.litetex.authback.client.AuthBackClient;


@Mixin(RealmsClient.class)
public abstract class RealmsClientMixin
{
	@Unique
	private static final Logger LOG = LoggerFactory.getLogger(RealmsClientMixin.class);
	
	@Unique
	private Runnable fetchRealmsFeatureFlagsAction;
	
	@WrapOperation(
		method = "<init>",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;"
				+ "Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;")
	)
	<U> CompletableFuture<U> init(
		final Supplier<U> supplier,
		final Executor executor,
		final Operation<CompletableFuture<U>> original)
	{
		if(AuthBackClient.instance().config().blockRealmsFetching().value())
		{
			LOG.debug("Delaying realms feature flag fetch");
			this.fetchRealmsFeatureFlagsAction = () -> original.call(supplier, executor);
			return null;
		}
		
		return original.call(supplier, executor);
	}
	
	@Inject(
		method = "getFeatureFlags",
		at = @At("HEAD")
	)
	void getFeatureFlags(final CallbackInfoReturnable<Set<String>> cir)
	{
		if(this.fetchRealmsFeatureFlagsAction != null)
		{
			this.fetchRealmsFeatureFlagsAction.run();
		}
	}
}
