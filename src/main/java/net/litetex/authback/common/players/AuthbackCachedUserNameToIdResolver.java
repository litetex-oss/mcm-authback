package net.litetex.authback.common.players;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;

import net.litetex.authback.common.gameprofile.GameProfileCacheManager;
import net.litetex.authback.shared.collections.MaxSizedLinkedHashMap;
import net.litetex.authback.shared.io.Persister;
import net.litetex.authback.shared.sync.SynchronizedContainer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserNameToIdResolver;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;


/**
 * A better replacement for {@link net.minecraft.server.players.CachedUserNameToIdResolver}.
 * <p>
 * Compared to the original it has the following improvements:
 * <ul>
 *     <li>If the users UUID can't be resolved the user is NOT treated as offline.
 *     No incorrect UUIDs (v3 not v4) will therefore be stored in the file.</li>
 *     <li>The file is saved async</li>
 *     <li>On access player information will be refreshed in the background BEFORE it expires.
 *     Situations where no information is available should therefore occur almost never.</li>
 *     <li>Removed legacy code before Java 8 code (like Date)</li>
 *     <li>Collections are kept with order and don't require sorting before each save</li>
 *     <li>Optimized serializer</li>
 *     <li>Cache-File is pretty printed and human-readable</li>
 *     <li>Information from fetched GameProfiles will be re-used if applicable</li>
 *     <li>{@link GameProfileCacheManager} is used as a secondary cache</li>
 * </ul>
 */
