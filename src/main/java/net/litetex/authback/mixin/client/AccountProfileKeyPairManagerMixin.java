package net.litetex.authback.mixin.client;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.minecraft.UserApiService;

import net.litetex.authback.client.AuthBackClient;
import net.litetex.authback.shared.mixin.log.MixinLogger;
import net.minecraft.client.multiplayer.AccountProfileKeyPairManager;
import net.minecraft.world.entity.player.ProfileKeyPair;


@Mixin(AccountProfileKeyPairManager.class)
public abstract class AccountProfileKeyPairManagerMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.client("AccountProfileKeyPairManagerMixin");
	
	@Inject(
		method = "fetchProfileKeyPair",
		at = @At("HEAD"),
		cancellable = true)
	void fetchProfileKeyPairOverride(
		final UserApiService userApiService,
		final CallbackInfoReturnable<ProfileKeyPair> cir)
	{
		if(AuthBackClient.instance().config().blockFetchingProfileKeys().value())
		{
			LOG.debug("Blocked fetching of profile/chat-signing keys");
			cir.setReturnValue(null);
		}
	}
}
