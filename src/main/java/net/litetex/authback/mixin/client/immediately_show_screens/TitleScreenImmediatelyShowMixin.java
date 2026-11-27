package net.litetex.authback.mixin.client.immediately_show_screens;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.litetex.authback.client.AuthBackClient;
import net.minecraft.client.gui.screens.TitleScreen;


@Mixin(TitleScreen.class)
public abstract class TitleScreenImmediatelyShowMixin
{
	@WrapOperation(
		method = "<init>(ZLnet/minecraft/client/gui/components/LogoRenderer;)V",
		at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;fading:Z",
			opcode = Opcodes.PUTFIELD),
		// Improve compatibility with other mods
		order = 1042,
		expect = 0
	)
	void init(final TitleScreen instance, final boolean value, final Operation<Void> original)
	{
		original.call(instance, !AuthBackClient.instance().config().immediatelyShowScreens().value() && value);
	}
}
