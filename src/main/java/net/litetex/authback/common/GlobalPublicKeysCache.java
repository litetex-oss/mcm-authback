package net.litetex.authback.common;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.minecraft.client.ObjectMapper;

import net.litetex.authback.shared.external.com.google.common.base.Suppliers;
import net.litetex.authback.shared.json.JSONSerializer;


public class GlobalPublicKeysCache
{
	private static final Logger LOG = LoggerFactory.getLogger(GlobalPublicKeysCache.class);
	
	private final ObjectMapper objectMapper = ObjectMapper.create();
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
		try
		{
			Files.writeString(
				this.cacheFile,
				JSONSerializer.GSON.toJson(new PersistentContainer(
					url.toString(),
					Instant.now(),
					this.objectMapper.writeValueAsString(response))));
		}
		catch(final Exception e)
		{
			LOG.warn("Failed to save {}", this.cacheFile, e);
		}
	}
	
	public <T> Optional<CachedResponse<T>> read(final URL url, final Class<T> responseClass)
	{
		if(!Files.exists(this.cacheFile))
		{
			return Optional.empty();
		}
		
		final long startMs = System.currentTimeMillis();
		try
		{
			
			final PersistentContainer persistentContainer = JSONSerializer.GSON.fromJson(
				Files.readString(this.cacheFile),
				PersistentContainer.class);
			if(!url.toString().equals(persistentContainer.url())
				|| persistentContainer.createdAt() == null
				|| persistentContainer.createdAt().isAfter(Instant.now())
				|| persistentContainer.response() == null)
			{
				return Optional.empty();
			}
			
			return Optional.of(
				new CachedResponse<>(
					persistentContainer.createdAt(),
					Suppliers.memoize(() ->
					{
						final long startMs2 = System.currentTimeMillis();
						try
						{
							return this.objectMapper.readValue(persistentContainer.response(), responseClass);
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
		catch(final Exception e)
		{
			LOG.warn("Failed to read {}", this.cacheFile, e);
			return Optional.empty();
		}
		finally
		{
			LOG.debug("Took {}ms to read and deserialize container", System.currentTimeMillis() - startMs);
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
