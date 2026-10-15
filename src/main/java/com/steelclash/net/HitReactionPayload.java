package com.steelclash.net;

import com.steelclash.SteelClash;
import com.steelclash.core.AttackType;
import com.steelclash.core.CollisionProvenance;
import com.steelclash.core.CombatStateMachine;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server → players watching a body: a blade met it, at this point and travelling this way. Clients turn it into the
 * struck part's flinch, a blood spray along the blade, a death fall along the blow, a lost head, or (for
 * {@link Kind#BOUNCE}) the parried attacker's blade rebounding from where it met the guard.
 *
 * @param entity   the struck body, or for a bounce the attacker whose blade was parried
 * @param region   {@link com.steelclash.core.HitLocation.Region} ordinal
 * @param x        presentation effect point (for a bounce, slightly offset from the body contact)
 * @param capturedBodyPoint body-clamped point in the target's historical world frame
 * @param entryPoint raw segment entry point in the historical collision AABB
 * @param dx       the blade's travel there, normalised
 * @param strength 1 for a light blow, more for a heavy
 * @param progress for a bounce, the blade's release progress when the delayed defense resolved
 * @param capturedProgress release progress at the original server contact
 * @param serial   for a bounce, the server's serial of the parried attack, so only its stagger rebounds
 * @param attackIdentity identity captured with the server contact
 * @param contactTick server game tick when the contact was traced
 * @param normal unique entry normal for the tested surface, when meaningful
 * @param collisionProvenance whether the entry surface was the body AABB or inflated AABB with body-clamped point
 * @param spray    blood particles to throw along the blade (0 = none)
 */
public record HitReactionPayload(int entity, Kind kind, int region, double x, double y, double z, float dx, float dy, float dz,
                                 float strength, double progress, double capturedProgress, int serial,
                                 CombatStateMachine.AttackIdentity attackIdentity, long contactTick,
                                 Vec3 capturedBodyPoint, Vec3 entryPoint,
                                 @Nullable Vec3 normal, CollisionProvenance collisionProvenance,
                                 int spray) implements CustomPacketPayload {
    public HitReactionPayload {
        if (serial != attackIdentity.serial()) {
            throw new IllegalArgumentException("payload serial must match the captured attack identity");
        }
    }

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
                buffer.writeDouble(payload.x);
                buffer.writeDouble(payload.y);
                buffer.writeDouble(payload.z);
                buffer.writeFloat(payload.dx);
                buffer.writeFloat(payload.dy);
                buffer.writeFloat(payload.dz);
                buffer.writeFloat(payload.strength);
                buffer.writeDouble(payload.progress);
                buffer.writeDouble(payload.capturedProgress);
                buffer.writeInt(payload.serial);
                CombatStateMachine.AttackIdentity identity = payload.attackIdentity;
                buffer.writeInt(identity.serial());
                buffer.writeByte(identity.type().ordinal());
                buffer.writeBoolean(identity.heavy());
                buffer.writeBoolean(identity.morphed());
                buffer.writeBoolean(identity.counterFeinted());
                buffer.writeInt(identity.variant());
                buffer.writeBoolean(identity.mirrored());
                buffer.writeLong(payload.contactTick);
                writeVec(buffer, payload.capturedBodyPoint);
                writeVec(buffer, payload.entryPoint);
                buffer.writeBoolean(payload.normal != null);
                if (payload.normal != null) {
                    writeVec(buffer, payload.normal);
                }
                buffer.writeByte(payload.collisionProvenance.ordinal());
                buffer.writeByte(payload.spray);
            },
            buffer -> {
                int entity = buffer.readInt();
                Kind kind = Kind.values()[Math.floorMod(buffer.readByte(), Kind.values().length)];
                int region = buffer.readByte();
                double x = buffer.readDouble();
                double y = buffer.readDouble();
                double z = buffer.readDouble();
                float dx = buffer.readFloat();
                float dy = buffer.readFloat();
                float dz = buffer.readFloat();
                float strength = buffer.readFloat();
                double progress = buffer.readDouble();
                double capturedProgress = buffer.readDouble();
                int serial = buffer.readInt();
                CombatStateMachine.AttackIdentity identity = new CombatStateMachine.AttackIdentity(buffer.readInt(),
                        AttackType.byId(buffer.readUnsignedByte()), buffer.readBoolean(), buffer.readBoolean(),
                        buffer.readBoolean(), buffer.readInt(), buffer.readBoolean());
                long contactTick = buffer.readLong();
                Vec3 capturedBodyPoint = readVec(buffer);
                Vec3 entryPoint = readVec(buffer);
                Vec3 normal = buffer.readBoolean() ? readVec(buffer) : null;
                CollisionProvenance provenance = CollisionProvenance.values()[Math.floorMod(buffer.readByte(),
                        CollisionProvenance.values().length)];
                int spray = buffer.readUnsignedByte();
                return new HitReactionPayload(entity, kind, region, x, y, z, dx, dy, dz, strength, progress, capturedProgress, serial,
                        identity, contactTick, capturedBodyPoint, entryPoint, normal, provenance, spray);
            });

    private static void writeVec(ByteBuf buffer, Vec3 vector) {
        buffer.writeDouble(vector.x);
        buffer.writeDouble(vector.y);
        buffer.writeDouble(vector.z);
    }

    private static Vec3 readVec(ByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
