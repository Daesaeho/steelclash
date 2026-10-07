package com.steelclash.net;

import com.steelclash.SteelClash;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → the downed player and everyone tracking them: the downed state for the crawl pose and the HUD.
 *
 * @param ticksLeft   ticks before bleeding out, -1 when standing
 * @param reviveTicks revive progress so far
 * @param reviverId   entity id of the ally reviving, -1 for nobody
 */
public record DownedPayload(int entityId, int ticksLeft, int reviveTicks, int reviverId) implements CustomPacketPayload {
    public static final Type<DownedPayload> TYPE = new Type<>(SteelClash.id("downed"));

    public static final StreamCodec<ByteBuf, DownedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DownedPayload::entityId,
            ByteBufCodecs.VAR_INT, DownedPayload::ticksLeft,
            ByteBufCodecs.VAR_INT, DownedPayload::reviveTicks,
            ByteBufCodecs.VAR_INT, DownedPayload::reviverId,
            DownedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
