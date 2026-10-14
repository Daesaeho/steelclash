package com.steelclash.net;

import com.steelclash.SteelClash;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → players watching a body: a blade met it, at this point and travelling this way. Clients turn it into the
 * struck part's flinch, a blood spray along the blade, a death fall along the blow, a lost head, or (for
 * {@link Kind#BOUNCE}) the parried attacker's blade rebounding from where it met the guard.
 *
 * @param entity   the struck body, or for a bounce the attacker whose blade was parried
 * @param region   {@link com.steelclash.core.HitLocation.Region} ordinal
 * @param x        contact point
 * @param dx       the blade's travel there, normalised
 * @param strength 1 for a light blow, more for a heavy
 * @param progress for a bounce, the release progress (0 to 1) where the blade met the guard
 * @param serial   for a bounce, the server's serial of the parried attack, so only its stagger rebounds
 * @param spray    blood particles to throw along the blade (0 = none)
 */
public record HitReactionPayload(int entity, Kind kind, int region, float x, float y, float z, float dx, float dy, float dz,
                                 float strength, float progress, int serial, int spray) implements CustomPacketPayload {
    public enum Kind {
        /** The blade landed. */
        HIT,
        /** The blade landed and killed. */
        KILL,
        /** The blade landed, killed and took the head. */
        SEVER,
        /** The blade was parried and bounces back. */
        BOUNCE
    }

    public static final Type<HitReactionPayload> TYPE = new Type<>(SteelClash.id("hit_reaction"));

    public static final StreamCodec<ByteBuf, HitReactionPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeInt(payload.entity);
                buffer.writeByte(payload.kind.ordinal());
                buffer.writeByte(payload.region);
                buffer.writeFloat(payload.x);
                buffer.writeFloat(payload.y);
                buffer.writeFloat(payload.z);
                buffer.writeFloat(payload.dx);
                buffer.writeFloat(payload.dy);
                buffer.writeFloat(payload.dz);
                buffer.writeFloat(payload.strength);
                buffer.writeFloat(payload.progress);
                buffer.writeInt(payload.serial);
                buffer.writeByte(payload.spray);
            },
            buffer -> new HitReactionPayload(buffer.readInt(), Kind.values()[Math.floorMod(buffer.readByte(), Kind.values().length)],
                    buffer.readByte(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readInt(),
                    buffer.readUnsignedByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