public class AuthbackCachedUserNameToIdResolver implements UserNameToIdResolver
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthbackCachedUserNameToIdResolver.class);
	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapter(OffsetDateTime.class, new CacheOffsetDateTimeConverter())
		.setPrettyPrinting()
		.create();
	
	private static final Duration CLEANUP_EXECUTION_INTERVAL = Duration.ofHours(12);
	private static final float TARGET_PROFILE_COUNT_PERCENT = 0.9f;
	
	private final int maxTargetedProfileCount;
	private final int targetedProfileCount;
	
	private final Duration expiresAfter;
	private final Duration refreshBeforeExpire;
	private Instant nextCleanUpTime = Instant.MIN;
	
	private final GameProfileRepository gameProfileRepository;
	private final Path file;
	
	// Secondary Cache
	private final Optional<Supplier<GameProfileCacheManager>> optGameProfileCacheManagerSupplier;
	
	// Primary
	private final SynchronizedContainer<LinkedHashMap<UUID, GameProfileInfo>> uuidProfilesSC =
		new SynchronizedContainer<>(new LinkedHashMap<>());
	private final SynchronizedContainer<Map<String, GameProfileInfo>> nameProfilesSC =
		new SynchronizedContainer<>(new HashMap<>());
	private final Set<UUID> uuidsRequiringRefresh = Collections.newSetFromMap(new ConcurrentHashMap<>());
	
	private Optional<OfflineProfiles> optOfflineProfiles = Optional.empty();
	
	@SuppressWarnings("PMD.ExcessiveParameterList")
	public AuthbackCachedUserNameToIdResolver(
		final GameProfileRepository gameProfileRepository,
		final Path file,
		final CompletableFuture<GameProfileCacheManager> cfGameProfileCacheManager,
		final Supplier<GameProfileCacheManager> gameProfileCacheManagerSupplier,
		final Duration expiresAfter,
		final Duration refreshBeforeExpire,
		final int maxTargetedProfileCount,
		final boolean resolveOfflineUsers,
		final boolean updateOnGameProfileFetch,
		final boolean useGameProfileCache)
	{
		this.gameProfileRepository = gameProfileRepository;
		this.file = file;
		
		this.expiresAfter = expiresAfter;
		this.refreshBeforeExpire = refreshBeforeExpire;
		
		if(maxTargetedProfileCount <= 0)
		{
			throw new IllegalArgumentException("maxTargetedProfileCount needs to be > 1");
		}
		this.maxTargetedProfileCount = maxTargetedProfileCount;
		this.targetedProfileCount = Math.max(Math.round(maxTargetedProfileCount * TARGET_PROFILE_COUNT_PERCENT), 1);
		
		this.initReadFileAsync();
		this.resolveOfflineUsers(resolveOfflineUsers);
		
		if(updateOnGameProfileFetch)
		{
			cfGameProfileCacheManager.thenAcceptAsync(manager ->
				manager.registerOnAddedProfileAsyncHandlers(this, this::addIfRequired));
		}
		
		this.optGameProfileCacheManagerSupplier = useGameProfileCache
			? Optional.of(gameProfileCacheManagerSupplier)
			: Optional.empty();
	}
	
	@Override
	public void add(final NameAndId nameAndId)
	{
		LOG.debug("Add {}", nameAndId);
		
		final long startMs = System.currentTimeMillis();
		try
		{
			// Check if offline mode is enabled and the player is offline
			final boolean isOfflinePlayer = nameAndId.id().version() == 3;
			final String name = nameAndId.name().toLowerCase(Locale.ROOT);
			if(isOfflinePlayer)
			{
				this.optOfflineProfiles.ifPresentOrElse(op -> {
						op.uuidProfilesSC().execWithLock(m -> m.put(nameAndId.id(), nameAndId));
						op.nameProfilesSC().execWithLock(m -> m.put(name, nameAndId));
					},
					() -> LOG.warn(
						"Encountered an offline NameAndId[{}] but cache is not configured for this. Ignoring",
						nameAndId));
				return;
			}
			
			this.uuidsRequiringRefresh.remove(nameAndId.id());
			
			final GameProfileInfo gpi = new GameProfileInfo(nameAndId, OffsetDateTime.now().plus(this.expiresAfter));
			this.uuidProfilesSC.execWithLock(m -> m.putFirst(nameAndId.id(), gpi));
			this.nameProfilesSC.execWithLock(m -> m.put(name, gpi));
			
			this.saveAsync();
		}
		finally
		{
			LOG.debug("Add {} took {}ms", nameAndId, System.currentTimeMillis() - startMs);
		}
	}
	
	private void addIfRequired(final GameProfile gameProfile)
	{
		final UUID id = gameProfile.id();
		LOG.debug("AddIfRequired {}/{}", gameProfile.name(), id);
		if(this.uuidsRequiringRefresh.contains(id)
			|| this.uuidProfilesSC.supplyWithLock(m -> !m.containsKey(id)))
		{
			this.add(new NameAndId(gameProfile));
		}
	}
	
	private NameAndId addToCache(final NameAndId nameAndId)
	{
		this.add(nameAndId);
		return nameAndId;
	}
	
	/**
	 * @apiNote Does NOT execute a lookup (see original)
	 */
	@Override
	public Optional<NameAndId> get(final UUID uuid)
	{
		LOG.debug("Get(uuid) {}", uuid);
		final long startMs = System.currentTimeMillis();
		try
		{
			return this.getFromCache(
				this.uuidProfilesSC,
				this.optOfflineProfiles.map(OfflineProfiles::uuidProfilesSC),
				GameProfileCacheManager::findByUUID,
				uuid);
		}
		finally
		{
			LOG.debug("Get(uuid) {} took {}ms", uuid, System.currentTimeMillis() - startMs);
		}
	}
	
	@Override
	public Optional<NameAndId> get(final String name)
	{
		LOG.debug("Get(name) {}", name);
		final long startMs = System.currentTimeMillis();
		try
		{
			// Sanity check
			if(name.isEmpty())
			{
				return Optional.empty();
			}
			
			// 1. Try to read from cache
			return this.getFromCache(
					this.nameProfilesSC,
					gpi -> {
						this.refreshInBackgroundIfRequired(gpi);
						return gpi.toNameAndId();
					},
					this.optOfflineProfiles.map(OfflineProfiles::nameProfilesSC),
					GameProfileCacheManager::findByName,
					name.toLowerCase(Locale.ROOT), // Case-insensitive
					name)
				// 2. Try to look up
				.or(() -> this.performLookup(name)
					// 3. If not resolved and in "resolve offline users"-mode -> Create
					.or(() -> this.optOfflineProfiles.map(ignored -> NameAndId.createOffline(name)))
					// 2B. Write to cache if present
					.map(this::addToCache));
		}
		finally
		{
			LOG.debug("Get(name) {} took {}ms", name, System.currentTimeMillis() - startMs);
		}
	}
	
	private void refreshInBackgroundIfRequired(final GameProfileInfo gpi)
	{
		if(!this.uuidsRequiringRefresh.remove(gpi.uuid()))
		{
			return;
		}
		
		CompletableFuture.runAsync(
			() -> {
				try
				{
					this.performLookup(gpi.name()).ifPresent(this::addToCache);
				}
				catch(final Exception ex)
				{
					LOG.warn("Failed to refresh GameProfileInfo[{}] in background", gpi, ex);
				}
			}, Util.nonCriticalIoPool());
	}
	
	private Optional<NameAndId> performLookup(final String name)
	{
		final long startMs = System.currentTimeMillis();
		final Optional<NameAndId> nameAndId = this.gameProfileRepository.findProfileByName(name)
			.map(NameAndId::new);
		LOG.debug(
			"Player '{}' has UUID {}, took {}ms",
			name,
			nameAndId.map(NameAndId::id).orElse(null),
			System.currentTimeMillis() - startMs);
		
		return nameAndId;
	}
	
	private <K> Optional<NameAndId> getFromCache(
		final SynchronizedContainer<? extends Map<K, GameProfileInfo>> onlineSC,
		final Optional<SynchronizedContainer<LinkedHashMap<K, NameAndId>>> optOfflineSC,
		final BiFunction<GameProfileCacheManager, K, GameProfile> secondaryCacheAccessor,
		final K key)
	{
		return this.getFromCache(
			onlineSC,
			GameProfileInfo::toNameAndId,
			optOfflineSC,
			secondaryCacheAccessor,
			key,
			key);
	}
	
	private <K> Optional<NameAndId> getFromCache(
		final SynchronizedContainer<? extends Map<K, GameProfileInfo>> onlineSC,
		final Function<GameProfileInfo, NameAndId> onlineGPIToNameAndId,
		final Optional<SynchronizedContainer<LinkedHashMap<K, NameAndId>>> optOfflineSC,
		final BiFunction<GameProfileCacheManager, K, GameProfile> secondaryCacheAccessor,
		final K primaryCacheKey,
		final K secondaryCacheKey)
	{
		this.cleanUpIfRequired();
		
		// 1. Check normal "online" players cache
		return Optional.ofNullable(onlineSC.supplyWithLock(m -> m.get(primaryCacheKey)))
			.map(onlineGPIToNameAndId)
			// 2. Check "offline" players cache if present
			.or(() -> optOfflineSC.map(sc -> sc.supplyWithLock(m -> m.get(primaryCacheKey))))
			// 3. Check secondary cache
			.or(() -> this.optGameProfileCacheManagerSupplier
				.map(Supplier::get)
				.map(c -> secondaryCacheAccessor.apply(c, secondaryCacheKey))
				.map(NameAndId::new));
	}
	
	// region Offline users
	@Override
	public void resolveOfflineUsers(final boolean resolveOffline)
	{
		final boolean isResolveOfflineUsers = this.optOfflineProfiles.isPresent();
		if(resolveOffline && !isResolveOfflineUsers)
		{
			LOG.debug("Enabling resolveOfflineUsers");
			this.optOfflineProfiles = Optional.of(new OfflineProfiles(this.maxTargetedProfileCount));
		}
		else if(!resolveOffline && isResolveOfflineUsers)
		{
			LOG.debug("Disabling resolveOfflineUsers");
			this.optOfflineProfiles = Optional.empty();
		}
	}
	
	record OfflineProfiles(
		SynchronizedContainer<LinkedHashMap<UUID, NameAndId>> uuidProfilesSC,
		SynchronizedContainer<LinkedHashMap<String, NameAndId>> nameProfilesSC
	)
	{
		public OfflineProfiles(final int maxTargetedProfileCount)
		{
			this(createContainer(maxTargetedProfileCount), createContainer(maxTargetedProfileCount));
		}
		
		private static @NonNull <K, V> SynchronizedContainer<LinkedHashMap<K, V>> createContainer(
			final int maxTargetedProfileCount
		)
		{
			return new SynchronizedContainer<>(new MaxSizedLinkedHashMap<>(maxTargetedProfileCount));
		}
	}
	
	// endregion
	// region Cleanup
	
	private void cleanUpIfRequired()
	{
		final Instant now = Instant.now();
		if(!(this.nextCleanUpTime.isBefore(now)
			|| this.uuidProfilesSC.value().size() > this.maxTargetedProfileCount))
		{
			return;
		}
		
		LOG.debug("Executing cleanup");
		this.nextCleanUpTime = now.plus(CLEANUP_EXECUTION_INTERVAL);
		
		final OffsetDateTime odtNow = OffsetDateTime.now();
		final OffsetDateTime odtRefreshTime = odtNow.plus(this.refreshBeforeExpire);
		
		final long startMs = System.currentTimeMillis();
		this.uuidsRequiringRefresh.clear();
		this.uuidProfilesSC.execWithLock(
			uuidProfiles -> {
				this.removeAllWithoutLock(findEntriesExpiredBefore(uuidProfiles, odtNow).stream());
				
				findEntriesExpiredBefore(uuidProfiles, odtRefreshTime).stream()
					.map(Map.Entry::getKey)
					.forEach(this.uuidsRequiringRefresh::add);
			});
		
		final long start2Ms = System.currentTimeMillis();
		LOG.debug("Cleanup with expiresOn took {}ms", start2Ms - startMs);
		
		if(this.uuidProfilesSC.value().size() > this.targetedProfileCount)
		{
			this.uuidProfilesSC.execWithLock(uuidProfileContainers ->
				this.removeAllWithoutLock(uuidProfileContainers.entrySet()
					.stream()
					// Skip instead of limit because we want to remove the end = oldest entries
					.skip(this.targetedProfileCount)));
			
			LOG.debug("Cleanup trim to targetedProfileCount took {}ms", System.currentTimeMillis() - start2Ms);
		}
	}
	
	private static @NonNull List<Map.Entry<UUID, GameProfileInfo>> findEntriesExpiredBefore(
		final LinkedHashMap<UUID, GameProfileInfo> uuidProfiles,
		final OffsetDateTime odtNow)
	{
		final List<Map.Entry<UUID, GameProfileInfo>> entriesToDelete = new ArrayList<>();
		// Reverse - Start with the oldest entry at the end!
		for(final Map.Entry<UUID, GameProfileInfo> entry : uuidProfiles.sequencedEntrySet().reversed())
		{
			// As the map is ordered: Abort everything else after the first entry that is valid
			if(!entry.getValue().expiresOn().isBefore(odtNow))
			{
				break;
			}
			entriesToDelete.add(entry);
		}
		return entriesToDelete;
	}
	
	private void removeAllWithoutLock(final Stream<Map.Entry<UUID, GameProfileInfo>> stream)
	{
		stream.map(Map.Entry::getKey)
			.toList() // Collect to prevent modification
			.forEach(this::removeWithoutLock);
	}
	
	private void removeWithoutLock(final UUID uuid)
	{
		final GameProfileInfo removed = this.uuidProfilesSC.value().remove(uuid);
		if(removed != null)
		{
			this.nameProfilesSC.execWithLock(m -> m.remove(removed.name()));
		}
	}
	
	// endregion
	// region Persistence
	
	private void initReadFileAsync()
	{
		CompletableFuture.runAsync(() -> {
			// Establish lock until read
			this.uuidProfilesSC.execWithLock(uuidProfilesRaw ->
				this.nameProfilesSC.execWithLock(nameProfilesRaw -> {
					this.initReadFile(uuidProfilesRaw, nameProfilesRaw);
				}));
			
			this.cleanUpIfRequired();
		});
	}
	
	private void initReadFile(
		final LinkedHashMap<UUID, GameProfileInfo> uuidProfiles,
		final Map<String, GameProfileInfo> nameProfiles)
	{
		uuidProfiles.clear();
		nameProfiles.clear();
		
		final OffsetDateTime maxExpireTime = OffsetDateTime.now()
			.plus(this.expiresAfter)
			// Buffer
			.plusDays(1);
		Persister.tryRead(LOG, this.file, GSON, GameProfileInfo[].class)
			.stream()
			.flatMap(Stream::of)
			.filter(Objects::nonNull)
			.filter(gpi -> {
				final boolean valid = gpi.isValid(maxExpireTime);
				if(!valid)
				{
					LOG.warn("Read invalid GameProfileInfo[{}] from file. Ignoring it", gpi);
				}
				return valid;
			})
			.forEach(gpi -> {
				uuidProfiles.putLast(gpi.uuid(), gpi);
				nameProfiles.put(gpi.name(), gpi);
			});
	}
	
	@Override
	public void save()
	{
		this.saveAsync();
	}
	
	private void saveAsync()
	{
		CompletableFuture.runAsync(this::saveToFile);
	}
	
	private synchronized void saveToFile()
	{
		final List<GameProfileInfo> gameProfileInfos =
			this.uuidProfilesSC.supplyWithLock(m -> new ArrayList<>(m.values()));
		
		LOG.debug("Saving {}x gameProfileInfos", gameProfileInfos.size());
		Persister.trySave(
			LOG,
			this.file,
			GSON,
			() -> gameProfileInfos
		);
	}
	
	record GameProfileInfo(
		UUID uuid,
		String name,
		OffsetDateTime expiresOn
	)
	{
		GameProfileInfo(final NameAndId nameAndId, final OffsetDateTime expiresOn)
		{
			this(nameAndId.id(), nameAndId.name(), expiresOn);
		}
		
		boolean isValid(final OffsetDateTime maxExpireTime)
		{
			return this.uuid() != null
				// UUIDv3 indicates a generated/offline UUID and should have never been persisted
				&& this.uuid().version() != 3
				&& this.name() != null
				&& !this.name().isEmpty()
				&& StringUtil.isValidPlayerName(this.name())
				&& this.expiresOn() != null
				&& this.expiresOn().isBefore(maxExpireTime);
		}
		
		NameAndId toNameAndId()
		{
			return new NameAndId(this.uuid(), this.name());
		}
	}
	
	
	static class CacheOffsetDateTimeConverter
		implements JsonSerializer<OffsetDateTime>, JsonDeserializer<OffsetDateTime>
	{
		static final DateTimeFormatter FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
		
		private final Map<OffsetDateTime, String> formatCache = new WeakHashMap<>();
		
		@Override
		public JsonElement serialize(
			final OffsetDateTime src,
			final Type typeOfSrc,
			final JsonSerializationContext context)
		{
			return new JsonPrimitive(this.formatCache.computeIfAbsent(src, FORMATTER::format));
		}
		
		@Override
		public OffsetDateTime deserialize(
			final JsonElement json, final Type typeOfT,
			final JsonDeserializationContext context)
		{
			return FORMATTER.parse(json.getAsString(), OffsetDateTime::from);
		}
	}
	
	// endregion
}
