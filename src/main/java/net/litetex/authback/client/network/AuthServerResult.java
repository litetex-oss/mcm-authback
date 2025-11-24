package net.litetex.authback.client.network;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.ForcedUsernameChangeException;
import com.mojang.authlib.exceptions.InsufficientPrivilegesException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.exceptions.UserBannedException;

import net.minecraft.network.chat.Component;


public record AuthServerResult(
	Type type,
	@Nullable
	AuthenticationException exception
)
{
	public AuthServerResult(final Type type)
	{
		this(type, null);
	}
	
	public Component toComponent()
	{
		if(this.exception == null)
		{
			return null;
		}
		final Object arg1 = switch(this.exception)
		{
			case final AuthenticationUnavailableException ignored ->
				Component.translatable("disconnect.loginFailedInfo.serversUnavailable");
			case final InvalidCredentialsException ignored ->
				Component.translatable("disconnect.loginFailedInfo.invalidSession");
			case final InsufficientPrivilegesException ignored ->
				Component.translatable("disconnect.loginFailedInfo.insufficientPrivileges");
			case final UserBannedException ignored -> Component.translatable("disconnect.loginFailedInfo.userBanned");
			case final ForcedUsernameChangeException ignored ->
				Component.translatable("disconnect.loginFailedInfo.userBanned");
			default -> this.exception.getMessage();
		};
		
		return Component.translatable("disconnect.loginFailedInfo", arg1);
	}
	
	// GUI - Options
	// ByPass Type
	// OFF
	// AUTH_SERVER_UNAVAILABLE/GENERAL (DEFAULT)
	// ALL
	public enum Type
	{
		SUCCESS,
		AUTH_SERVER_UNAVAILABLE,
		INVALID_SESSION,
		MULTI_PLAYER_DISABLED, // in MS acc
		BANNED,
		GENERAL
	}
}
