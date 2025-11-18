package net.litetex.authback.client;

import java.security.KeyPair;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.litetex.authback.client.keys.ClientKeysManager;
import net.litetex.authback.shared.AuthBack;
import net.litetex.authback.shared.crypto.Ed25519Signature;
import net.litetex.authback.shared.network.ChannelNames;
import net.litetex.authback.shared.network.configuration.ConfigurationRegistrySetup;
import net.litetex.authback.shared.network.configuration.SyncPayloadC2S;
import net.litetex.authback.shared.network.configuration.SyncPayloadS2C;
import net.litetex.authback.shared.network.login.LoginCompatibility;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;


public class AuthBackClient extends AuthBack
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackClient.class);
	
	private static AuthBackClient instance;
	
	public static AuthBackClient instance()
	{
		return instance;
	}
	
	public static void setInstance(final AuthBackClient instance)
	{
		AuthBackClient.instance = instance;
	}
	
	private final ClientKeysManager clientKeysManager;
	
	// Replaces the server blocklist check with a dummy that will do no initial fetching
	private final boolean blockAddressCheck;
	// Will block fetching of profile/chat-signing keys
	// This will result in not being able to join servers that have enforce-secure-profile set to true (default value)
	private final boolean blockFetchingProfileKeys;
	// Blocks initial fetching of Realms news, notifications, etc
	private final boolean blockRealmsFetching;
	// Suppresses all joinServer errors
	// WARNING: Allows to join servers with possibly invalid session data
	private final boolean suppressAllServerJoinErrors;
	
	public AuthBackClient()
	{
		super("client");
		this.clientKeysManager = new ClientKeysManager(this.authbackDir);
		
		this.setupProtoLogin();
		this.setupProtoConfiguration();
		
		this.blockFetchingProfileKeys = this.config.getBoolean("block-profile-keys-fetching", false);
		this.blockAddressCheck = this.config.getBoolean("block-address-check", false);
		this.blockRealmsFetching = this.config.getBoolean("block-realms-fetching", false);
		this.suppressAllServerJoinErrors = this.config.getBoolean("suppress-all-server-join-errors", false);
		
		LOG.debug("Initialized");
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
	
	public boolean isBlockFetchingProfileKeys()
	{
		return this.blockFetchingProfileKeys;
	}
	
	public boolean isBlockAddressCheck()
	{
		return this.blockAddressCheck;
	}
	
	public boolean isBlockRealmsFetching()
	{
		return this.blockRealmsFetching;
	}
	
	public boolean isSuppressAllServerJoinErrors()
	{
		return this.suppressAllServerJoinErrors;
	}
}
