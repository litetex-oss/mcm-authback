package net.litetex.authback.mixin.client;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.ForcedUsernameChangeException;
import com.mojang.authlib.exceptions.InsufficientPrivilegesException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.exceptions.UserBannedException;
import com.mojang.authlib.minecraft.MinecraftSessionService;

import net.litetex.authback.client.AuthBackClient;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;


@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerImplMixin
{
	@Unique
	private static final Logger LOG = LoggerFactory.getLogger(ClientHandshakePacketListenerImplMixin.class);
	
	@Redirect(
		method = "authenticateServer",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/authlib/minecraft/MinecraftSessionService;joinServer"
				+ "(Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;)V"))
	private void redirectJoinServer(
		final MinecraftSessionService sessionService,
		final UUID uuid,
		final String authToken,
		final String serverId) throws AuthenticationException
	{
		try
		{
			sessionService.joinServer(uuid, authToken, serverId);
		}
		catch(final AuthenticationUnavailableException ex)
		{
			LOG.warn("Authentication servers were unavailable while calling joinServer - Suppressing", ex);
		}
		catch(final AuthenticationException ex)
		{
			if(!AuthBackClient.instance().config().suppressAllServerJoinErrors().value()
				&& (ex instanceof InvalidCredentialsException
				|| ex instanceof InsufficientPrivilegesException
				|| ex instanceof ForcedUsernameChangeException
				|| ex instanceof UserBannedException))
			{
				throw ex;
			}
			LOG.warn("General exception while calling joinServer - Suppressing", ex);
		}
	}
}
