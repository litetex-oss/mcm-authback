package net.litetex.authback.server.network;

import java.security.PublicKey;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.litetex.authback.server.keys.ServerProfilePublicKeysManager;
import net.litetex.authback.shared.crypto.Ed25519KeyDecoder;
import net.litetex.authback.shared.crypto.Ed25519Signature;
import net.litetex.authback.shared.crypto.SecureRandomByteArrayCreator;
import net.litetex.authback.shared.network.configuration.ConfigurationRegistrySetup;
import net.litetex.authback.shared.network.configuration.SyncPayloadC2S;
import net.litetex.authback.shared.network.configuration.SyncPayloadS2C;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;


public class AuthBackServerNetworking
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackServerNetworking.class);
	
	private final Set<Connection> connectionsToSkipUpToDateCheck;
	private final ServerProfilePublicKeysManager serverProfilePublicKeysManager;
	
	public AuthBackServerNetworking(
		final Set<Connection> connectionsToSkipUpToDateCheck,
		final ServerProfilePublicKeysManager serverProfilePublicKeysManager)
	{
		this.connectionsToSkipUpToDateCheck = connectionsToSkipUpToDateCheck;
		this.serverProfilePublicKeysManager = serverProfilePublicKeysManager;
		
		this.setupProtoConfiguration();
	}
	
	private void setupProtoConfiguration()
	{
		ConfigurationRegistrySetup.setup();
		
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			final GameProfile profile = handler.getOwner();
			
			if(this.connectionsToSkipUpToDateCheck.remove(handler.connection))
			{
				LOG.debug("Skipping up-to-date check for {}", profile.id());
				return;
			}
			
			if(!ServerConfigurationNetworking.canSend(handler, SyncPayloadS2C.ID))
			{
				LOG.debug("Unable to send {} to {}", SyncPayloadS2C.ID, profile.id());
				return;
			}
			
			final byte[] challenge = SecureRandomByteArrayCreator.create(4);
			
			this.registerUpToDateCheckPacketReceiver(handler, challenge, profile);
			
			handler.send(new ClientboundCustomPayloadPacket(new SyncPayloadS2C(challenge)));
		});
	}
	
	private void registerUpToDateCheckPacketReceiver(
		final ServerConfigurationPacketListenerImpl originalHandler,
		final byte[] challenge,
		final GameProfile profile)
	{
		ServerConfigurationNetworking.registerReceiver(
			originalHandler,
			SyncPayloadC2S.ID,
			(payload, context) -> {
				final PublicKey publicKey = new Ed25519KeyDecoder().decodePublic(payload.publicKey());
				
				if(!Ed25519Signature.isValidSignature(challenge, payload.signature(), publicKey))
				{
					LOG.debug("Received invalid signature from {}", profile.id());
					return;
				}
				
				this.serverProfilePublicKeysManager.add(profile.id(), payload.publicKey(), publicKey);
			}
		);
	}
}
