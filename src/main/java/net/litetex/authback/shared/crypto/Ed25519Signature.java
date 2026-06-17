package net.litetex.authback.shared.crypto;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;


public final class Ed25519Signature
{
	public static byte[] createSignature(final byte[] data, final PrivateKey privateKey)
	{
		try
		{
			final Signature signature = createSignature();
			signature.initSign(privateKey);
			signature.update(data);
			return signature.sign();
		}
		catch(final InvalidKeyException | SignatureException e)
		{
			throw new IllegalStateException("Failed to create signature", e);
		}
	}
	
	public static boolean isValidSignature(final byte[] data, final byte[] signatureBytes, final PublicKey publicKey)
	{
		try
		{
			final Signature signature = createSignature();
			signature.initVerify(publicKey);
			signature.update(data);
			
			return signature.verify(signatureBytes);
		}
		catch(final InvalidKeyException | SignatureException e)
		{
			throw new IllegalStateException("Failed to verify signature", e);
		}
	}
	
	private static Signature createSignature()
	{
		try
		{
			return Signature.getInstance("Ed25519");
		}
		catch(final NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("Failed to find ED25519 algorithm", e);
		}
	}
	
	private Ed25519Signature()
	{
	}
}
