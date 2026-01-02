package net.litetex.authback.mixin.common;

import java.net.URL;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.HttpAuthenticationService;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.exceptions.MinecraftClientException;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.ProfileActionType;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;
import com.mojang.authlib.yggdrasil.response.MinecraftProfilePropertiesResponse;
import com.mojang.authlib.yggdrasil.response.ProfileAction;
import com.mojang.util.UndashedUuid;

import net.litetex.authback.common.AuthBackCommon;
import net.litetex.authback.common.gameprofile.GameProfileCacheManager;
import net.litetex.authback.shared.mixin.log.MixinLogger;


@Mixin(value = YggdrasilMinecraftSessionService.class, remap = false)
public abstract class YggdrasilMinecraftSessionServiceMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.common("YggdrasilMinecraftSessionServiceMixin");
	
	@Final
	@Shadow
	private MinecraftClient client;
	
	@Final
	@Shadow
	private String baseUrl;
	
	@Shadow
	private static Set<ProfileActionType> extractProfileActionTypes(final Set<ProfileAction> response)
	{
		return null;
	}
	
	@Inject(
		method = "fetchProfileUncached",
		at = @At(value = "HEAD"),
		cancellable = true
	)
	void fetchProfileUncached(
		final UUID profileId,
		final boolean requireSecure,
		final CallbackInfoReturnable<ProfileResult> cir)
	{
		try
		{
			final URL url = HttpAuthenticationService.concatenateURL(
				HttpAuthenticationService.constantURL(
					this.baseUrl + "profile/" + UndashedUuid.toString(profileId)),
				"unsigned=" + !requireSecure);
			
			final long startMs = System.currentTimeMillis();
			final MinecraftProfilePropertiesResponse response =
				this.client.get(url, MinecraftProfilePropertiesResponse.class);
			LOG.debug(
				"Took {}ms to get response for {}",
				System.currentTimeMillis() - startMs,
				url);
			if(response == null)
			{
				LOG.debug("Couldn't fetch profile properties for {} as the profile does not exist", profileId);
				cir.setReturnValue(null);
				return;
			}
			
			final GameProfile profile = response.profile();
			
			if(requireSecure)
			{
				this.gameProfileCacheManager().add(profile);
			}
			
			final Set<ProfileActionType> profileActions =
				extractProfileActionTypes(response.profileActions());
			
			LOG.trace("Successfully fetched profile properties for {}", profile);
			cir.setReturnValue(new ProfileResult(profile, profileActions));
		}
		catch(final MinecraftClientException | IllegalArgumentException e)
		{
			if(e instanceof MinecraftClientException)
			{
				final GameProfile cachedProfile = this.gameProfileCacheManager().findByUUID(profileId);
				if(cachedProfile != null)
				{
					LOG.info("Failed to look up profile properties for {} but used cache instead", profileId, e);
					cir.setReturnValue(new ProfileResult(cachedProfile, Set.of()));
					return;
				}
			}
			
			LOG.warn("Couldn't look up profile properties for {}", profileId, e);
			cir.setReturnValue(null);
		}
	}
	
	@Unique
	private GameProfileCacheManager gameProfileCacheManager()
	{
		return AuthBackCommon.instance().gameProfileCacheManagerSupplier().get();
	}
	
	@Inject(
		method = "extractProfileActionTypes",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void extractProfileActionTypesInject(
		final Set<ProfileAction> response,
		final CallbackInfoReturnable<Set<ProfileActionType>> cir)
	{
		if(AuthBackCommon.instance().config().skipExtractProfileActionTypes().value())
		{
			LOG.debug("Skipping execution of extractProfileActionTypes");
			cir.setReturnValue(Set.of());
		}
	}
	
	// Workaround annoying log spam:
	// Some servers send empty signatures, for example Hypixel during login
	// This causes a subsequent signature validation crash:
	// "Bad signature length: got 0 but was expecting 512"
	// and subsequently
	// "Failed to verify signature on property... <StackTrace>"
	@Inject(
		method = "getPropertySignatureState",
		at = @At(value = "INVOKE", target = "Lcom/mojang/authlib/yggdrasil/ServicesKeySet;keys"
			+ "(Lcom/mojang/authlib/yggdrasil/ServicesKeyType;)Ljava/util/Collection;"),
		cancellable = true
	)
	void hasSignatureFixed(final Property property, final CallbackInfoReturnable<SignatureState> cir)
	{
		if(property.signature().isEmpty())
		{
			LOG.warn("Prevented signer crash due to empty signature (should be null) on property {}", property);
			cir.setReturnValue(SignatureState.INVALID);
		}
	}
}
