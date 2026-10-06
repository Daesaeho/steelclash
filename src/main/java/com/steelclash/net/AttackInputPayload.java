package com.steelclash.net;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.core.AttackType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: the player pressed an attack input. */
public record AttackInputPayload(AttackType attackType) implements CustomPacketPayload {
    public static final Type<AttackInputPayload> TYPE = new Type<>(SteelClash.id("attack_input"));

    public static final StreamCodec<ByteBuf, AttackInputPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT
            .map(id -> new AttackInputPayload(AttackType.byId(id)), payload -> payload.attackType().ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(AttackInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!player.isAlive() || player.isSpectator()) {
                return;
            }
            if (player.isUsingItem()) {
                // Attacking lowers a raised shield / interrupts eating, as in Chivalry 2.
                player.stopUsingItem();
            }
            Combat.requestAttack(player, payload.attackType());
        });
    }
}
