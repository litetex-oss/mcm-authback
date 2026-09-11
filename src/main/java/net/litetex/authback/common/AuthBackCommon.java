package net.litetex.authback.common;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.GameProfileRepository;

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
	
	private final GlobalPublicKeysCache globalPublicKeysCache;
	private final CompletableFuture<GameProfileCacheManager> cfGameProfileCacheManager;
	private final Supplier<GameProfileCacheManager> gameProfileCacheManagerSupplier;
	
	private final AuthBackCommonConfig config;
	
	@SuppressWarnings("checkstyle:MagicNumber")
	public AuthBackCommon(final String envType)
	{
		super(envType);
		
		this.globalPublicKeysCache = new GlobalPublicKeysCache(
			this.authbackDir.resolve("global-public-keys.json"),
			this.lowLevelConfig.getInteger("global-public-keys-cache.default-reuse-minutes", 120));
		
		this.cfGameProfileCacheManager = CompletableFuture.supplyAsync(() -> new GameProfileCacheManager(
			this.authbackDir.resolve("game-profiles.json"),
			// When a player changes their username the name will be unavailable for 37 days
			Duration.ofDays(this.lowLevelConfig.getInteger("game-profiles.delete-after-days", 36)),
			this.lowLevelConfig.getInteger("game-profiles.max-cache-size", 250)));
		this.gameProfileCacheManagerSupplier = Suppliers.memoize(this.cfGameProfileCacheManager::join);
		
		this.config = new AuthBackCommonConfig(this.lowLevelConfig);
		
		LOG.debug("Initialized");
	}
	
	public GlobalPublicKeysCache publicKeysCache()
	{
		return this.globalPublicKeysCache;
	}
	
	@SuppressWarnings("checkstyle:MagicNumber")
	public UserNameToIdResolver createUserNameToIdResolver(
		final GameProfileRepository gameProfileRepository,
		final File gameDir)
	{
		final String configPrefix = "username-to-id-resolver.";
		
		final String cacheFile = "usercache.json";
		
		if(this.lowLevelConfig.getBoolean(configPrefix + "use-vanilla", false))
		{
			return new CachedUserNameToIdResolver(gameProfileRepository, new File(gameDir, cacheFile));
		}
		
		return new AuthbackCachedUserNameToIdResolver(
			gameProfileRepository,
			gameDir.toPath().resolve(this.lowLevelConfig.getString("cache-file", cacheFile)),
			this.cfGameProfileCacheManager,
			this.gameProfileCacheManagerSupplier,
			// When a player changes their username the name will be unavailable for 37 days
			// The default of the original is 1 month
			Duration.ofDays(this.lowLevelConfig.getInteger(configPrefix + "expire-after-days", 36)),
			Duration.ofDays(this.lowLevelConfig.getInteger(configPrefix + "refresh-before-expire-days", 14)),
			this.lowLevelConfig.getInteger(configPrefix + "max-cache-size", 1000),
			// true is the default
			// servers might disable this via resolveOfflineUsers when online-mode=true (default)
			this.lowLevelConfig.getBoolean(configPrefix + "resolve-offline-users-by-default", true),
			// Tries to save/update name and id when the game profile is fetched
			this.lowLevelConfig.getBoolean(configPrefix + "update-on-game-profile-fetch", true),
			// Uses GameProfileCacheManager as secondary cache when all primary caches fail
			// Usage should be extremely rare but can happen if usercache was e.g. corrupted
			this.lowLevelConfig.getBoolean(configPrefix + "use-game-profile-cache", true)
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
