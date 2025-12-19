package net.litetex.authback.server.keys;

import static net.litetex.authback.shared.collections.AdvancedCollectors.toLinkedHashMap;

import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.litetex.authback.shared.crypto.Ed25519KeyDecoder;
import net.litetex.authback.shared.external.com.google.common.base.Suppliers;
import net.litetex.authback.shared.external.org.apache.commons.codec.DecoderException;
import net.litetex.authback.shared.external.org.apache.commons.codec.binary.Hex;
import net.litetex.authback.shared.io.Persister;
import net.litetex.authback.shared.sync.SynchronizedContainer;


public class ServerProfilePublicKeysManager
{
	private static final Logger LOG = LoggerFactory.getLogger(ServerProfilePublicKeysManager.class);
	
	private static final Duration DELETE_AFTER_UNUSED_EXECUTION_INTERVAL = Duration.ofHours(12);
	
	private final Path file;
	
	private final int maxKeysPerUser;
	private final Duration deleteAfterUnused;
	
	private Instant nextDeleteAfterUnusedExecutionTime = Instant.MIN;
	
	// Using an ordered map here that always contains the latest value at the end
	// This way cleanups can be A LOT (>20x) faster
	// For some reason there is no Collections.synchronizedSequenceMap, so this needs to be done manually
	private final SynchronizedContainer<SequencedMap<UUID, UUIDKeyInfos>> profileUUIDKeysSC =
		new SynchronizedContainer<>(new LinkedHashMap<>());
	
	public ServerProfilePublicKeysManager(final Path file, final int maxKeysPerUser, final Duration deleteAfterUnused)
	{
		this.file = file;
		this.maxKeysPerUser = maxKeysPerUser;
		this.deleteAfterUnused = deleteAfterUnused;
		this.readFile();
	}
	
	public void add(final UUID uuid, final byte[] encodedPublicKey, final PublicKey publicKey)
	{
		final var uuidKeyInfosSC = this.profileUUIDKeysSC.supplyWithLock(profileUUIDKeys -> {
			final var existingUuidKeyInfosSC = profileUUIDKeys.get(uuid);
			return profileUUIDKeys.putLast(
				uuid,
				existingUuidKeyInfosSC != null
					? existingUuidKeyInfosSC
					: new UUIDKeyInfos());
		});
		
		final int hash = Arrays.hashCode(encodedPublicKey);
		final Instant now = Instant.now();
		
		uuidKeyInfosSC.execWithLock(hashKeyInfos -> {
			final KeyInfo existingKeyInfo = hashKeyInfos.get(hash);
			hashKeyInfos.putLast(
				hash,
				existingKeyInfo != null
					? existingKeyInfo.updateLastUsedAt(now)
					: new KeyInfo(encodedPublicKey, () -> publicKey, now));
		});
		
		if(uuidKeyInfosSC.value().size() > this.maxKeysPerUser)
		{
			uuidKeyInfosSC.execWithLock(hashKeyInfos -> {
				hashKeyInfos.entrySet()
					.stream()
					.limit(Math.max(hashKeyInfos.size() - this.maxKeysPerUser, 0))
					.map(Map.Entry::getKey)
					.toList()  // Collect to prevent modification
					.forEach(hashKeyInfos::remove);
			});
		}
		
		this.saveAsync();
	}
	
	// Quick check if there are any keys without validating if a key is valid
	public boolean hasAnyKeyQuickCheck(final UUID profileUUID)
	{
		return this.getUUIDKeyInfosSC(profileUUID) != null;
	}
	
	private SynchronizedContainer<LinkedHashMap<Integer, KeyInfo>> getUUIDKeyInfosSC(final UUID profileUUID)
	{
		return this.profileUUIDKeysSC.supplyWithLock(profileUUIDKeys -> profileUUIDKeys.get(profileUUID));
	}
	
