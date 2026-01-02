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
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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
import net.litetex.authback.shared.json.JSONSerializer;
import net.litetex.authback.shared.sync.SynchronizedContainer;


public class GameProfileCacheManager
{
	private static final Logger LOG = LoggerFactory.getLogger(GameProfileCacheManager.class);
	
	private static final Duration DELETE_AFTER_EXECUTION_INTERVAL = Duration.ofHours(12);
	private static final float TARGET_PROFILE_COUNT_PERCENT = 0.9f;
	
	private final ObjectMapper objectMapper = JSONSerializer.FAST_OBJECT_MAPPER;
	
	private final Path file;
	
	private final Duration deleteAfter;
	private Instant nextDeletedAfterExecuteTime = Instant.MIN;
	
	private final int maxTargetedProfileCount;
	private final int targetedProfileCount;
	
	// Key = Owner
	private final Map<Object, Consumer<GameProfile>> onAddedProfileAsyncHandlers =
		Collections.synchronizedMap(new WeakHashMap<>());
	
	private Map<UUID, String> uuidUsernames = new HashMap<>(); // Reverse map for tracking when deleting
	private Map<String, UUID> usernameUuids = new HashMap<>();
	// Using an ordered map here that always contains the latest value at the end
	// This way cleanups can be A LOT (>20x) faster
	// For some reason there is no Collections.synchronizedSequenceMap, so this needs to be done manually
	private final SynchronizedContainer<SequencedMap<UUID, ProfileContainer>> uuidProfileContainersSC =
		new SynchronizedContainer<>(new LinkedHashMap<>());
	
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
		LOG.debug("Add {}/{}", profile.name(), profile.id());
		
		final ProfileContainer profileContainer = new ProfileContainer(
			this.objectMapper.writeValueAsString(profile),
			() -> profile,
			Instant.now());
		
		this.uuidProfileContainersSC.execWithLock(m -> m.putLast(profile.id(), profileContainer));
		final String previousName = this.uuidUsernames.put(profile.id(), profile.name());
		// Handle account name change
		if(previousName != null && !profile.name().equals(previousName))
		{
			this.usernameUuids.remove(previousName);
		}
		this.usernameUuids.put(profile.name(), profile.id());
		
		this.saveAsync();
		
		if(!this.onAddedProfileAsyncHandlers.isEmpty())
		{
			CompletableFuture.runAsync(() -> this.onAddedProfileAsyncHandlers.forEach((owner, c) -> {
				final long startMs = System.currentTimeMillis();
				try
				{
					c.accept(profile);
					LOG.debug(
						"Called onAddedProfileAsyncHandler for {} took {}ms",
						owner,
						System.currentTimeMillis() - startMs);
				}
				catch(final Exception ex)
				{
					LOG.warn("Failed to execute onAddedProfileAsyncHandler for {}", owner, ex);
				}
			}));
		}
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
		final ProfileContainer container = this.uuidProfileContainersSC.supplyWithLock(m -> m.get(id));
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
			// Remove corrupted container
			this.uuidProfileContainersSC.execWithLock(ignored -> this.removeWithoutLock(id));
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
	
	private void cleanUpIfRequired()
	{
		final Instant now = Instant.now();
		if(!(this.nextDeletedAfterExecuteTime.isBefore(now)
			|| this.uuidProfileContainersSC.value().size() > this.maxTargetedProfileCount))
		{
			return;
		}
		
		LOG.debug("Executing cleanup");
		this.nextDeletedAfterExecuteTime = now.plus(DELETE_AFTER_EXECUTION_INTERVAL);
		
		final Instant deleteBefore = now.minus(this.deleteAfter);
		
		final long startMs = System.currentTimeMillis();
		this.uuidProfileContainersSC.execWithLock(
			uuidProfileContainers -> {
				final List<Map.Entry<UUID, ProfileContainer>> entriesToDelete = new ArrayList<>();
				for(final Map.Entry<UUID, ProfileContainer> entry : uuidProfileContainers.entrySet())
				{
					// As the map is ordered: Abort everything else after the first entry that is valid
					if(!entry.getValue().createdAt().isBefore(deleteBefore))
					{
						break;
					}
					entriesToDelete.add(entry);
				}
				
				this.removeAllWithoutLock(entriesToDelete.stream());
			});
		
		final long start2Ms = System.currentTimeMillis();
		LOG.debug("Cleanup with isBefore took {}ms", start2Ms - startMs);
		
		if(this.uuidProfileContainersSC.value().size() > this.targetedProfileCount)
		{
			this.uuidProfileContainersSC.execWithLock(uuidProfileContainers ->
				this.removeAllWithoutLock(uuidProfileContainers.entrySet()
					.stream()
					.limit(Math.max(uuidProfileContainers.size() - this.targetedProfileCount, 0))));
			
			LOG.debug("Cleanup trim to targetedCacheSize took {}ms", System.currentTimeMillis() - start2Ms);
		}
	}
	
