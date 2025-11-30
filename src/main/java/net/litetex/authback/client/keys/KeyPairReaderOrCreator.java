package net.litetex.authback.client.keys;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.litetex.authback.shared.crypto.Ed25519KeyDecoder;
import net.litetex.authback.shared.crypto.Ed25519Signature;
import net.litetex.authback.shared.crypto.SecureRandomByteArrayCreator;


class KeyPairReaderOrCreator
{
	private static final Logger LOG = LoggerFactory.getLogger(KeyPairReaderOrCreator.class);
	
	private final Map<String, KeyState> keyStates;
	private final String uuid;
	
	KeyPairReaderOrCreator(
		final Map<String, KeyState> keyStates,
		final String uuid)
	{
		this.keyStates = keyStates;
		this.uuid = uuid;
	}
	
	public KeyPair getOrCreateKeyPair()
	{
		final KeyState keyState = this.keyStates.get(this.uuid);
		if(keyState == null)
		{
			return this.generate();
		}
		
		try
		{
			final KeyPair keyPair = this.decode(keyState);
			this.keyStates.put(this.uuid, keyState.lastUsedNow());
			
			LOG.info("Successfully loaded for keypair[uuid={}] - Public Key: {}", this.uuid, keyState.publicKey());
			return keyPair;
		}
		catch(final Exception ex)
		{
			LOG.warn("Failed to decode keys[uuid={}] - Regenerating them", this.uuid, ex);
			return this.generate();
		}
	}
	
	private KeyPair decode(final KeyState keyState) throws Exception
	{
		final Ed25519KeyDecoder keyDecoder = new Ed25519KeyDecoder();
		
		final KeyPair keyPair = new KeyPair(
			keyDecoder.decodePublic(Hex.decodeHex(keyState.publicKey())),
			keyDecoder.decodePrivate(Hex.decodeHex(keyState.privateKey())));
		validateKeysMatch(keyPair);
		return keyPair;
	}
	
	@SuppressWarnings("checkstyle:MagicNumber")
	private static void validateKeysMatch(final KeyPair keyPair)
	{
		final byte[] challenge = SecureRandomByteArrayCreator.create(4);
		if(!Ed25519Signature.isValidSignature(
			challenge,
			Ed25519Signature.createSignature(challenge, keyPair.getPrivate()),
			keyPair.getPublic()))
		{
			throw new IllegalStateException("Signature match failed");
		}
	}
	
	private KeyPair generate()
	{
		final KeyPair keyPair = createKeyPairGenerator().generateKeyPair();
		
		final Instant now = Instant.now();
		final String publicKeyHex = Hex.encodeHexString(keyPair.getPublic().getEncoded());
		this.keyStates.put(
			this.uuid,
			new KeyState(
				Hex.encodeHexString(keyPair.getPrivate().getEncoded()),
				publicKeyHex,
				now,
				now));
		LOG.info("Generated new keypair - Public key: {}", publicKeyHex);
		
		return keyPair;
	}
	
	private static KeyPairGenerator createKeyPairGenerator()
	{
		try
		{
			return KeyPairGenerator.getInstance("Ed25519");
		}
		catch(final NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("Failed to find ED25519 algorithm", e);
		}
	}
}
