package net.litetex.authback.common.gameprofile;

import static net.litetex.authback.shared.collections.AdvancedCollectors.toLinkedHashMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Suppliers;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.client.ObjectMapper;

import net.litetex.authback.shared.json.JSONSerializer;


public class GameProfileCacheManager
{
	private static final Logger LOG = LoggerFactory.getLogger(GameProfileCacheManager.class);
	
	private static final Duration DELETE_AFTER_EXECUTION_INTERVAL = Duration.ofHours(12);
	private static final float TARGET_PROFILE_COUNT_PERCENT = 0.8f;
	
	private final ObjectMapper objectMapper = ObjectMapper.create();
	
	private final Path file;
	
	private final Duration deleteAfter;
	private Instant nextDeletedAfterExecuteTime = Instant.now().plus(DELETE_AFTER_EXECUTION_INTERVAL);
	
	private final int maxTargetedProfileCount;
	private final int targetedProfileCount;
	
	private Map<String, UUID> usernameUuids = new HashMap<>();
	private Map<UUID, String> uuidUsernames = new HashMap<>(); // Reverse map for tracking when deleting
	private Map<UUID, ProfileContainer> uuidProfileContainers = new HashMap<>();
	
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
		this.uuidProfileContainers.put(
			profile.id(),
			new ProfileContainer(
				this.objectMapper.writeValueAsString(profile),
				() -> profile,
				Instant.now()));
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
		final ProfileContainer container = this.uuidProfileContainers.get(id);
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
			this.remove(id); // Remove corrupted container
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
		if(this.nextDeletedAfterExecuteTime.isBefore(now)
			|| this.uuidProfileContainers.size() > this.maxTargetedProfileCount)
		{
			LOG.debug("Executing cleanup");
			this.nextDeletedAfterExecuteTime = now.plus(DELETE_AFTER_EXECUTION_INTERVAL);
			
			final Instant deleteBefore = now.minus(this.deleteAfter);
			
			this.removeAll(this.uuidProfileContainers.entrySet()
				.stream()
				.filter(e -> e.getValue().createdAt().isBefore(deleteBefore)));
			
			if(this.uuidProfileContainers.size() > this.targetedProfileCount)
			{
				final var comparator =
					Comparator.<Map.Entry<UUID, ProfileContainer>, Instant>comparing(e -> e.getValue().createdAt())
						.reversed();
				this.removeAll(this.uuidProfileContainers.entrySet()
					.stream()
					.sorted(comparator)
					.skip(this.targetedProfileCount));
			}
		}
	}
	
	private void removeAll(final Stream<Map.Entry<UUID, ProfileContainer>> stream)
	{
		stream.map(Map.Entry::getKey)
			.toList() // Collect to prevent modification
			.forEach(this::remove);
	}
	
	private void remove(final UUID uuid)
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
		if(!Files.exists(this.file))
		{
			return;
		}
		
		final long startMs = System.currentTimeMillis();
		try
		{
			final Instant deleteBefore = Instant.now().minus(this.deleteAfter);
			
			final PersistentState persistentState =
				JSONSerializer.GSON.fromJson(Files.readString(this.file), PersistentState.class);
			this.uuidProfileContainers = Collections.synchronizedMap(persistentState.ensureIdProfiles()
				.entrySet()
				.stream()
				.filter(e -> e.getValue().createdAt().isAfter(deleteBefore))
				.collect(toLinkedHashMap(
					e -> UUID.fromString(e.getKey()),
					e -> new ProfileContainer(
						e.getValue().serializedGameProfile(),
						Suppliers.memoize(() -> this.objectMapper.readValue(
							e.getValue().serializedGameProfile(),
							GameProfile.class)),
						e.getValue().createdAt()
					))));
			
			this.usernameUuids = Collections.synchronizedMap(persistentState.ensureUsernameUUIDs()
				.entrySet()
				.stream()
				.map(e -> Map.entry(e.getKey(), UUID.fromString(e.getValue())))
				.filter(e -> this.uuidProfileContainers.containsKey(e.getValue()))
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
		final long startMs = System.currentTimeMillis();
		
		this.cleanUpIfRequired();
		
		try
		{
			final PersistentState persistentState = new PersistentState(
				this.usernameUuids.entrySet()
					.stream()
					.collect(toLinkedHashMap(
						Map.Entry::getKey,
						e -> e.getValue().toString()
					)),
				this.uuidProfileContainers.entrySet()
					.stream()
					.collect(toLinkedHashMap(
						e -> e.getKey().toString(),
						e -> e.getValue().persist()
					)));
			
			Files.writeString(this.file, JSONSerializer.GSON.toJson(persistentState));
			LOG.debug(
				"Took {}ms to write {}x profiles",
				System.currentTimeMillis() - startMs,
				this.uuidProfileContainers.size());
		}
		catch(final Exception ex)
		{
			LOG.warn("Failed to write file['{}']", this.file, ex);
		}
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
