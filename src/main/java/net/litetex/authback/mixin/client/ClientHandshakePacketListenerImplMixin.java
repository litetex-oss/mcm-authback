package net.litetex.authback.mixin.client;

import javax.crypto.Cipher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.ForcedUsernameChangeException;
import com.mojang.authlib.exceptions.InsufficientPrivilegesException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.exceptions.UserBannedException;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.litetex.authback.client.network.AuthServerResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;


@Environment(EnvType.CLIENT)
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class ClientHandshakePacketListenerImplMixin
{
	@Shadow
	@Final
	public Connection connection;
	@Unique
	private static final Logger LOG = LoggerFactory.getLogger(ClientHandshakePacketListenerImplMixin.class);
	
	// Lambda in handleHello
	@Inject(
		method = "method_2894",
		at = @At(value = "HEAD"),
		cancellable = true
	)
	void override(
		final String serverId,
		final ServerboundKeyPacket serverboundKeyPacket,
		final Cipher decryptCipher,
		final Cipher encryptCipher,
		final CallbackInfo ci)
	{
		final ClientHandshakePacketListenerImpl self = this.self();
		
		final AuthServerResult result = this.authenticateServer(self.minecraft, serverId);
		if(result.exception() != null)
		{
			final Component component = result.toComponent();
			if(self.serverData == null || !self.serverData.isLan())
			{
				self.connection.disconnect(component);
				return;
			}
			
			LOG.warn("Auth failed", result.exception());
		}
		
		// final ServerboundAuthbackKeyPacket serverboundAuthbackKeyPacket = new ServerboundAuthbackKeyPacket();
		// this.connection.send(
		// 	serverboundAuthbackKeyPacket,
		// 	channelFuture -> {
		// 		// TODO
		// 		System.out.println(channelFuture);
		// 	});
		
		System.out.println("SET_ENCRYPTION");
		
		self.setEncryption(serverboundKeyPacket, decryptCipher, encryptCipher);
		ci.cancel();
	}
	
	@Unique
	private AuthServerResult authenticateServer(final Minecraft minecraft, final String serverId)
	{
		try
		{
			minecraft.services()
				.sessionService()
				.joinServer(
					minecraft.getUser().getProfileId(),
					minecraft.getUser().getAccessToken(),
					serverId);
			return new AuthServerResult(AuthServerResult.Type.SUCCESS);
		}
		catch(final AuthenticationUnavailableException ex)
		{
			return new AuthServerResult(AuthServerResult.Type.AUTH_SERVER_UNAVAILABLE, ex);
		}
		catch(final InvalidCredentialsException ex)
		{
			return new AuthServerResult(AuthServerResult.Type.INVALID_SESSION, ex);
		}
		catch(final InsufficientPrivilegesException ex)
		{
			return new AuthServerResult(AuthServerResult.Type.MULTI_PLAYER_DISABLED, ex);
		}
		catch(final ForcedUsernameChangeException | UserBannedException ex)
		{
			return new AuthServerResult(AuthServerResult.Type.BANNED, ex);
		}
		catch(final AuthenticationException ex)
		{
			return new AuthServerResult(AuthServerResult.Type.GENERAL, ex);
		}
	}
	
	@Unique
	private ClientHandshakePacketListenerImpl self()
	{
		return (ClientHandshakePacketListenerImpl)(Object)this;
	}
}
