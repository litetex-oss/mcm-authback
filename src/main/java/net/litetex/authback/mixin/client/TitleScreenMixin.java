package net.litetex.authback.mixin.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.realmsclient.gui.screens.RealmsNotificationsScreen;

import net.litetex.authback.client.AuthBackClient;
import net.minecraft.client.gui.screens.TitleScreen;


@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin
{
	@Unique
	private static final Logger LOG = LoggerFactory.getLogger(TitleScreenMixin.class);
	
	@WrapOperation(
		method = "init",
		at = @At(
			value = "NEW",
			target = "()Lcom/mojang/realmsclient/gui/screens/RealmsNotificationsScreen;")
	)
	RealmsNotificationsScreen createRealmsNotificationScreen(final Operation<RealmsNotificationsScreen> original)
	{
		if(AuthBackClient.instance().config().blockRealmsFetching().value())
		{
			LOG.debug("Preventing realms notification screen from existing and fetching data");
			return null;
		}
		
		return original.call();
	}
}
