package net.litetex.authback.server.keys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Suppliers;
import com.mojang.authlib.GameProfile;

import net.litetex.authback.shared.crypto.Ed25519KeyDecoder;
import net.litetex.authback.shared.json.JSONSerializer;


public class ServerProfilePublicKeysManager
{
	private static final Logger LOG = LoggerFactory.getLogger(ServerProfilePublicKeysManager.class);
	
	private static final Duration DELETE_AFTER_UNUSED_EXECUTION_INTERVAL = Duration.ofHours(1);
	
	private final Path file;
	
	private final int maxKeysPerUser;
	private final Duration deleteAfterUnused;
	
	private Instant lastDeletedAfterUnusedExecuted = Instant.MIN;
	
	private Map<String, Map<Integer, KeyInfo>> profileUUIDKeys = new HashMap<>();
	
	public ServerProfilePublicKeysManager(final Path file, final int maxKeysPerUser, final Duration deleteAfterUnused)
	{
		this.file = file;
		this.maxKeysPerUser = maxKeysPerUser;
		this.deleteAfterUnused = deleteAfterUnused;
		this.readFile();
	}
	
	public void syncFromClient(final GameProfile profile, final byte[] encodedPublicKey, final PublicKey publicKey)
	{
		final Map<Integer, KeyInfo> publicKeys = this.profileUUIDKeys.computeIfAbsent(
			profile.id().toString(),
			ignored -> Collections.synchronizedMap(new LinkedHashMap<>()));
		
		final int hash = Arrays.hashCode(encodedPublicKey);
		final Instant now = Instant.now();
		
		final KeyInfo keyInfo = publicKeys.computeIfAbsent(
			hash,
			ignored -> new KeyInfo(encodedPublicKey, () -> publicKey, now));
		if(keyInfo.lastUsedAt() != now)
		{
			publicKeys.put(hash, keyInfo.updateLastUsedAt(now));
		}
		
		if(publicKeys.size() > this.maxKeysPerUser)
		{
			final Comparator<Map.Entry<Integer, KeyInfo>> comparator =
				Comparator.<Map.Entry<Integer, KeyInfo>, Instant>comparing(e -> e.getValue().lastUsedAt())
					.reversed();
			publicKeys.entrySet()
				.stream()
				.sorted(comparator)
				.skip(this.maxKeysPerUser)
				.map(Map.Entry::getKey)
				.toList() // Collect to prevent modification
				.forEach(publicKeys::remove);
		}
		
		this.saveAsync();
	}
	
	private void readFile()
	{
		if(!Files.exists(this.file))
		{
			return;
		}
		
		final long startMs = System.currentTimeMillis();
		try
		{
			final Instant deleteBefore = Instant.now().minus(this.deleteAfterUnused);
			
			final PersistentState persistentState =
				JSONSerializer.GSON.fromJson(Files.readString(this.file), PersistentState.class);
			this.profileUUIDKeys = Collections.synchronizedMap(persistentState.getV1()
				.entrySet()
				.stream()
				.collect(Collectors.toMap(
					Map.Entry::getKey,
					e -> Collections.synchronizedMap(e.getValue().stream()
						.filter(e2 -> e2.getLastUsedAt().isAfter(deleteBefore))
						.map(e2 -> {
							try
							{
								return Map.entry(Hex.decodeHex(e2.getPublicKey()), e2);
							}
							catch(final DecoderException ex)
							{
								LOG.warn("Failed to decode public key from file", ex);
								return null;
							}
						})
						.filter(Objects::nonNull)
						.collect(Collectors.toMap(
							e3 -> Arrays.hashCode(e3.getKey()),
							e3 -> new KeyInfo(
								e3.getKey(),
								Suppliers.memoize(() -> new Ed25519KeyDecoder().decodePublic(e3.getKey())),
								e3.getValue().getLastUsedAt()
							),
							(l, r) -> r,
							LinkedHashMap::new))),
					(l, r) -> r,
					LinkedHashMap::new)));
			LOG.debug(
				"Took {}ms to read keys for {}x profiles",
				System.currentTimeMillis() - startMs,
				this.profileUUIDKeys.size());
		}
		catch(final Exception ex)
		{
			LOG.warn("Failed to read file['{}']", this.file, ex);
		}
	}
	
