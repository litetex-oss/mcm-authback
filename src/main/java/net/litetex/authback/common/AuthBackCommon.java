package net.litetex.authback.common;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.litetex.authback.common.gameprofile.GameProfileCacheManager;
import net.litetex.authback.shared.AuthBack;


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
	private final GameProfileCacheManager gameProfileCacheManager;
	
	private final boolean skipExtractProfileActionTypes;
	
	@SuppressWarnings("checkstyle:MagicNumber")
	public AuthBackCommon(final String envType)
	{
		super(envType);
		
		this.globalPublicKeysCache = new GlobalPublicKeysCache(
			this.authbackDir.resolve("global-public-keys.json"),
			this.config.getInteger("global-public-keys-cache.default-reuse-minutes", 120));
		
		this.gameProfileCacheManager = new GameProfileCacheManager(
			this.authbackDir.resolve("game-profiles.json"),
			// When a player changes their username the name will be unavailable for 37 days
			Duration.ofDays(this.config.getInteger("game-profiles.delete-after-days", 36)));
		
		this.skipExtractProfileActionTypes = this.config.getBoolean("skip-extract-profile-action-types", false);
		
		LOG.debug("Initialized");
	}
	
	public GlobalPublicKeysCache publicKeysCache()
	{
		return this.globalPublicKeysCache;
	}
	
	public GameProfileCacheManager gameProfileCacheManager()
	{
		return this.gameProfileCacheManager;
	}
	
	public boolean isSkipExtractProfileActionTypes()
	{
		return this.skipExtractProfileActionTypes;
	}
}
