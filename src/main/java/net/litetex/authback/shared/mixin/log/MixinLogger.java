package net.litetex.authback.shared.mixin.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class MixinLogger
{
	public static Logger common(final String mixinClassName)
	{
		return getLogger("common", mixinClassName);
	}
	
	public static Logger client(final String mixinClassName)
	{
		return getLogger("client", mixinClassName);
	}
	
	public static Logger server(final String mixinClassName)
	{
		return getLogger("server", mixinClassName);
	}
	
	static Logger getLogger(final String variant, final String mixinClassName)
	{
		return LoggerFactory.getLogger("net.litetex.authback.mixin." + variant + "." + mixinClassName);
	}
	
	private MixinLogger()
	{
	}
}
