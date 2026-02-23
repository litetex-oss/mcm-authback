package net.litetex.authback.shared.network.configuration;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;


public final class ConfigurationRegistrySetup
{
	public static void setup()
	{
		PayloadTypeRegistry.configurationS2C().register(SyncPayloadS2C.ID, SyncPayloadS2C.PACKET_CODEC);
		PayloadTypeRegistry.configurationC2S().register(SyncPayloadC2S.ID, SyncPayloadC2S.PACKET_CODEC);
	}
	
	private ConfigurationRegistrySetup()
	{
	}
}
