package net.litetex.authback.shared.network.configuration;

import net.litetex.authback.shared.network.ChannelNames;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;


public record SyncPayloadS2C(
	byte[] challenge
) implements CustomPacketPayload
{
	public static final CustomPacketPayload.Type<SyncPayloadS2C> ID =
		new CustomPacketPayload.Type<>(ChannelNames.SYNC_S2C);
	
	public static final StreamCodec<FriendlyByteBuf, SyncPayloadS2C> PACKET_CODEC = CustomPacketPayload.codec(
		SyncPayloadS2C::write,
		SyncPayloadS2C::new
	);
	
	public SyncPayloadS2C(final FriendlyByteBuf buf)
	{
		this(buf.readByteArray());
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return ID;
	}
	
	private void write(final FriendlyByteBuf friendlyByteBuf)
	{
		friendlyByteBuf.writeByteArray(this.challenge);
	}
}
