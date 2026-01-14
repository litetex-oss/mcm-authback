package net.litetex.authback.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;


@SuppressWarnings("checkstyle:VisibilityModifier")
@Mixin(Options.class)
public abstract class OptionsMixin
{
	@Inject(
		method = "load",
		at = @At("HEAD"),
		order = 999
	)
	void overrideDefaultAnnoyingValues(final CallbackInfo ci)
	{
		// Replace default values with ones that are not annoying (during debugging)
		
		// There is no reason to annoy 99.999% of players with the narrator because they will NEVER need it
		this.onboardAccessibility = false;
		// Number of accidentally enabled? YES!
		this.narratorHotkey.set(false);
		
		// "During online play, you may be exposed to unmoderated chat messages or
		// other types of user-generated content that may not be suitable for everyone"
		// Welcome to the internet
		this.skipMultiplayerWarning = true;
		
		// The first thing you see when joining a server is,
		// how you can block or report other players/your friends.
		// Isn't that nice?
		// Same thing is also in ESC menu
		this.joinedFirstServer = true;
	}
	
	@Shadow
	public boolean onboardAccessibility;
	
	@Shadow
	public boolean skipMultiplayerWarning;
	
	@Shadow
	public boolean joinedFirstServer;
	
	@Shadow
	@Final
	private OptionInstance<Boolean> narratorHotkey;
}
