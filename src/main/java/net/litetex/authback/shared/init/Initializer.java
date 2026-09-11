package net.litetex.authback.shared.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class Initializer
{
	private final Logger logger;
	
	public Initializer()
	{
		this.logger = LoggerFactory.getLogger(this.getClass());
	}
	
	protected void doInit(final Runnable runnable)
	{
		final long startMs = System.currentTimeMillis();
		
		runnable.run();
		
		this.logger.debug("Init took {}ms", System.currentTimeMillis() - startMs);
	}
}
