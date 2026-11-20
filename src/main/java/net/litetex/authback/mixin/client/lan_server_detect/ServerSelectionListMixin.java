package net.litetex.authback.mixin.client.lan_server_detect;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.litetex.authback.client.AuthBackClient;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;


@Mixin(ServerSelectionList.class)
public abstract class ServerSelectionListMixin
{
	@WrapOperation(
		method = "refreshEntries",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/List;add(Ljava/lang/Object;)Z")
	)
	@SuppressWarnings("rawtypes")
	boolean addLanHeader(final List instance, final Object e, final Operation<Boolean> original)
	{
		if(AuthBackClient.instance().config().disableLANServerDetection().value())
		{
			return true;
		}
		
		return original.call(instance, e);
	}
}
