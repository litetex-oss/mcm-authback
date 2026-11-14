package net.litetex.authback.shared.crypto;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;


public class Ed25519KeyDecoder
{
	private final KeyFactory keyFactory = createKeyFactory();
	
	public PrivateKey decodePrivate(final byte[] data)
	{
		try
		{
			return this.keyFactory.generatePrivate(new PKCS8EncodedKeySpec(data));
		}
		catch(final InvalidKeySpecException e)
		{
			throw new IllegalStateException("Failed to decode private key", e);
		}
	}
	
	public PublicKey decodePublic(final byte[] data)
	{
		try
		{
			return this.keyFactory.generatePublic(new X509EncodedKeySpec(data));
		}
		catch(final InvalidKeySpecException e)
		{
			throw new IllegalStateException("Failed to decode public key", e);
		}
	}
	
	public static KeyFactory createKeyFactory()
	{
		try
		{
			return KeyFactory.getInstance("Ed25519");
		}
		catch(final NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("Failed to find ED25519 algorithm", e);
		}
	}
}
