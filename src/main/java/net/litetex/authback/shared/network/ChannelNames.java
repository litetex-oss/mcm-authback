package net.litetex.authback.shared.network;

import net.minecraft.resources.ResourceLocation;


public final class ChannelNames
{
	public static final ResourceLocation FALLBACK_AUTH = create("fallback_auth");
	
	public static final ResourceLocation SYNC_S2C = create("sync_s2c");
	public static final ResourceLocation SYNC_C2S = create("sync_c2s");
	
	private static ResourceLocation create(final String name)
	{
		return ResourceLocation.fromNamespaceAndPath("authback", name);
	}
	
	private ChannelNames()
	{
	}
}
