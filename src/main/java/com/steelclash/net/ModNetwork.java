package com.steelclash.net;

import com.steelclash.client.ClientPayloadHandler;
import com.steelclash.combat.CombatProfiler;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "7";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(AttackInputPayload.TYPE, AttackInputPayload.STREAM_CODEC, AttackInputPayload::handle);
        registrar.playToServer(BlockInputPayload.TYPE, BlockInputPayload.STREAM_CODEC, BlockInputPayload::handle);
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, ActionPayload::handle);
        // A lambda (not a method reference) so the client-only class is only loaded when a packet actually arrives,
        // which never happens on a dedicated server.
        registrar.playToClient(CombatStatePayload.TYPE, CombatStatePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleCombatState(payload, context));
        registrar.playToClient(StaminaPayload.TYPE, StaminaPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleStamina(payload, context));
        registrar.playToClient(FeedbackPayload.TYPE, FeedbackPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleFeedback(payload, context));
    }

    /**
     * Sends to one player, if their connection can take it. Fake players (other mods' machines, GameTest mocks) have
     * connections that never negotiated our channels, and sending to them throws.
     */
    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload)) {
            CombatProfiler.begin(CombatProfiler.Section.SYNC);
            try {
                PacketDistributor.sendToPlayer(player, payload);
                CombatProfiler.count(CombatProfiler.Counter.PACKETS, 1);
            } finally {
                CombatProfiler.end(CombatProfiler.Section.SYNC);
            }
        }
    }

    /** Sends to everyone tracking the entity, and to the entity itself if it's a (real) player. */
    public static void sendToTrackingAndSelf(Entity entity, CustomPacketPayload payload) {
        CombatProfiler.begin(CombatProfiler.Section.SYNC);
        try {
            PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
            CombatProfiler.count(CombatProfiler.Counter.PACKETS, 1);
        } finally {
            CombatProfiler.end(CombatProfiler.Section.SYNC);
        }
        if (entity instanceof ServerPlayer player) {
            sendTo(player, payload);
        }
    }
}
