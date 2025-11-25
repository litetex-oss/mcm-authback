package net.litetex.authback.server.keys;

import static net.litetex.authback.shared.collections.AdvancedCollectors.toLinkedHashMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Suppliers;

import net.litetex.authback.shared.crypto.Ed25519KeyDecoder;
import net.litetex.authback.shared.json.JSONSerializer;


public class ServerProfilePublicKeysManager
{
	private static final Logger LOG = LoggerFactory.getLogger(ServerProfilePublicKeysManager.class);
	
	private static final Duration DELETE_AFTER_UNUSED_EXECUTION_INTERVAL = Duration.ofHours(12);
	
	private final Path file;
	
	private final int maxKeysPerUser;
	private final Duration deleteAfterUnused;
	
	private Instant nextDeleteAfterUnusedExecutionTime = Instant.now().plus(DELETE_AFTER_UNUSED_EXECUTION_INTERVAL);
	
	private Map<UUID, Map<Integer, KeyInfo>> profileUUIDKeys = new HashMap<>();
	
	public ServerProfilePublicKeysManager(final Path file, final int maxKeysPerUser, final Duration deleteAfterUnused)
	{
		this.file = file;
		this.maxKeysPerUser = maxKeysPerUser;
		this.deleteAfterUnused = deleteAfterUnused;
		this.readFile();
	}
	
	public void add(final UUID uuid, final byte[] encodedPublicKey, final PublicKey publicKey)
	{
		final Map<Integer, KeyInfo> publicKeys = this.profileUUIDKeys.computeIfAbsent(
			uuid,
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
	
	// Quick check if there are any keys without validating if a key is valid
	public boolean hasAnyKeyQuickCheck(final UUID profileUUID)
	{
		return this.profileUUIDKeys.get(profileUUID) != null;
	}
	
	public PublicKey find(final UUID profileUUID, final byte[] encodedPublicKey)
	{
		final Map<Integer, KeyInfo> keyInfos = this.profileUUIDKeys.get(profileUUID);
		if(keyInfos == null)
		{
			return null;
		}
		
		this.cleanUpIfRequired();
		
		final int hashedPublicKey = Arrays.hashCode(encodedPublicKey);
		final KeyInfo keyInfo = keyInfos.get(hashedPublicKey);
		if(keyInfo == null)
		{
			return null;
		}
		
		try
		{
			return keyInfo.publicKeySupplier().get();
		}
		catch(final Exception ex)
		{
			LOG.warn("Failed to deserialize public key", ex);
			keyInfos.remove(hashedPublicKey);
			if(keyInfos.isEmpty())
			{
				this.profileUUIDKeys.remove(profileUUID);
			}
			
			return null;
		}
	}
	
	public int removeAll(final UUID uuid)
	{
		final Map<Integer, KeyInfo> keyInfos = this.profileUUIDKeys.remove(uuid);
		if(keyInfos == null)
		{
			return 0;
		}
		
		return keyInfos.size();
	}
	
	public boolean remove(final UUID uuid, final String publicKeyEncoded)
	{
		final Map<Integer, KeyInfo> keyInfos = this.profileUUIDKeys.get(uuid);
		if(keyInfos == null)
		{
			return false;
		}
		
		try
		{
			if(keyInfos.remove(Arrays.hashCode(Hex.decodeHex(publicKeyEncoded))) == null)
			{
				return false;
			}
		}
		catch(final DecoderException dex)
		{
			return false;
		}
		
		if(keyInfos.isEmpty())
		{
			this.profileUUIDKeys.remove(uuid);
		}
		return true;
	}
	
	public Set<UUID> profileUUIDs()
	{
		return new HashSet<>(this.profileUUIDKeys.keySet());
	}
	
	public Map<UUID, List<PublicKeyInfo>> uuidPublicKeyHex()
	{
		return this.profileUUIDKeys.entrySet()
			.stream()
			.collect(Collectors.toMap(
				Map.Entry::getKey,
				e -> e.getValue().values()
					.stream()
					.map(k -> new PublicKeyInfo(Hex.encodeHexString(k.publicKeyEncoded()), k.lastUsedAt()))
					.sorted(Comparator.comparing(PublicKeyInfo::lastUse))
					.toList()
			));
	}
	
	public record PublicKeyInfo(
		String hex,
		Instant lastUse)
	{
	}
	
	private synchronized void cleanUpIfRequired()
	{
		final Instant now = Instant.now();
		if(this.nextDeleteAfterUnusedExecutionTime.isBefore(now))
		{
			this.nextDeleteAfterUnusedExecutionTime = now.plus(DELETE_AFTER_UNUSED_EXECUTION_INTERVAL);
			
			final Instant deleteBefore = now.minus(this.deleteAfterUnused);
			
			this.profileUUIDKeys.values()
				.forEach(publicKeys -> publicKeys.entrySet()
					.stream()
					.filter(e -> e.getValue().lastUsedAt().isBefore(deleteBefore))
					.map(Map.Entry::getKey)
					.toList() // Collect to prevent modification
					.forEach(publicKeys::remove));
			
			this.profileUUIDKeys.entrySet()
				.stream()
				.filter(e -> e.getValue().isEmpty())
				.map(Map.Entry::getKey)
				.toList() // Collect to prevent modification
				.forEach(this.profileUUIDKeys::remove);
		}
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
			this.profileUUIDKeys = Collections.synchronizedMap(persistentState.ensureProfileUUIDKeys()
				.entrySet()
				.stream()
				.filter(e -> e.getValue() != null)
				.collect(toLinkedHashMap(
					e -> UUID.fromString(e.getKey()),
					e -> Collections.synchronizedMap(e.getValue().stream()
						.filter(e2 -> e2.lastUsedAt().isAfter(deleteBefore))
						.map(e2 -> {
							try
							{
								return Map.entry(Hex.decodeHex(e2.publicKey()), e2);
							}
							catch(final DecoderException ex)
							{
								LOG.warn("Failed to decode public key from file", ex);
								return null;
							}
						})
						.filter(Objects::nonNull)
						.collect(toLinkedHashMap(
							e3 -> Arrays.hashCode(e3.getKey()),
							e3 -> new KeyInfo(
								e3.getKey(),
								Suppliers.memoize(() -> new Ed25519KeyDecoder().decodePublic(e3.getKey())),
								e3.getValue().lastUsedAt()
							))))
				)));
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
		
		this.cleanUpIfRequired();
		
		try
		{
			final PersistentState persistentState = new PersistentState(this.profileUUIDKeys.entrySet()
				.stream()
				.collect(toLinkedHashMap(
					e -> e.getKey().toString(),
					e -> e.getValue().values()
						.stream()
						.map(KeyInfo::persist)
						.collect(Collectors.toCollection(LinkedHashSet::new))
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
	
	
	record PersistentState(
		Map<String, Set<PersistentKeyInfo>> profileUUIDKeys
	)
	{
		Map<String, Set<PersistentKeyInfo>> ensureProfileUUIDKeys()
		{
			return this.profileUUIDKeys != null ? this.profileUUIDKeys : Map.of();
		}
		
		record PersistentKeyInfo(
			String publicKey,
			Instant lastUsedAt
		)
		{
		}
	}
}
