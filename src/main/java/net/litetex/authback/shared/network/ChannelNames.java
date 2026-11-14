package net.litetex.authback.shared.network;

import net.minecraft.resources.Identifier;


public final class ChannelNames
{
	public static final Identifier FALLBACK_AUTH = create("fallback_auth_v1");
	
	public static final Identifier SYNC_S2C = create("sync_s2c_v1");
	public static final Identifier SYNC_C2S = create("sync_c2s_v1");
	
	private static Identifier create(final String name)
	{
		return Identifier.fromNamespaceAndPath("authback", name);
	}
	
	private ChannelNames()
	{
	}
}
