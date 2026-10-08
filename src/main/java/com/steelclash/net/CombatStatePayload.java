package com.steelclash.net;

import com.steelclash.SteelClash;
import com.steelclash.combat.CombatData;
import com.steelclash.core.AttackTimings;
import com.steelclash.core.AttackType;
import com.steelclash.core.CombatStateMachine;
import com.steelclash.core.Phase;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Server → clients: snapshot of an entity's attack state, sent on every phase change. */
public record CombatStatePayload(int entityId, Phase phase, AttackType attackType, long phaseElapsedUs, long phaseDurationUs,
                                 AttackTimings timings, int riposteTicks, boolean heavy, boolean morphed,
                                 boolean comboAllowed, int variant, boolean mirrored, boolean thwacked, float recoverFrom,
                                 CombatStateMachine.PredictionState predictionState,
                                 Optional<ResourceLocation> profile, boolean authoritative) implements CustomPacketPayload {
    public static final Type<CombatStatePayload> TYPE = new Type<>(SteelClash.id("combat_state"));

    public static final StreamCodec<FriendlyByteBuf, CombatStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(CombatStatePayload::write, CombatStatePayload::read);

    public static CombatStatePayload of(LivingEntity entity, CombatData data, boolean authoritative) {
        return new CombatStatePayload(entity.getId(), data.machine.phase(), data.machine.type(),
                data.machine.phaseElapsedUs(), data.machine.phaseDurationUs(), data.machine.timings(), data.machine.riposteTicks(),
                data.machine.isHeavy(), data.machine.isMorphed(), data.machine.isComboAllowed(),
                data.machine.variant(), data.machine.isMirrored(), data.machine.isThwacked(), (float) data.machine.recoverFrom(),
                data.machine.predictionState(),
                Optional.ofNullable(data.profileKey).map(key -> key.location()), authoritative);
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(phase.ordinal());
        buf.writeByte(attackType.ordinal());
        buf.writeVarLong(phaseElapsedUs);
        buf.writeVarLong(phaseDurationUs);
        buf.writeVarInt(timings.windupUs());
        buf.writeVarInt(timings.releaseUs());
        buf.writeVarInt(timings.recoveryUs());
        buf.writeVarInt(riposteTicks);
        buf.writeByte((heavy ? 1 : 0) | (morphed ? 2 : 0) | (comboAllowed ? 4 : 0) | (mirrored ? 8 : 0) | (thwacked ? 16 : 0));
        buf.writeVarInt(variant);
        if (thwacked) {
            buf.writeFloat(recoverFrom);
        }
        buf.writeVarInt(predictionState.guardRecovery());
        buf.writeVarInt(predictionState.parryCooldown());
        buf.writeVarInt(predictionState.parryCooldownLeft());
        buf.writeVarInt(predictionState.parriedHits());
        buf.writeVarInt(predictionState.activeParryTicks());
        buf.writeByte((predictionState.staggerAllowsParry() ? 1 : 0) | (predictionState.fromGuard() ? 2 : 0)
                | (predictionState.counterFeinted() ? 4 : 0));
        buf.writeVarInt(predictionState.attackSerial());
        buf.writeOptional(profile, FriendlyByteBuf::writeResourceLocation);
        buf.writeBoolean(authoritative);
    }

    private static CombatStatePayload read(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        Phase phase = Phase.byId(buf.readByte());
        AttackType type = AttackType.byId(buf.readByte());
        long phaseElapsedUs = buf.readVarLong();
        long phaseDurationUs = buf.readVarLong();
        AttackTimings timings = new AttackTimings(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        int riposteTicks = buf.readVarInt();
        int flags = buf.readByte();
        int variant = buf.readVarInt();
        boolean thwacked = (flags & 16) != 0;
        float recoverFrom = thwacked ? buf.readFloat() : 1f;
        int guardRecovery = buf.readVarInt();
        int parryCooldown = buf.readVarInt();
        int parryCooldownLeft = buf.readVarInt();
        int parriedHits = buf.readVarInt();
        int activeParryTicks = buf.readVarInt();
        int predictionFlags = buf.readByte();
        int attackSerial = buf.readVarInt();
        CombatStateMachine.PredictionState predictionState = new CombatStateMachine.PredictionState(guardRecovery,
                parryCooldown, parryCooldownLeft, parriedHits, (predictionFlags & 1) != 0, activeParryTicks,
                (predictionFlags & 2) != 0, (predictionFlags & 4) != 0, attackSerial);
        return new CombatStatePayload(entityId, phase, type, phaseElapsedUs, phaseDurationUs, timings, riposteTicks,
                (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, variant, (flags & 8) != 0, thwacked, recoverFrom, predictionState,
                buf.readOptional(FriendlyByteBuf::readResourceLocation), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
