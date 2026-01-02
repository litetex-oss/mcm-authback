package net.litetex.authback.common;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.litetex.authback.shared.external.com.google.common.base.Suppliers;
import net.litetex.authback.shared.io.Persister;
import net.litetex.authback.shared.json.JSONSerializer;


public class GlobalPublicKeysCache
{
	private static final Logger LOG = LoggerFactory.getLogger(GlobalPublicKeysCache.class);
	
	private final Path cacheFile;
	
	private final Optional<Duration> optDefaultReuseDuration;
	
	public GlobalPublicKeysCache(final Path cacheFile, final int defaultReuseMinutes)
	{
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
			LOG,
			this.cacheFile,
			() -> new PersistentContainer(
				url.toString(),
				Instant.now(),
				JSONSerializer.FAST_OBJECT_MAPPER.writeValueAsString(response)));
	}
	
	public <T> Optional<CachedResponse<T>> read(final URL url, final Class<T> responseClass)
	{
		return Persister.tryRead(LOG, this.cacheFile, PersistentContainer.class)
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
						LOG.warn("Failed to cached response", e2);
						return null;
					}
					finally
					{
						LOG.debug("Took {}ms to deserialize response", System.currentTimeMillis() - startMs2);
					}
				})
			));
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
