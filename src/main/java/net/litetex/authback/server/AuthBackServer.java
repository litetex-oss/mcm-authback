package net.litetex.authback.server;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.litetex.authback.common.AuthBackCommon;
import net.litetex.authback.common.gameprofile.GameProfileCacheManager;
import net.litetex.authback.server.command.AuthbackCommand;
import net.litetex.authback.server.config.AuthBackServerConfig;
import net.litetex.authback.server.fallbackauth.FallbackAuthRateLimiter;
import net.litetex.authback.server.fallbackauth.FallbackUserAuthenticationAdapter;
import net.litetex.authback.server.keys.ServerProfilePublicKeysManager;
import net.litetex.authback.server.network.AuthBackServerNetworking;
import net.litetex.authback.shared.AuthBack;
import net.litetex.authback.shared.external.com.google.common.base.Suppliers;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;


public class AuthBackServer extends AuthBack
{
	private static final Logger LOG = LoggerFactory.getLogger(AuthBackServer.class);
	
	private static AuthBackServer instance;
	
	public static AuthBackServer instance()
	{
		return instance;
	}
	
	public static void setInstance(final AuthBackServer instance)
	{
		AuthBackServer.instance = instance;
	}
	
	private final Supplier<ServerProfilePublicKeysManager> serverProfilePublicKeysManagerSupplier;
	private final Supplier<GameProfileCacheManager> gameProfileCacheManagerSupplier;
	private final FallbackUserAuthenticationAdapter fallbackUserAuthenticationAdapter;
	
	// Temporarily marks connections that should not execute an up-to-date check
	// This is the case when a connection did log in using fallback auth
	private final Set<Connection> connectionsToSkipUpToDateCheck;
	
	private final AuthBackServerConfig config;
	
	@SuppressWarnings("checkstyle:MagicNumber")
	public AuthBackServer()
	{
		super("server");
		
		this.connectionsToSkipUpToDateCheck =
			Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
		
		final CompletableFuture<ServerProfilePublicKeysManager> cfServerProfilePublicKeysManager =
			CompletableFuture.supplyAsync(() -> new ServerProfilePublicKeysManager(
				this.authbackDir.resolve("profiles-public-keys.json"),
				this.lowLevelConfig.getInteger("keys.max-keys-per-player", 3),
				// When a player changes their username the name will be unavailable for 37 days
				Duration.ofDays(this.lowLevelConfig.getInteger("keys.delete-after-unused-days", 36))
			));
		this.serverProfilePublicKeysManagerSupplier = Suppliers.memoize(cfServerProfilePublicKeysManager::join);
		this.gameProfileCacheManagerSupplier = AuthBackCommon.instance().gameProfileCacheManagerSupplier();
		this.fallbackUserAuthenticationAdapter = new FallbackUserAuthenticationAdapter(
			this.serverProfilePublicKeysManagerSupplier,
			this.gameProfileCacheManagerSupplier,
			FallbackAuthRateLimiter.create(this.lowLevelConfig)
		);
		
		this.config = new AuthBackServerConfig(this.lowLevelConfig);
		
		new AuthBackServerNetworking(
			this.connectionsToSkipUpToDateCheck,
			this.serverProfilePublicKeysManagerSupplier);
		
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
			new AuthbackCommand(
				this.serverProfilePublicKeysManagerSupplier,
				this.gameProfileCacheManagerSupplier)
				.register(dispatcher));
		
		LOG.debug("Initialized");
	}
	
	public void handleJoinSuccess(final GameProfile profile)
	{
		this.gameProfileCacheManagerSupplier.get().add(profile);
	}
	
	public void doFallbackAuth(
		final ServerLoginPacketListenerImpl loginPacketListener,
		final Runnable defaultAction,
		final Consumer<String> customDisconnectAction,
		final Consumer<GameProfile> successAction)
	{
		this.fallbackUserAuthenticationAdapter.doFallbackAuth(
			loginPacketListener,
			defaultAction,
			customDisconnectAction,
			profile -> {
				this.connectionsToSkipUpToDateCheck.add(loginPacketListener.connection);
				successAction.accept(profile);
			});
	}
	
	public AuthBackServerConfig config()
	{
		return this.config;
	}
}
