package net.litetex.authback.mixin.common;

import java.net.URL;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.authlib.exceptions.MinecraftClientException;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo;

import net.litetex.authback.common.AuthBackCommon;
import net.litetex.authback.common.GlobalPublicKeysCache;
import net.litetex.authback.shared.mixin.log.MixinLogger;


@Mixin(value = YggdrasilServicesKeyInfo.class, remap = false)
public abstract class YggdrasilServicesKeyInfoMixin
{
	@Unique
	private static final Logger LOG = MixinLogger.common("YggdrasilServicesKeyInfoMixin");
	
	@Redirect(
		method = "fetch",
		at = @At(value = "INVOKE",
			target = "Lcom/mojang/authlib/minecraft/client/MinecraftClient;get(Ljava/net/URL;"
				+ "Ljava/lang/Class;)Ljava/lang/Object;",
			remap = false),
		remap = false)
	private static <T> T get(
		final MinecraftClient client,
		final URL url,
		final Class<T> responseClass)
	{
		// This tries to reuse the cache public keys when
		// 1. the upstream server is down or an error was encountered
		// 2. the keys were recently fetched
		try
		{
			final GlobalPublicKeysCache globalPublicKeysCache = AuthBackCommon.instance().publicKeysCache();
			final Optional<GlobalPublicKeysCache.CachedResponse<T>> optCachedResponse =
				globalPublicKeysCache.read(url, responseClass);
			
			return optCachedResponse
				.filter(r -> globalPublicKeysCache.optDefaultReuseDuration()
					.map(Instant.now()::minus)
					.map(r.createdAt()::isAfter)
					.orElse(false))
				.flatMap(GlobalPublicKeysCache.CachedResponse::response)
				.map(YggdrasilServicesKeyInfoMixin::logUseCachedResponse)
				.orElseGet(() ->
				{
					try
					{
						final long startMs = System.currentTimeMillis();
						final T response = client.get(url, responseClass);
						LOG.info(
							"Took {}ms to get response for {}",
							System.currentTimeMillis() - startMs,
							url.toString());
						
						if(response == null)
						{
							LOG.warn("Got empty response from server");
							
							return optCachedResponse
								.flatMap(GlobalPublicKeysCache.CachedResponse::response)
								.map(YggdrasilServicesKeyInfoMixin::logUseCachedResponse)
								.orElse(null);
						}
						
						globalPublicKeysCache.saveAsync(url, response);
						return response;
					}
					catch(final MinecraftClientException ex)
					{
						LOG.warn("Failed to get public key from servers", ex);
						
						return optCachedResponse
							.flatMap(GlobalPublicKeysCache.CachedResponse::response)
							.map(YggdrasilServicesKeyInfoMixin::logUseCachedResponse)
							.orElseThrow(() -> ex);
					}
				});
		}
		catch(final Exception ex)
		{
			LOG.error("Encountered general problem", ex);
			throw ex;
		}
	}
	
	@Unique
	private static <T> T logUseCachedResponse(final T value)
	{
		LOG.info("Using cached response");
		return value;
	}
}
