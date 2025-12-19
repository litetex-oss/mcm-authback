package net.litetex.authback.common.gameprofile;

import static net.litetex.authback.shared.collections.AdvancedCollectors.toLinkedHashMap;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.client.ObjectMapper;

import net.litetex.authback.shared.external.com.google.common.base.Suppliers;
import net.litetex.authback.shared.io.Persister;


public class GameProfileCacheManager
{
	private static final Logger LOG = LoggerFactory.getLogger(GameProfileCacheManager.class);
	
	private static final Duration DELETE_AFTER_EXECUTION_INTERVAL = Duration.ofHours(12);
	private static final float TARGET_PROFILE_COUNT_PERCENT = 0.9f;
	
	private final ObjectMapper objectMapper = ObjectMapper.create();
	
	private final Path file;
	
	private final Duration deleteAfter;
	private Instant nextDeletedAfterExecuteTime = Instant.MIN;
	
	private final int maxTargetedProfileCount;
	private final int targetedProfileCount;
	
	private Map<String, UUID> usernameUuids = new HashMap<>();
	private Map<UUID, String> uuidUsernames = new HashMap<>(); // Reverse map for tracking when deleting
	// Using an ordered map here that always contains the latest value at the end
	// This way cleanups can be A LOT (>20x) faster
	private final SequencedMap<UUID, ProfileContainer> uuidProfileContainers = new LinkedHashMap<>();
	// For some reason there is no Collections.synchronizedSequenceMap, so this needs to be done manually
	private final Object uuidProfileContainersLock = new Object();
	
	public GameProfileCacheManager(
		final Path file,
		final Duration deleteAfter,
		final int maxTargetedProfileCount)
	{
		this.file = file;
		this.deleteAfter = deleteAfter;
		if(maxTargetedProfileCount <= 0)
		{
			throw new IllegalArgumentException("maxTargetedProfileCount needs to be > 1");
		}
		this.maxTargetedProfileCount = maxTargetedProfileCount;
		this.targetedProfileCount = Math.max(Math.round(maxTargetedProfileCount * TARGET_PROFILE_COUNT_PERCENT), 1);
		this.readFile();
	}
	
	public void add(final GameProfile profile)
	{
		LOG.debug("Add {}", profile.id());
		synchronized(this.uuidProfileContainersLock)
		{
			this.uuidProfileContainers.putLast(
				profile.id(),
				new ProfileContainer(
					this.objectMapper.writeValueAsString(profile),
					() -> profile,
					Instant.now()));
		}
		this.usernameUuids.put(profile.name(), profile.id());
		this.uuidUsernames.put(profile.id(), profile.name());
		
		this.saveAsync();
	}
	
	public GameProfile findByName(final String username)
	{
		LOG.debug("FindByName {}", username);
		
		final UUID uuid = this.usernameUuids.get(username);
		if(uuid == null)
		{
			return null;
		}
		
		return this.findByUUID(uuid);
	}
	
	public GameProfile findByUUID(final UUID id)
	{
		LOG.debug("FindByUUID {}", id);
		final long startMs = System.currentTimeMillis();
		final ProfileContainer container;
		synchronized(this.uuidProfileContainersLock)
		{
			container = this.uuidProfileContainers.get(id);
		}
		if(container == null)
		{
			return null;
		}
		
		this.cleanUpIfRequired();
		
		try
		{
			final GameProfile gameProfile = container.gameProfileSupplier().get();
			LOG.debug("Took {}ms for findByUUID[id={}] to return result", System.currentTimeMillis() - startMs, id);
			return gameProfile;
		}
		catch(final Exception ex)
		{
			LOG.warn("Failed to deserialize game profile", ex);
			this.removeWithoutLock(id); // Remove corrupted container
			return null;
		}
	}
	
	public Set<UUID> uuids()
	{
		return new HashSet<>(this.uuidUsernames.keySet());
	}
	
	public Set<String> names()
	{
		return new HashSet<>(this.usernameUuids.keySet());
	}
	
	private boolean cleanUpIfRequired()
	{
		final Instant now = Instant.now();
		if(!(this.nextDeletedAfterExecuteTime.isBefore(now)
			|| this.uuidProfileContainers.size() > this.maxTargetedProfileCount))
		{
			return false;
		}
		
		LOG.debug("Executing cleanup");
		this.nextDeletedAfterExecuteTime = now.plus(DELETE_AFTER_EXECUTION_INTERVAL);
		
		boolean requiresSaving;
		final Instant deleteBefore = now.minus(this.deleteAfter);
		
		final long startMs = System.currentTimeMillis();
		synchronized(this.uuidProfileContainersLock)
		{
			final List<Map.Entry<UUID, ProfileContainer>> entriesToDelete = new ArrayList<>();
			for(final Map.Entry<UUID, ProfileContainer> entry : this.uuidProfileContainers.entrySet())
			{
				// As the map is ordered: Abort everything else after the first entry that is valid
				if(!entry.getValue().createdAt().isBefore(deleteBefore))
				{
					break;
				}
				entriesToDelete.add(entry);
			}
			
			this.removeAllWithoutLock(entriesToDelete.stream());
			requiresSaving = !entriesToDelete.isEmpty();
		}
		
		final long start2Ms = System.currentTimeMillis();
		LOG.debug("Cleanup with isBefore took {}ms", start2Ms - startMs);
		
		if(this.uuidProfileContainers.size() > this.targetedProfileCount)
		{
			synchronized(this.uuidProfileContainersLock)
			{
				this.removeAllWithoutLock(this.uuidProfileContainers.entrySet()
					.stream()
					.limit(Math.max(this.uuidProfileContainers.size() - this.targetedProfileCount, 0)));
			}
			
			requiresSaving = true;
			LOG.debug("Cleanup trim to targetedCacheSize took {}ms", System.currentTimeMillis() - start2Ms);
		}
		
		return requiresSaving;
	}
	
