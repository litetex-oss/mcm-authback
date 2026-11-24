package net.litetex.authback.common;

import java.io.File;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.GameProfileRepository;

import net.litetex.authback.common.cache.DiscoveryCache;
import net.litetex.authback.common.cache.GlobalPublicKeysCache;
import net.litetex.authback.common.config.AuthBackCommonConfig;
import net.litetex.authback.common.gameprofile.GameProfileCacheManager;
import net.litetex.authback.common.players.AuthbackCachedUserNameToIdResolver;
import net.litetex.authback.shared.AuthBack;
import net.litetex.authback.shared.external.com.google.common.base.Suppliers;
import net.minecraft.server.players.CachedUserNameToIdResolver;
import net.minecraft.server.players.UserNameToIdResolver;


public class AuthBackCommon extends AuthBack
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackCommon.class);
	
	private static AuthBackCommon instance;
	
	public static AuthBackCommon instance()
	{
		return instance;
	}
	
	public static void setInstance(final AuthBackCommon instance)
	{
		AuthBackCommon.instance = instance;
	}
	
	private final DiscoveryCache discoveryCache;
	private final GlobalPublicKeysCache globalPublicKeysCache;
	private final CompletableFuture<GameProfileCacheManager> cfGameProfileCacheManager;
	private final Supplier<GameProfileCacheManager> gameProfileCacheManagerSupplier;
	
	private final Optional<Boolean> globalPublicKeysFetchingEnabled;
	private boolean globalPublicKeysFetchingEnabledAutoDetect = true;
	
	private final AuthBackCommonConfig config;
	
	@SuppressWarnings("checkstyle:MagicNumber")
	public AuthBackCommon(final String envType)
	{
		super(envType);
		
		this.discoveryCache = new DiscoveryCache(
			this.authbackDir.resolve("discovery.json"),
			this.lowLevelConfig.getInteger("discovery-cache.default-reuse-minutes").orElse(120));
		this.globalPublicKeysCache = new GlobalPublicKeysCache(
			this.authbackDir.resolve("global-public-keys.json"),
			this.lowLevelConfig.getInteger("global-public-keys-cache.default-reuse-minutes").orElse(120));
		
		this.cfGameProfileCacheManager = CompletableFuture.supplyAsync(() -> new GameProfileCacheManager(
			this.authbackDir.resolve("game-profiles.json"),
			// When a player changes their username the name will be unavailable for 37 days
			Duration.ofDays(this.lowLevelConfig.getInteger("game-profiles.delete-after-days").orElse(36)),
			this.lowLevelConfig.getInteger("game-profiles.max-cache-size").orElse(250)));
		this.gameProfileCacheManagerSupplier = Suppliers.memoize(this.cfGameProfileCacheManager::join);
		
		this.globalPublicKeysFetchingEnabled =
			this.lowLevelConfig.getBoolean("global-public-keys.fetching.enabled");
		
		this.config = new AuthBackCommonConfig(this.lowLevelConfig);
		
		LOG.debug("Initialized");
	}
	
	public DiscoveryCache discoveryCache()
	{
		return this.discoveryCache;
	}
	
	public GlobalPublicKeysCache publicKeysCache()
	{
		return this.globalPublicKeysCache;
	}
	
	public void globalPublicKeysFetchingEnabledAutoDetect(
		final Supplier<Boolean> valueSupplier)
	{
		if(this.globalPublicKeysFetchingEnabled.isEmpty())
		{
			final Boolean autoDetected = valueSupplier.get();
			LOG.debug(
				"Auto detected globalPublicKeysFetchingEnabled as {}",
				autoDetected);
			if(autoDetected != null)
			{
				this.globalPublicKeysFetchingEnabledAutoDetect = autoDetected;
			}
		}
	}
	
	public boolean globalPublicKeysFetchingEnabled()
	{
		return this.globalPublicKeysFetchingEnabled.orElse(this.globalPublicKeysFetchingEnabledAutoDetect);
	}
	
	@SuppressWarnings("checkstyle:MagicNumber")
	public UserNameToIdResolver createUserNameToIdResolver(
		final GameProfileRepository gameProfileRepository,
		final File gameDir)
	{
		final String configPrefix = "username-to-id-resolver.";
		
		final String cacheFile = "usercache.json";
		
		if(this.lowLevelConfig.getBoolean(configPrefix + "use-vanilla").orElse(false))
		{
			return new CachedUserNameToIdResolver(gameProfileRepository, new File(gameDir, cacheFile));
		}
		
		return new AuthbackCachedUserNameToIdResolver(
			gameProfileRepository,
			gameDir.toPath().resolve(this.lowLevelConfig.getString("cache-file").orElse(cacheFile)),
			this.cfGameProfileCacheManager,
			this.gameProfileCacheManagerSupplier,
			// When a player changes their username the name will be unavailable for 37 days
			// The default of the original is 1 month
			Duration.ofDays(this.lowLevelConfig.getInteger(configPrefix + "expire-after-days").orElse(36)),
			Duration.ofDays(this.lowLevelConfig.getInteger(configPrefix + "refresh-before-expire-days").orElse(14)),
			this.lowLevelConfig.getInteger(configPrefix + "max-cache-size").orElse(1000),
			// true is the default
			// servers might disable this via resolveOfflineUsers when online-mode=true (default)
			this.lowLevelConfig.getBoolean(configPrefix + "resolve-offline-users-by-default").orElse(true),
			// Tries to save/update name and id when the game profile is fetched
			this.lowLevelConfig.getBoolean(configPrefix + "update-on-game-profile-fetch").orElse(true),
			// Uses GameProfileCacheManager as secondary cache when all primary caches fail
			// Usage should be extremely rare but can happen if usercache was e.g. corrupted
			this.lowLevelConfig.getBoolean(configPrefix + "use-game-profile-cache").orElse(true)
		);
	}
	
	public Supplier<GameProfileCacheManager> gameProfileCacheManagerSupplier()
	{
		return this.gameProfileCacheManagerSupplier;
	}
	
	public AuthBackCommonConfig config()
	{
		return this.config;
	}
}
