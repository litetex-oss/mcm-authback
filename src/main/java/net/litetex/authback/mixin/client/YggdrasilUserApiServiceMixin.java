package net.litetex.authback.mixin.client;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.request.AbuseReportRequest;
import com.mojang.authlib.yggdrasil.response.KeyPairResponse;

import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.client.config.AuthBackClientConfig;
import net.litetex.authback.shared.mixin.log.MixinLogger;


// Is not in core client and can be updated on the fly -> everything is optional
@Mixin(
	targets = "com.mojang.authlib.yggdrasil.YggdrasilUserApiService",
	remap = false)
public abstract class YggdrasilUserApiServiceMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.client("YggdrasilUserApiServiceMixin");
	
	@Unique
	private AuthBackClientConfig.UserAPIConfig userAPIConfig()
	{
		return AuthBackClient.instance().config().userAPIConfig();
	}
	
	@Inject(
		method = "fetchProperties",
		at = @At("HEAD"),
		cancellable = true,
		// Optional
		remap = false,
		order = 999,
		require = 0
	)
	void fetchProperties(final CallbackInfoReturnable<UserApiService.UserProperties> cir)
	{
		if(this.userAPIConfig().blockFetchProperties().value())
		{
			LOG.debug("Blocked fetching of properties");
			cir.setReturnValue(UserApiService.OFFLINE_PROPERTIES);
		}
	}
	
	@Inject(
		method = "isBlockedPlayer",
		at = @At("HEAD"),
		cancellable = true,
		// Optional
		remap = false,
		order = 999,
		require = 0
	)
	void isBlockedPlayer(final UUID playerID, final CallbackInfoReturnable<Boolean> cir)
	{
		if(this.userAPIConfig().blockFetchBlocklist().value())
		{
			LOG.debug("Returning false for isBlockedPlayer");
			cir.setReturnValue(false);
		}
	}
	
	@Inject(
		method = "refreshBlockList",
		at = @At("HEAD"),
		cancellable = true,
		// Optional
		remap = false,
		order = 999,
		require = 0
	)
	void refreshBlockList(final CallbackInfo ci)
	{
		if(this.userAPIConfig().blockFetchBlocklist().value())
		{
			LOG.debug("Blocked blocklist refresh");
			ci.cancel();
		}
	}
	
	@Inject(
		method = "forceFetchBlockList",
		at = @At("HEAD"),
		cancellable = true,
		// Optional
		remap = false,
		order = 999,
		require = 0
	)
	void forceFetchBlockList(final CallbackInfoReturnable<Set<UUID>> cir)
	{
		if(this.userAPIConfig().blockFetchBlocklist().value())
		{
			LOG.debug("Blocked force fetching of blocklist");
			cir.setReturnValue(Set.of());
		}
	}
	
	@Inject(
		method = "newTelemetrySession",
		at = @At("HEAD"),
		cancellable = true,
		// Optional
		remap = false,
		order = 999,
		require = 0
	)
	void newTelemetrySession(final Executor executor, final CallbackInfoReturnable<TelemetrySession> cir)
	{
		if(this.userAPIConfig().blockTelemetry().value())
		{
			LOG.debug("Blocked newTelemetrySession");
			cir.setReturnValue(TelemetrySession.DISABLED);
		}
	}
	
	@Inject(
		method = "getKeyPair",
		at = @At("HEAD"),
		cancellable = true,
		// Optional
		remap = false,
		order = 999,
		require = 0
	)
	void getKeyPair(final CallbackInfoReturnable<KeyPairResponse> cir)
	{
		if(AuthBackClient.instance().config().blockFetchingProfileKeys().value())
		{
			LOG.debug("Blocked getKeyPair");
			cir.setReturnValue(null);
		}
	}
	
	@Inject(
		method = "reportAbuse",
		at = @At("HEAD"),
		cancellable = true,
		// Optional
		remap = false,
		order = 999,
		require = 0
	)
	void reportAbuse(final AbuseReportRequest request, final CallbackInfo ci)
	{
		if(this.userAPIConfig().blockReportAbuse().value())
		{
			LOG.debug("Blocked reportAbuse");
			ci.cancel();
		}
	}
	
	@Inject(
		method = "canSendReports",
		at = @At("HEAD"),
		cancellable = true,
		// Optional
		remap = false,
		order = 999,
		require = 0
	)
	void canSendReports(final CallbackInfoReturnable<Boolean> cir)
	{
		if(this.userAPIConfig().blockReportAbuse().value())
		{
			LOG.debug("Returning false for canSendReports");
			cir.setReturnValue(false);
		}
	}
}
