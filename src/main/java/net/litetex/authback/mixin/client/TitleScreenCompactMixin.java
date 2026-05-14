package net.litetex.authback.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;

import net.litetex.authback.client.AuthBackClient;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;


@Mixin(TitleScreen.class)
public abstract class TitleScreenCompactMixin
{
	// NOTE: As of 2026-05 unable to modify topPos+=24 (Opcode: IADD) to 36 because OpCode is not accessible
	@Inject(
		method = "init",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/TitleScreen;showFriendsListButton(Z)Z")
	)
	void fixYPos1(
		final CallbackInfo ci,
		@Local(name = "topPos") final LocalIntRef topPos)
	{
		if(this.isCompact())
		{
			topPos.set(topPos.get() + 12);
		}
	}
	
	@SuppressWarnings("checkstyle:MagicNumber")
	@WrapOperation(
		method = "init",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;getHorizontalPosition(III)I",
			ordinal = 0
		),
		slice = @Slice(
			from = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/client/gui/screens/TitleScreen;showFriendsListButton(Z)Z"),
			to = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/client/gui/components/FriendsButton;setPosition(II)V",
				ordinal = 0
			)
		)
	)
	int correctFriendsButtonX(
		final TitleScreen instance,
		final int currentButton,
		final int numberOfButtons,
		final int buttonWidth,
		final Operation<Integer> original)
	{
		if(this.isCompact())
		{
			return instance.width / 2 - 124;
		}
		return original.call(instance, currentButton, numberOfButtons, buttonWidth);
	}
	
	@WrapOperation(
		method = "init",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;addRenderableWidget"
				+ "(Lnet/minecraft/client/gui/components/events/GuiEventListener;)"
				+ "Lnet/minecraft/client/gui/components/events/GuiEventListener;"),
		slice =
		@Slice(
			from = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/client/gui/components/CommonButtons;language"
					+ "(ILnet/minecraft/client/gui/components/Button$OnPress;Z)"
					+ "Lnet/minecraft/client/gui/components/SpriteIconButton;"),
			to = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/client/gui/components/SpriteIconButton;setPosition(II)V",
				ordinal = 1
			)
		)
	)
	GuiEventListener hideLanguageAndAccessibilityIfRequired(
		final TitleScreen instance,
		final GuiEventListener guiEventListener,
		final Operation<GuiEventListener> original)
	{
		if(this.isCompact())
		{
			return guiEventListener;
		}
		return original.call(instance, guiEventListener);
	}
	
	// NOTE: As of 2026-05 unable to wrap topPos+=24 (Opcode: IADD) because OpCode is not accessible
	@Inject(
		method = "init",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;getHorizontalPosition(III)I",
			ordinal = 2
		)
	)
	void fixYPos2(
		final CallbackInfo ci,
		@Local(name = "topPos") final LocalIntRef topPos)
	{
		if(this.isCompact())
		{
			topPos.set(topPos.get() - 24);
		}
	}
	
	@Unique
	private boolean isCompact()
	{
		return AuthBackClient.instance().config().compactTitleScreen().value();
	}
}
