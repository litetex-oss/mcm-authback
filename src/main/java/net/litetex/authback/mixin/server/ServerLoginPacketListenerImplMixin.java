package net.litetex.authback.mixin.server;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PrivateKey;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;

import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.yggdrasil.ProfileResult;

import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;


@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin
{
	@Unique
	private static final int HEX_RADIX = 16;
	
	@Inject(
		method = "handleKey",
		// at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl$1;<init>
		// (Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;Ljava/lang/String;Ljava/lang/String;)V")
		at = @At(value = "HEAD"),
		cancellable = true)
	void handleKey(final ServerboundKeyPacket serverboundKeyPacket, final CallbackInfo ci)
	{
		final ServerLoginPacketListenerImpl self = (ServerLoginPacketListenerImpl)(Object)this;
		
		Validate.validState(self.state == ServerLoginPacketListenerImpl.State.KEY, "Unexpected key packet");
		
		final String serverId;
		try
		{
			final PrivateKey privateKey = self.server.getKeyPair().getPrivate();
			if(!serverboundKeyPacket.isChallengeValid(self.challenge, privateKey))
			{
				throw new IllegalStateException("Protocol error");
			}
			
			final SecretKey secretKey = serverboundKeyPacket.getSecretKey(privateKey);
			final Cipher decryptCipher = Crypt.getCipher(Cipher.DECRYPT_MODE, secretKey);
			final Cipher encryptCipher = Crypt.getCipher(Cipher.ENCRYPT_MODE, secretKey);
			serverId = new BigInteger(Crypt.digestData("", self.server.getKeyPair().getPublic(), secretKey))
				.toString(HEX_RADIX);
			self.state = ServerLoginPacketListenerImpl.State.AUTHENTICATING;
			self.connection.setEncryptionKey(decryptCipher, encryptCipher);
		}
		catch(final CryptException ex)
		{
			throw new IllegalStateException("Protocol error", ex);
		}
		
		final Thread thread = new Thread("User Authenticator #"
			+ ServerLoginPacketListenerImpl.UNIQUE_THREAD_ID.incrementAndGet())
		{
			@Override
			public void run()
			{
				final String username = Objects.requireNonNull(
					self.requestedUsername,
					"Player name not initialized");
				
				try
				{
					final ProfileResult profileResult = self.server.services()
						.sessionService()
						.hasJoinedServer(username, serverId, this.getAddress());
					if(profileResult != null)
					{
						final GameProfile gameProfile = profileResult.profile();
						ServerLoginPacketListenerImpl.LOGGER.info(
							"UUID of player {} is {}",
							gameProfile.name(),
							gameProfile.id());
						self.startClientVerification(gameProfile);
					}
					else if(self.server.isSingleplayer())
					{
						ServerLoginPacketListenerImpl.LOGGER.warn(
							"Failed to verify username but will let them in anyway!");
						self.startClientVerification(UUIDUtil.createOfflineProfile(username));
					}
					else
					{
						self.disconnect(Component.translatable(
							"multiplayer.disconnect.unverified_username"));
						ServerLoginPacketListenerImpl.LOGGER.error(
							"Username '{}' tried to join with an invalid session",
							username);
					}
				}
				catch(final AuthenticationUnavailableException var4)
				{
					if(self.server.isSingleplayer())
					{
						ServerLoginPacketListenerImpl.LOGGER.warn(
							"Authentication servers are down but will let them in anyway!");
						self.startClientVerification(UUIDUtil.createOfflineProfile(username));
					}
					else
					{
						self.disconnect(Component.translatable(
							"multiplayer.disconnect.authservers_down"));
						ServerLoginPacketListenerImpl.LOGGER.error(
							"Couldn't verify username because servers are unavailable");
					}
				}
			}
			
			@Nullable
			private InetAddress getAddress()
			{
				final SocketAddress socketAddress = self.connection.getRemoteAddress();
				return self.server.getPreventProxyConnections()
					&& socketAddress instanceof InetSocketAddress
					? ((InetSocketAddress)socketAddress).getAddress()
					: null;
			}
		};
		thread.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler(ServerLoginPacketListenerImpl.LOGGER));
		thread.start();
		
		ci.cancel();
	}
}
