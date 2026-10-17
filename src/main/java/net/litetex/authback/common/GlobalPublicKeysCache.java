package net.litetex.authback.common;

import java.net.URL;
import java.nio.file.Path;

import org.slf4j.Logger;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.minecraft.client.MinecraftClient;


public class GlobalPublicKeysCache extends SingleHTTPRequestCache
{
	public GlobalPublicKeysCache(final Path cacheFile, final int defaultReuseMinutes)
	{
		super(cacheFile, defaultReuseMinutes);
	}
	
	@Override
	public <T> T handleGetCall(
		final Logger mixinLogger,
		final MinecraftClient client,
		final URL url,
		final Class<T> responseClass,
		final Operation<T> original)
	{
		return super.handleGetCall(mixinLogger, client, url, responseClass, original);
	}
}
