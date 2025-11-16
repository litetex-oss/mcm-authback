package net.litetex.authback.client.keys;

import java.time.Instant;


public record KeyState(
	String privateKey,
	String publicKey,
	Instant createdAt,
	Instant lastAccessedAt
)
{
	public KeyState lastUsedNow()
	{
		return new KeyState(
			this.privateKey(),
			this.publicKey(),
			this.createdAt(),
			Instant.now());
	}
}
