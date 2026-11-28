package net.litetex.authback.common;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.exceptions.MinecraftClientException;
import com.mojang.authlib.minecraft.client.MinecraftClient;

import net.litetex.authback.shared.external.com.google.common.base.Suppliers;
import net.litetex.authback.shared.io.Persister;
import net.litetex.authback.shared.json.JSONSerializer;


// For type safety/confusion reasons this class should be extended into subclasses
public abstract class SingleHTTPRequestCache
{
	private final Logger logger;
	private final Path cacheFile;
	
	private final Optional<Duration> optDefaultReuseDuration;
	
	public SingleHTTPRequestCache(final Path cacheFile, final int defaultReuseMinutes)
	{
		this.logger = LoggerFactory.getLogger(this.getClass());
		this.cacheFile = cacheFile;
		this.optDefaultReuseDuration = defaultReuseMinutes > 0
			? Optional.of(Duration.ofMinutes(defaultReuseMinutes))
			: Optional.empty();
	}
	
	public Optional<Duration> optDefaultReuseDuration()
	{
		return this.optDefaultReuseDuration;
	}
	
	public void saveAsync(final URL url, final Object response)
	{
		CompletableFuture.runAsync(() -> this.save(url, response));
	}
	
	private synchronized void save(final URL url, final Object response)
	{
		Persister.trySave(
			this.logger,
			this.cacheFile,
			() -> new PersistentContainer(
				url.toString(),
				Instant.now(),
				JSONSerializer.FAST_OBJECT_MAPPER.writeValueAsString(response)));
	}
	
	public <T> Optional<CachedResponse<T>> read(final URL url, final Class<T> responseClass)
	{
		return Persister.tryRead(this.logger, this.cacheFile, PersistentContainer.class)
			// Validate
			.filter(persistentContainer -> url.toString().equals(persistentContainer.url())
				&& persistentContainer.createdAt() != null
				&& persistentContainer.createdAt().isBefore(Instant.now())
				&& persistentContainer.response() != null)
			.map(persistentContainer -> new CachedResponse<>(
				persistentContainer.createdAt(),
				Suppliers.memoize(() ->
				{
					final long startMs2 = System.currentTimeMillis();
					try
					{
						return JSONSerializer.FAST_OBJECT_MAPPER.readValue(
							persistentContainer.response(),
							responseClass);
					}
					catch(final Exception e2)
					{
						this.logger.warn("Failed to cached response", e2);
						return null;
					}
					finally
					{
						this.logger.debug("Took {}ms to deserialize response", System.currentTimeMillis() - startMs2);
					}
				})
			));
	}
	
	public <T> T handleGetCall(
		final Logger mixinLogger,
		final MinecraftClient client,
		final URL url,
		final Class<T> responseClass,
		final Operation<T> original)
	{
		// This tries to reuse the cached response when
		// 1. the upstream server is down or an error was encountered
		// 2. the response were recently fetched
		
		final UnaryOperator<T> logUseCachedResponseFunc = r -> {
			mixinLogger.info("Using cached response");
			return r;
		};
		try
		{
			final Optional<CachedResponse<T>> optCachedResponse = this.read(url, responseClass);
			
			return optCachedResponse
				.filter(r -> this.optDefaultReuseDuration()
					.map(Instant.now()::minus)
					.map(r.createdAt()::isAfter)
					.orElse(false))
				.flatMap(CachedResponse::response)
				.map(logUseCachedResponseFunc)
				.orElseGet(() ->
				{
					try
					{
						final long startMs = System.currentTimeMillis();
						final T response = original.call(client, url, responseClass);
						mixinLogger.info(
							"Took {}ms to get response for {}",
							System.currentTimeMillis() - startMs,
							url.toString());
						
						if(response == null)
						{
							mixinLogger.warn("Got empty response from server");
							
							return optCachedResponse
								.flatMap(CachedResponse::response)
								.map(logUseCachedResponseFunc)
								.orElse(null);
						}
						
						this.saveAsync(url, response);
						return response;
					}
					catch(final MinecraftClientException ex)
					{
						mixinLogger.warn("Request failed - Will try to use fallback", ex);
						
						return optCachedResponse
							.flatMap(CachedResponse::response)
							.map(logUseCachedResponseFunc)
							.orElseThrow(() -> ex);
					}
				});
		}
		catch(final Exception ex)
		{
			mixinLogger.error("Encountered general problem", ex);
			throw ex;
		}
	}
	
	public record CachedResponse<T>(
		Instant createdAt,
		Supplier<T> reponseSupplier)
	{
		public Optional<T> response()
		{
			return Optional.ofNullable(this.reponseSupplier().get());
		}
	}
	
	
	record PersistentContainer(
		String url,
		Instant createdAt,
		String response)
	{
	}
}
