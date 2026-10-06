package com.steelclash.net;

import com.steelclash.client.ClientPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(AttackInputPayload.TYPE, AttackInputPayload.STREAM_CODEC, AttackInputPayload::handle);
        registrar.playToServer(BlockInputPayload.TYPE, BlockInputPayload.STREAM_CODEC, BlockInputPayload::handle);
        // A lambda (not a method reference) so the client-only class is only loaded when a packet actually arrives,
        // which never happens on a dedicated server.
        registrar.playToClient(CombatStatePayload.TYPE, CombatStatePayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleCombatState(payload, context));
        registrar.playToClient(StaminaPayload.TYPE, StaminaPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandler.handleStamina(payload, context));
    }
}
