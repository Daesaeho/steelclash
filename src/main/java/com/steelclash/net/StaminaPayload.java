package com.steelclash.net;

import com.steelclash.SteelClash;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → owning client: current stamina, for the HUD. */
public record StaminaPayload(float current, float max) implements CustomPacketPayload {
    public static final Type<StaminaPayload> TYPE = new Type<>(SteelClash.id("stamina"));

    public static final StreamCodec<ByteBuf, StaminaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, StaminaPayload::current,
            ByteBufCodecs.FLOAT, StaminaPayload::max,
            StaminaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
