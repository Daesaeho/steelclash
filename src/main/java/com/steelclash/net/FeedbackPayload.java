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

/** Server → a participating player: drives local-only feel and carries captured contact diagnostics when available. */
public record FeedbackPayload(Kind kind, float strength, @Nullable Contact contact, int recipientAttackSerial,
                              boolean parryDefender)
        implements CustomPacketPayload {
    /**
     * Immutable attacker-side contact metadata. For body hits, capturedPoint is the historical body projection; for
     * block clanks it is the block point. The raw entry and recipientAttackSerial remain separate from those values.
     */
    public record Contact(Vec3 capturedPoint, Vec3 entryPoint, Vec3 motion, CombatStateMachine.AttackIdentity attackIdentity,
                          long serverTick, double progress, @Nullable Vec3 normal,
                          CollisionProvenance collisionProvenance) {
    }

    /** Legacy/manual feedback remains representable without inventing contact data or an action token. */
    public FeedbackPayload(Kind kind, float strength) {
        this(kind, strength, null, -1, false);
    }

    public FeedbackPayload(Kind kind, float strength, @Nullable Contact contact, int recipientAttackSerial) {
        this(kind, strength, contact, recipientAttackSerial, false);
    }

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

    public static final StreamCodec<ByteBuf, FeedbackPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeByte(payload.kind.ordinal());
                buffer.writeFloat(payload.strength);
                buffer.writeInt(payload.recipientAttackSerial);
                buffer.writeBoolean(payload.parryDefender);
                Contact contact = payload.contact;
                buffer.writeBoolean(contact != null);
                if (contact != null) {
                    writeVec(buffer, contact.capturedPoint);
                    writeVec(buffer, contact.entryPoint);
                    writeVec(buffer, contact.motion);
                    CombatStateMachine.AttackIdentity id = contact.attackIdentity;
                    buffer.writeInt(id.serial());
                    buffer.writeByte(id.type().ordinal());
                    buffer.writeBoolean(id.heavy());
                    buffer.writeBoolean(id.morphed());
                    buffer.writeBoolean(id.counterFeinted());
                    buffer.writeInt(id.variant());
                    buffer.writeBoolean(id.mirrored());
                    buffer.writeLong(contact.serverTick);
                    buffer.writeDouble(contact.progress);
                    buffer.writeBoolean(contact.normal != null);
                    if (contact.normal != null) {
                        writeVec(buffer, contact.normal);
                    }
                    buffer.writeByte(contact.collisionProvenance.ordinal());
                }
            },
            buffer -> {
                Kind kind = Kind.values()[Math.floorMod(buffer.readByte(), Kind.values().length)];
                float strength = buffer.readFloat();
                int recipientSerial = buffer.readInt();
                boolean parryDefender = buffer.readBoolean();
                Contact contact = null;
                if (buffer.readBoolean()) {
                    Vec3 point = readVec(buffer);
                    Vec3 entryPoint = readVec(buffer);
                    Vec3 motion = readVec(buffer);
                    CombatStateMachine.AttackIdentity id = new CombatStateMachine.AttackIdentity(buffer.readInt(),
                            AttackType.byId(buffer.readUnsignedByte()), buffer.readBoolean(), buffer.readBoolean(),
                            buffer.readBoolean(), buffer.readInt(), buffer.readBoolean());
                    long tick = buffer.readLong();
                    double progress = buffer.readDouble();
                    Vec3 normal = buffer.readBoolean() ? readVec(buffer) : null;
                    CollisionProvenance provenance = CollisionProvenance.values()[Math.floorMod(buffer.readByte(),
                            CollisionProvenance.values().length)];
                    contact = new Contact(point, entryPoint, motion, id, tick, progress, normal, provenance);
                }
                return new FeedbackPayload(kind, strength, contact, recipientSerial, parryDefender);
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