	private void removeAllWithoutLock(final Stream<Map.Entry<UUID, ProfileContainer>> stream)
	{
		stream.map(Map.Entry::getKey)
			.toList() // Collect to prevent modification
			.forEach(this::removeWithoutLock);
	}
	
	private void removeWithoutLock(final UUID uuid)
	{
		this.uuidProfileContainersSC.value().remove(uuid);
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
			
			final LinkedHashMap<UUID, ProfileContainer> deserializedUuidProfileContainers =
				persistentState.ensureIdProfiles()
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
						)));
			this.uuidProfileContainersSC.execWithLock(map -> {
				map.clear();
				map.putAll(deserializedUuidProfileContainers);
			});
			
			this.uuidUsernames = Collections.synchronizedMap(persistentState.ensureUUIDUsernames()
				.entrySet()
				.stream()
				.map(e -> {
					try
					{
						return Map.entry(stringToUUIDFunc.apply(e.getKey()), e.getValue());
					}
					catch(final Exception ex)
					{
						LOG.warn("Failed to parse", ex);
						return null;
					}
				})
				.filter(Objects::nonNull)
				.filter(e -> this.uuidProfileContainersSC.supplyWithLock(
					map -> map.containsKey(e.getKey())))
				.collect(toLinkedHashMap(Map.Entry::getKey, Map.Entry::getValue)));
			if(persistentState.usernameUUIDs != null) // Migrate legacy
			{
				persistentState.usernameUUIDs
					.entrySet()
					.stream()
					.map(e -> Map.entry(e.getKey(), stringToUUIDFunc.apply(e.getValue())))
					.filter(e -> this.uuidProfileContainersSC.supplyWithLock(
						map -> map.containsKey(e.getValue())))
					.forEach(e -> this.uuidUsernames.put(e.getValue(), e.getKey()));
			}
			this.usernameUuids = Collections.synchronizedMap(this.uuidUsernames.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey)));
			
			LOG.debug(
				"Took {}ms to read {}x profiles",
				System.currentTimeMillis() - startMs,
				this.uuidProfileContainersSC.value().size());
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
		
		final LinkedHashMap<UUID, ProfileContainer> uuidProfileContainerSaveMap =
			this.uuidProfileContainersSC.supplyWithLock(LinkedHashMap::new);
		
		LOG.debug("Saving {}x profiles", uuidProfileContainerSaveMap.size());
		Persister.trySave(
			LOG,
			this.file,
			() -> new PersistentState(
				null,
				this.uuidUsernames.entrySet()
					.stream()
					.collect(toLinkedHashMap(
						e -> e.getKey().toString(),
						Map.Entry::getValue
					)),
				uuidProfileContainerSaveMap.entrySet()
					.stream()
					.collect(toLinkedHashMap(
						e -> e.getKey().toString(),
						e -> e.getValue().persist()
					))));
	}
	
	public void registerOnAddedProfileAsyncHandlers(final Object owner, final Consumer<GameProfile> consumer)
	{
		this.onAddedProfileAsyncHandlers.put(owner, consumer);
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
		// Legacy: Can be removed after 2026-01
		@Deprecated
		Map<String, String> usernameUUIDs,
		Map<String, String> uuidUsernames,
		Map<String, PersistentProfileContainer> idProfiles
	)
	{
		public PersistentState(
			final Map<String, String> uuidUsernames,
			final Map<String, PersistentProfileContainer> idProfiles)
		{
			this(null, uuidUsernames, idProfiles);
		}
		
		public PersistentState()
		{
			this(new LinkedHashMap<>(), new LinkedHashMap<>());
		}
		
		Map<String, String> ensureUUIDUsernames()
		{
			return this.uuidUsernames != null ? this.uuidUsernames : Map.of();
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
