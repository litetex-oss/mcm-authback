package net.litetex.authback.client.network;

import java.security.KeyPair;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.litetex.authback.client.keys.ClientKeysManager;
import net.litetex.authback.shared.crypto.Ed25519Signature;
import net.litetex.authback.shared.network.ChannelNames;
import net.litetex.authback.shared.network.configuration.ConfigurationRegistrySetup;
import net.litetex.authback.shared.network.configuration.SyncPayloadC2S;
import net.litetex.authback.shared.network.configuration.SyncPayloadS2C;
import net.litetex.authback.shared.network.login.LoginCompatibility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;


public class AuthBackClientNetworking
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackClientNetworking.class);
	
	private final ClientKeysManager clientKeysManager;
	
	public AuthBackClientNetworking(final ClientKeysManager clientKeysManager)
	{
		this.clientKeysManager = clientKeysManager;
		this.setupProtoLogin();
		this.setupProtoConfiguration();
	}
	
	private void setupProtoLogin()
	{
		ClientLoginNetworking.registerGlobalReceiver(
			ChannelNames.FALLBACK_AUTH,
			(client, handler, buf, callbacksConsumer) -> {
				
				LOG.debug("Fallback auth request from server");
				
				final int serverCompatibilityVersion = buf.readInt();
				if(serverCompatibilityVersion != LoginCompatibility.S2C)
				{
					LOG.warn(
						"Fallback auth request from server failed - Compatibility mismatch[server={}, client={}]",
						serverCompatibilityVersion,
						LoginCompatibility.S2C);
					return null;
				}
				
				final byte[] challenge = buf.readByteArray();
				
				final KeyPair keyPair = this.clientKeysManager.currentKeyPair();
				
				final FriendlyByteBuf responseBuf = new FriendlyByteBuf(Unpooled.buffer());
				responseBuf.writeInt(LoginCompatibility.C2S);
				responseBuf.writeByteArray(Ed25519Signature.createSignature(
					challenge,
					keyPair.getPrivate()));
				responseBuf.writeByteArray(keyPair.getPublic().getEncoded());
				
				return CompletableFuture.completedFuture(responseBuf);
			}
		);
	}
	
	private void setupProtoConfiguration()
	{
		ConfigurationRegistrySetup.setup();
		
		ClientConfigurationNetworking.registerGlobalReceiver(
			SyncPayloadS2C.ID,
			(payload, context) -> {
				if(!ClientConfigurationNetworking.canSend(SyncPayloadC2S.ID))
				{
					LOG.debug("Unable to send {}", SyncPayloadC2S.ID);
					return;
				}
				
				LOG.debug("Synchronizing with server");
				final KeyPair keyPair = this.clientKeysManager.currentKeyPair();
				context.networkHandler().send(new ServerboundCustomPayloadPacket(new SyncPayloadC2S(
					Ed25519Signature.createSignature(
						payload.challenge(),
						keyPair.getPrivate()),
					keyPair.getPublic().getEncoded()
				)));
			});
	}
}
