package com.steelclash.net;

import com.steelclash.SteelClash;
import com.steelclash.combat.Combat;
import com.steelclash.combat.Dodge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: windup modifiers decided by the player's input. */
public record ActionPayload(Action action) implements CustomPacketPayload {
    public enum Action {
        /** The attack input is still held: make it a heavy. */
        HEAVY,
        FEINT,
        /** The player dodged (they've already moved themselves): pay for it, cancel a windup or guard. */
        DODGE
    }

    public static final Type<ActionPayload> TYPE = new Type<>(SteelClash.id("action"));

    public static final StreamCodec<ByteBuf, ActionPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            id -> new ActionPayload(Action.values()[Math.floorMod(id, Action.values().length)]),
            payload -> payload.action().ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!player.isAlive() || player.isSpectator()) {
                return;
            }
            switch (payload.action()) {
                case HEAVY -> Combat.requestHeavy(player);
                case FEINT -> Combat.requestFeint(player);
                case DODGE -> Dodge.request(player);
            }
        });
    }
}
