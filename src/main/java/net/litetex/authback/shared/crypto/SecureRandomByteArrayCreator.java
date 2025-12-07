package net.litetex.authback.shared.crypto;

import java.security.SecureRandom;
import java.util.Random;


public final class SecureRandomByteArrayCreator
{
	private static final Random RANDOM = new SecureRandom();
	
	public static byte[] create(final int length)
	{
		final byte[] data = new byte[length];
		RANDOM.nextBytes(data);
		return data;
	}
	
	private SecureRandomByteArrayCreator()
	{
	}
}
