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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;


@Mixin(TitleScreen.class)
public abstract class TitleScreenCompactMixin extends Screen
{
	// NOTE: As of 2026-05 unable to modify topPos+=24 (Opcode: IADD) to 36 because OpCode is not accessible
	@WrapOperation(
		method = "init",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;createDemoMenuOptions(II)I"),
		require = 0
	)
	int fixYPos1Demo(
		final TitleScreen instance,
		final int topPos,
		final int spacing,
		final Operation<Integer> original)
	{
		return this.fixYPos1(instance, topPos, spacing, original);
	}
	
	@WrapOperation(
		method = "init",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;createNormalMenuOptions(II)I"),
		require = 0
	)
	int fixYPos1Normal(
		final TitleScreen instance,
		final int topPos,
		final int spacing,
		final Operation<Integer> original)
	{
		return this.fixYPos1(instance, topPos, spacing, original);
	}
	
	@Unique
	private int fixYPos1(
		final TitleScreen instance,
		final int topPos,
		final int spacing,
		final Operation<Integer> original)
	{
		return original.call(instance, topPos, spacing)
			+ (this.isCompact() ? 12 : 0);
	}
	
	// region Hide friends button if friends-list is disabled
	@WrapOperation(
		method = "init",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;addRenderableWidget"
				+ "(Lnet/minecraft/client/gui/components/events/GuiEventListener;)"
				+ "Lnet/minecraft/client/gui/components/events/GuiEventListener;",
			ordinal = 0
		),
		require = 0
	)
	GuiEventListener addRenderableWidgetFriendList(
		final TitleScreen instance,
		final GuiEventListener guiEventListener,
		final Operation<GuiEventListener> original)
	{
		if(this.isCompact() && !this.minecraft.getPlayerSocialManager().isFriendListEnabled())
		{
			return null;
		}
		
		return original.call(instance, guiEventListener);
	}
	
	@WrapOperation(
		method = "init",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/FriendsButton;setPosition(II)V"
		),
		require = 0
	)
	void addRenderableWidgetFriendList(
		final FriendsButton instance,
		final int x,
		final int y,
		final Operation<Void> original)
	{
		if(instance != null)
		{
			original.call(instance, x, y);
		}
	}
	// endregion
	
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
				value = "HEAD"
			),
			to = @At(
				value = "INVOKE",
				target = "Lnet/minecraft/client/gui/components/FriendsButton;setPosition(II)V",
				ordinal = 0
			)
		),
		require = 0
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
		),
		require = 0
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
		),
		require = 0
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
	
	public TitleScreenCompactMixin(final Component title)
	{
		super(title);
	}
	
	public TitleScreenCompactMixin(
		final Minecraft minecraft,
		final Font font,
		final Component title)
	{
		super(minecraft, font, title);
	}
}