	public PublicKey find(final UUID profileUUID, final byte[] encodedPublicKey)
	{
		final var uuidKeyInfosSC = this.getUUIDKeyInfosSC(profileUUID);
		if(uuidKeyInfosSC == null)
		{
			return null;
		}
		
		this.cleanUpIfRequired();
		
		final int hashedPublicKey = Arrays.hashCode(encodedPublicKey);
		final KeyInfo keyInfo = uuidKeyInfosSC.supplyWithLock(
			hashKeyInfos -> hashKeyInfos.get(hashedPublicKey));
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
			if(uuidKeyInfosSC.supplyWithLock(hashKeyInfos -> {
				hashKeyInfos.remove(hashedPublicKey);
				return hashKeyInfos.isEmpty();
			}))
			{
				this.removeProfileUUIDKeyWithLock(profileUUID);
			}
			
			return null;
		}
	}
	
	public int removeAll(final UUID uuid)
	{
		final var uuidKeyInfosSC = this.removeProfileUUIDKeyWithLock(uuid);
		if(uuidKeyInfosSC == null)
		{
			return 0;
		}
		
		this.saveAsync();
		
		return uuidKeyInfosSC.supplyWithLock(HashMap::size);
	}
	
	public boolean remove(final UUID uuid, final String publicKeyEncoded)
	{
		final var uuidKeyInfosSC = this.getUUIDKeyInfosSC(uuid);
		if(uuidKeyInfosSC == null)
		{
			return false;
		}
		
		try
		{
			final int hash = Arrays.hashCode(Hex.decodeHex(publicKeyEncoded));
			if(uuidKeyInfosSC.supplyWithLock(hashKeyInfos -> hashKeyInfos.remove(hash)) == null)
			{
				return false;
			}
		}
		catch(final DecoderException dex)
		{
			return false;
		}
		
		if(uuidKeyInfosSC.supplyWithLock(HashMap::isEmpty))
		{
			this.removeProfileUUIDKeyWithLock(uuid);
		}
		
		this.saveAsync();
		
		return true;
	}
	
	private SynchronizedContainer<LinkedHashMap<Integer, KeyInfo>> removeProfileUUIDKeyWithLock(final UUID uuid)
	{
		return this.profileUUIDKeysSC.supplyWithLock(profileUUIDKeys -> profileUUIDKeys.remove(uuid));
	}
	
	public Set<UUID> profileUUIDs()
	{
		return this.profileUUIDKeysSC.supplyWithLock(profileUUIDKeys -> new HashSet<>(profileUUIDKeys.keySet()));
	}
	
	public Map<UUID, List<PublicKeyInfo>> uuidPublicKeyHex()
	{
		final SequencedMap<UUID, SynchronizedContainer<LinkedHashMap<Integer, KeyInfo>>> profileUUIDKeysCopy =
			this.profileUUIDKeysSC.supplyWithLock(LinkedHashMap::new);
		return profileUUIDKeysCopy.entrySet()
			.stream()
			.collect(Collectors.toMap(
				Map.Entry::getKey,
				e -> e.getValue().supplyWithLock(hashKeyInfos -> hashKeyInfos.values()
					.stream()
					.map(k -> new PublicKeyInfo(Hex.encodeHexString(k.publicKeyEncoded()), k.lastUsedAt()))
					.sorted(Comparator.comparing(PublicKeyInfo::lastUse))
					.toList())
			));
	}
	
	public record PublicKeyInfo(
		String hex,
		Instant lastUse)
	{
	}
	
	@SuppressWarnings("PMD.CognitiveComplexity") // Well documented
	private synchronized void cleanUpIfRequired()
	{
		final Instant now = Instant.now();
		if(!this.nextDeleteAfterUnusedExecutionTime.isBefore(now))
		{
			return;
		}
		
		LOG.debug("Executing cleanup");
		final long startMs = System.currentTimeMillis();
		this.nextDeleteAfterUnusedExecutionTime = now.plus(DELETE_AFTER_UNUSED_EXECUTION_INTERVAL);
		
		final Instant deleteBefore = now.minus(this.deleteAfterUnused);
		
		// The ordered structure looks like this
		// A-+1 2000-01-01
		// | -2 2000-02-01
		// B-+1 2000-01-01
		// | -2 2000-02-02
		// C--1 2000-02-03
		final LinkedHashMap<UUID, UUIDKeyInfos> profileUUIDKeysCopy =
			this.profileUUIDKeysSC.supplyWithLock(LinkedHashMap::new);
		
		final List<UUID> entriesToDelete = new ArrayList<>();
		final AtomicInteger deletedKeysCounter = new AtomicInteger(0);
		
		boolean checkForDeleteEntireEntry = true;
		for(final Map.Entry<UUID, UUIDKeyInfos> entry : profileUUIDKeysCopy.entrySet())
		{
			if(checkForDeleteEntireEntry)
			{
				// We are starting with the oldest uuids
				// Check the newest/last keyinfo of this uuid
				// If this keyinfo is expired the entire uuid must therefore be removed
				
				final Map.Entry<Integer, KeyInfo> newestLastEntry =
					entry.getValue().supplyWithLock(SequencedMap::lastEntry);
				if(newestLastEntry == null // has no keyInfos!
					|| newestLastEntry.getValue().lastUsedAt().isBefore(deleteBefore)
				)
				{
					entriesToDelete.add(entry.getKey());
					continue;
				}
				
				checkForDeleteEntireEntry = false;
			}
			
			// Check in detail if a keyinfo in an uuid is expired
			// and remove it if required
			// also remove uuids that have 0 keyinfos
			if(entry.getValue().supplyWithLock(hashKeyInfos -> {
				if(hashKeyInfos.isEmpty())
				{
					return true;
				}
				
				final List<Integer> keysToRemove = new ArrayList<>();
				for(final Map.Entry<Integer, KeyInfo> entry2 : hashKeyInfos.entrySet())
				{
					if(!entry2.getValue().lastUsedAt().isBefore(deleteBefore))
					{
						continue;
					}
					keysToRemove.add(entry2.getKey());
				}
				keysToRemove.forEach(hashKeyInfos::remove);
				deletedKeysCounter.addAndGet(keysToRemove.size());
				
				return false;
			}))
			{
				entriesToDelete.add(entry.getKey());
			}
		}
		
		if(!entriesToDelete.isEmpty())
		{
			this.profileUUIDKeysSC.execWithLock(profileUUIDKeys ->
				entriesToDelete.forEach(profileUUIDKeys::remove));
		}
		
		LOG.debug(
			"Executed cleanUp, deleted {}x UUIDs and {}x keys in {}ms",
			entriesToDelete.size(),
			deletedKeysCounter.get(),
			System.currentTimeMillis() - startMs);
	}
	
	private void readFile()
	{
		final long startMs = System.currentTimeMillis();
		try
		{
			final Instant deleteBefore = Instant.now().minus(this.deleteAfterUnused);
			
			final LinkedHashMap<UUID, UUIDKeyInfos> readProfileUUIDKeys =
				Persister.tryRead(LOG, this.file, PersistentState.class)
					.orElseGet(PersistentState::new)
					.ensureProfileUUIDKeys()
					.entrySet()
					.stream()
					.filter(e -> e.getValue() != null)
					.collect(toLinkedHashMap(
						e -> UUID.fromString(e.getKey()),
						e -> new UUIDKeyInfos(e.getValue().stream()
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
								)))))
					);
			
			this.profileUUIDKeysSC.execWithLock(profileUUIDKeys -> {
				profileUUIDKeys.clear();
				profileUUIDKeys.putAll(readProfileUUIDKeys);
			});
			
			LOG.debug(
				"Took {}ms to read keys for {}x profiles",
				System.currentTimeMillis() - startMs,
				this.profileUUIDKeysSC.value().size());
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
		this.cleanUpIfRequired();
		
		final LinkedHashMap<String, Set<PersistentState.PersistentKeyInfo>> mapToSave =
			this.profileUUIDKeysSC.supplyWithLock(profileUUIDKeys ->
				profileUUIDKeys.entrySet()
					.stream()
					.collect(toLinkedHashMap(
						e -> e.getKey().toString(),
						e -> e.getValue().supplyWithLock(map -> new ArrayList<>(map.values()))
							.stream()
							.map(KeyInfo::persist)
							.collect(Collectors.toCollection(LinkedHashSet::new))
					))
			);
		
		LOG.debug("Saving {}x profiles", mapToSave.size());
		Persister.trySave(
			LOG,
			this.file,
			() -> new PersistentState(mapToSave));
	}
	
	static class UUIDKeyInfos extends SynchronizedContainer<LinkedHashMap<Integer, KeyInfo>>
	{
		public UUIDKeyInfos()
		{
			this(new LinkedHashMap<>());
		}
		
		public UUIDKeyInfos(final LinkedHashMap<Integer, KeyInfo> value)
		{
			super(value);
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
		public PersistentState()
		{
			this(new LinkedHashMap<>());
		}
		
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