	private void saveAsync()
	{
		CompletableFuture.runAsync(this::saveToFile);
	}
	
	private synchronized void saveToFile()
	{
		final long startMs = System.currentTimeMillis();
		
		final Instant now = Instant.now();
		if(this.lastDeletedAfterUnusedExecuted.isBefore(now.minus(DELETE_AFTER_UNUSED_EXECUTION_INTERVAL)))
		{
			this.lastDeletedAfterUnusedExecuted = Instant.now();
			
			final Instant deleteBefore = now.minus(this.deleteAfterUnused);
			
			this.profileUUIDKeys.values()
				.forEach(publicKeys -> publicKeys.entrySet()
					.stream()
					.filter(e -> e.getValue().lastUsedAt().isBefore(deleteBefore))
					.map(Map.Entry::getKey)
					.toList() // Collect to prevent modification
					.forEach(publicKeys::remove));
		}
		
		try
		{
			final PersistentState persistentState = new PersistentState(this.profileUUIDKeys.entrySet()
				.stream()
				.collect(Collectors.toMap(
					Map.Entry::getKey,
					e -> e.getValue().values()
						.stream()
						.map(KeyInfo::persist)
						.collect(Collectors.toCollection(LinkedHashSet::new)),
					(l, r) -> r,
					LinkedHashMap::new
				)));
			
			Files.writeString(this.file, JSONSerializer.GSON.toJson(persistentState));
			LOG.debug(
				"Took {}ms to write keys for {}x profiles",
				System.currentTimeMillis() - startMs,
				this.profileUUIDKeys.size());
		}
		catch(final Exception ex)
		{
			LOG.warn("Failed to write file['{}']", this.file, ex);
		}
	}
	
	record KeyInfo(
		byte[] publicKeyEncoded,
		Supplier<PublicKey> publicKeySupplier,
		Instant lastUsedAt
	)
	{
		public KeyInfo updateLastUsedAt(final Instant now)
		{
			return new KeyInfo(this.publicKeyEncoded(), this.publicKeySupplier(), now);
		}
		
		public PersistentState.PersistentKeyInfo persist()
		{
			return new PersistentState.PersistentKeyInfo(
				Hex.encodeHexString(this.publicKeyEncoded()),
				this.lastUsedAt()
			);
		}
	}
	
	
	static class PersistentState
	{
		private Map<String, Set<PersistentKeyInfo>> v1 = new HashMap<>();
		
		public PersistentState()
		{
		}
		
		public PersistentState(final Map<String, Set<PersistentKeyInfo>> v1)
		{
			this.v1 = v1;
		}
		
		public Map<String, Set<PersistentKeyInfo>> getV1()
		{
			return this.v1;
		}
		
		public void setV1(final Map<String, Set<PersistentKeyInfo>> v1)
		{
			this.v1 = v1;
		}
		
		static class PersistentKeyInfo
		{
			private String publicKey;
			private Instant lastUsedAt;
			
			public PersistentKeyInfo()
			{
			}
			
			public PersistentKeyInfo(final String publicKey, final Instant lastUsedAt)
			{
				this.publicKey = publicKey;
				this.lastUsedAt = lastUsedAt;
			}
			
			public String getPublicKey()
			{
				return this.publicKey;
			}
			
			public void setPublicKey(final String publicKey)
			{
				this.publicKey = publicKey;
			}
			
			public Instant getLastUsedAt()
			{
				return this.lastUsedAt;
			}
			
			public void setLastUsedAt(final Instant lastUsedAt)
			{
				this.lastUsedAt = lastUsedAt;
			}
		}
	}
}
