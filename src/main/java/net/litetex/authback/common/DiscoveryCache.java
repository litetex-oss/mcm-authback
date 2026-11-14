package net.litetex.authback.common;

import java.nio.file.Path;


public class DiscoveryCache extends SingleHTTPRequestCache
{
	public DiscoveryCache(final Path cacheFile, final int defaultReuseMinutes)
	{
		super(cacheFile, defaultReuseMinutes);
	}
}
