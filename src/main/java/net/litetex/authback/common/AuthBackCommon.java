package net.litetex.authback.common;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.litetex.authback.common.config.AuthBackCommonConfig;
import net.litetex.authback.common.gameprofile.GameProfileCacheManager;
import net.litetex.authback.shared.AuthBack;
import net.litetex.authback.shared.external.com.google.common.base.Suppliers;


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
	private final Supplier<GameProfileCacheManager> gameProfileCacheManagerSupplier;
	
	private final AuthBackCommonConfig config;
	
	@SuppressWarnings("checkstyle:MagicNumber")
	public AuthBackCommon(final String envType)
	{
		super(envType);
		
		this.globalPublicKeysCache = new GlobalPublicKeysCache(
			this.authbackDir.resolve("global-public-keys.json"),
			this.lowLevelConfig.getInteger("global-public-keys-cache.default-reuse-minutes", 120));
		
		final CompletableFuture<GameProfileCacheManager> cfGameProfileCacheManager =
			CompletableFuture.supplyAsync(() -> new GameProfileCacheManager(
				this.authbackDir.resolve("game-profiles.json"),
				// When a player changes their username the name will be unavailable for 37 days
				Duration.ofDays(this.lowLevelConfig.getInteger("game-profiles.delete-after-days", 36)),
				this.lowLevelConfig.getInteger("game-profiles.max-cache-size", 250)));
		this.gameProfileCacheManagerSupplier = Suppliers.memoize(cfGameProfileCacheManager::join);
		
		this.config = new AuthBackCommonConfig(this.lowLevelConfig);
		
		LOG.debug("Initialized");
	}
	
	public GlobalPublicKeysCache publicKeysCache()
	{
		return this.globalPublicKeysCache;
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
