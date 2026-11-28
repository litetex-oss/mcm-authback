package net.litetex.authback.mixin.client;

import java.time.Instant;
import java.util.UUID;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;


@Mixin(ChatListener.class)
public abstract class ChatListenerMixin
{
	@Inject(
		method = "handleSystemMessage",
		at = @At("HEAD"),
		// Run this at last - after any other injection - as it's an optimization and replace the default
		order = 999_999_999,
		cancellable = true
	)
	void handleSystemMessageComputeOptimized(final Component message, final boolean remote, final CallbackInfo ci)
	{
		// Do not parse chat messages and look for player names in the first place when this was disabled!
		if(this.minecraft.options.hideMatchedNames().get())
		{
			final UUID guessedUUID = this.guessChatUUID(message);
			
			// Do not show message when...
			// Player is blocked
			if(this.minecraft.isBlocked(guessedUUID)
				|| guessedUUID != Util.NIL_UUID
				// User is friend only restricted and player is not a friend
				&& this.minecraft.isFriendOnlyRestricted(guessedUUID))
			{
				ci.cancel();
				return;
			}
		}
		
		final LocalPlayer receiver = this.minecraft.player;
		if(receiver != null && receiver.chatAbilities().canReceiveSystemMessages())
		{
			final ChatComponent chat = this.minecraft.gui.hud.getChat();
			if(remote)
			{
				chat.addServerSystemMessage(message);
				this.logSystemMessage(message, Instant.now());
			}
			else
			{
				chat.addClientSystemMessage(message);
			}
			
			this.minecraft.getNarrator().saySystemChatQueued(message);
		}
		
		ci.cancel();
	}
	
	@Shadow
	@Final
	private Minecraft minecraft;
	
	@Shadow
	protected abstract void logSystemMessage(final Component message, final Instant timeStamp);
	
	@Shadow
	protected abstract UUID guessChatUUID(final Component message);
}