	private void removeAllWithoutLock(final Stream<Map.Entry<UUID, ProfileContainer>> stream)
	{
		stream.map(Map.Entry::getKey)
			.toList() // Collect to prevent modification
			.forEach(this::removeWithoutLock);
	}
	
	private void removeWithoutLock(final UUID uuid)
	{
		this.uuidProfileContainers.remove(uuid);
		final String usernameToRemove = this.uuidUsernames.remove(uuid);
		if(usernameToRemove != null)
		{
			this.usernameUuids.remove(usernameToRemove);
		}
	}
	
	private void readFile()
	{
		final long startMs = System.currentTimeMillis();
		try
		{
			final Instant deleteBefore = Instant.now().minus(this.deleteAfter);
			
			final PersistentState persistentState = Persister.tryRead(LOG, this.file, PersistentState.class)
				.orElseGet(PersistentState::new);
			
			final Map<String, UUID> stringToUUIDCache = new HashMap<>(persistentState.ensureIdProfiles().size());
			final Function<String, UUID> stringToUUIDFunc =
				s -> stringToUUIDCache.computeIfAbsent(s, UUID::fromString);
			
			synchronized(this.uuidProfileContainersLock)
			{
				this.uuidProfileContainers.clear();
				this.uuidProfileContainers.putAll(persistentState.ensureIdProfiles()
					.entrySet()
					.stream()
					.filter(e -> e.getValue().createdAt().isAfter(deleteBefore))
					.collect(toLinkedHashMap(
						e -> stringToUUIDFunc.apply(e.getKey()),
						e -> new ProfileContainer(
							e.getValue().serializedGameProfile(),
							Suppliers.memoize(() -> this.objectMapper.readValue(
								e.getValue().serializedGameProfile(),
								GameProfile.class)),
							e.getValue().createdAt()
						))));
			}
			
			this.usernameUuids = Collections.synchronizedMap(persistentState.ensureUsernameUUIDs()
				.entrySet()
				.stream()
				.map(e -> Map.entry(e.getKey(), stringToUUIDFunc.apply(e.getValue())))
				.filter(e -> {
					synchronized(this.uuidProfileContainersLock)
					{
						return this.uuidProfileContainers.containsKey(e.getValue());
					}
				})
				.collect(toLinkedHashMap(Map.Entry::getKey, Map.Entry::getValue)));
			this.uuidUsernames = Collections.synchronizedMap(this.usernameUuids.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)));
			
			LOG.debug(
				"Took {}ms to read {}x profiles",
				System.currentTimeMillis() - startMs,
				this.uuidProfileContainers.size());
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
		
		final LinkedHashMap<UUID, ProfileContainer> uuidProfileContainerSaveMap;
		synchronized(this.uuidProfileContainersLock)
		{
			uuidProfileContainerSaveMap = new LinkedHashMap<>(this.uuidProfileContainers);
		}
		
		LOG.debug("Saving {}x profiles", uuidProfileContainerSaveMap.size());
		Persister.trySave(
			LOG,
			this.file,
			() -> new PersistentState(
				this.usernameUuids.entrySet()
					.stream()
					.collect(toLinkedHashMap(
						Map.Entry::getKey,
						e -> e.getValue().toString()
					)),
				uuidProfileContainerSaveMap.entrySet()
					.stream()
					.collect(toLinkedHashMap(
						e -> e.getKey().toString(),
						e -> e.getValue().persist()
					))));
	}
	
	record ProfileContainer(
		String serializedGameProfile,
		Supplier<GameProfile> gameProfileSupplier,
		Instant createdAt
	)
	{
		public PersistentState.PersistentProfileContainer persist()
		{
			return new PersistentState.PersistentProfileContainer(
				this.serializedGameProfile(),
				this.createdAt()
			);
		}
	}
	
	
	record PersistentState(
		Map<String, String> usernameUUIDs,
		Map<String, PersistentProfileContainer> idProfiles
	)
	{
		public PersistentState()
		{
			this(new LinkedHashMap<>(), new LinkedHashMap<>());
		}
		
		Map<String, String> ensureUsernameUUIDs()
		{
			return this.usernameUUIDs != null ? this.usernameUUIDs : Map.of();
		}
		
		Map<String, PersistentProfileContainer> ensureIdProfiles()
		{
			return this.idProfiles != null ? this.idProfiles : Map.of();
		}
		
		record PersistentProfileContainer(
			String serializedGameProfile,
			Instant createdAt
		)
		{
		}
	}
}
