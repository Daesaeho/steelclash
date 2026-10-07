package com.steelclash.net;

import com.steelclash.SteelClash;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → a participating player: drives local-only feel (camera shake, hit-stop, headshot sound, lost draws). */
public record FeedbackPayload(Kind kind, float strength) implements CustomPacketPayload {
    public enum Kind {
        /** Your swing connected. */
        LANDED,
        /** You were hit. */
        TAKEN,
        /** You parried, or your attack was parried. */
        PARRIED,
        /** A shield stopped the hit (yours or theirs). */
        BLOCKED,
        /** Your blade hit a wall. */
        CLANK,
        /** Your arrow, bolt or thrown weapon hit someone in the head. */
        HEADSHOT,
        /** You were hurt while drawing a bow or loading a crossbow: the draw is lost. */
        DRAW_INTERRUPTED
    }

    public static final Type<FeedbackPayload> TYPE = new Type<>(SteelClash.id("feedback"));

    public static final StreamCodec<ByteBuf, FeedbackPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(id -> Kind.values()[Math.floorMod(id, Kind.values().length)], Kind::ordinal), FeedbackPayload::kind,
            ByteBufCodecs.FLOAT, FeedbackPayload::strength,
            FeedbackPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
