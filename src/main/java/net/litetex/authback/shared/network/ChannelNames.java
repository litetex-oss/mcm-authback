package net.litetex.authback.shared.network;

import net.minecraft.resources.ResourceLocation;


public final class ChannelNames
{
	public static final ResourceLocation FALLBACK_AUTH = create("fallback_auth_v1");
	
	public static final ResourceLocation SYNC_S2C = create("sync_s2c_v1");
	public static final ResourceLocation SYNC_C2S = create("sync_c2s_v1");
	
	private static ResourceLocation create(final String name)
	{
		return ResourceLocation.fromNamespaceAndPath("authback", name);
	}
	
	private ChannelNames()
	{
	}
}
