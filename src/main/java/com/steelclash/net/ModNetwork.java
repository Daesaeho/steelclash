package com.steelclash.net;

import com.steelclash.client.ClientPayloadHandler;
import com.steelclash.combat.CombatProfiler;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.InputLimit;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "9";

    private ModNetwork() {
    }

    /** Server: whether another combat input from this client fits in this tick's budget ({@link InputLimit}). */
    static boolean allowInput(Player player) {
        return player.getData(ModAttachments.COMBAT).inputs.allow(player.level().getGameTime());
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
        registrar.playToClient(DownedPayload.TYPE, DownedPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleDowned(payload, context));
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

    /**
     * Sends to everyone tracking the entity, and to the entity itself if it's a (real) player. When a fake player is
     * watching (GameTest mocks, other mods' machines), the tracking broadcast would throw at it, so the level's players
     * in tracking range are sent to one by one instead.
     */
    public static void sendToTrackingAndSelf(Entity entity, CustomPacketPayload payload) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        List<ServerPlayer> watchers = level.players();
        boolean allReal = true;
        for (ServerPlayer watcher : watchers) {
            if (watcher.connection == null || !watcher.connection.hasChannel(payload)) {
                allReal = false;
                break;
            }
        }
        CombatProfiler.begin(CombatProfiler.Section.SYNC);
        try {
            if (allReal) {
                PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
                CombatProfiler.count(CombatProfiler.Counter.PACKETS, 1);
            }
        } finally {
            CombatProfiler.end(CombatProfiler.Section.SYNC);
        }
        if (!allReal) {
            double range = entity.getType().clientTrackingRange() * 16.0;
            for (ServerPlayer watcher : watchers) {
                if (watcher != entity && watcher.distanceToSqr(entity) <= range * range) {
                    sendTo(watcher, payload);
                }
            }
        }
        if (entity instanceof ServerPlayer player) {
            sendTo(player, payload);
        }
    }
}
