package net.litetex.authback.shared.network.configuration;

import net.litetex.authback.shared.network.ChannelNames;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;


public record SyncPayloadC2S(
	byte[] signature,
	byte[] publicKey
) implements CustomPacketPayload
{
	public static final Type<SyncPayloadC2S> ID =
		new Type<>(ChannelNames.SYNC_C2S);
	
	public static final StreamCodec<FriendlyByteBuf, SyncPayloadC2S> PACKET_CODEC = CustomPacketPayload.codec(
		SyncPayloadC2S::write,
		SyncPayloadC2S::new
	);
	
	public SyncPayloadC2S(final FriendlyByteBuf buf)
	{
		this(buf.readByteArray(), buf.readByteArray());
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type()
	{
		return ID;
	}
	
	private void write(final FriendlyByteBuf friendlyByteBuf)
	{
		friendlyByteBuf.writeByteArray(this.signature);
		friendlyByteBuf.writeByteArray(this.publicKey);
	}
}
