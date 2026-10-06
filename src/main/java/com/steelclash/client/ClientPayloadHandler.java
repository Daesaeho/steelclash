package com.steelclash.client;

import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.net.CombatStatePayload;
import com.steelclash.net.StaminaPayload;
import com.steelclash.profile.WeaponProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void handleCombatState(CombatStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !(mc.level.getEntity(payload.entityId()) instanceof LivingEntity entity)) {
                return;
            }
            CombatData data = entity.getData(ModAttachments.COMBAT);
            // The local player predicts its own actions with the same state machine. Non-authoritative updates only
            // carry windows the server alone can decide (a landed hit allows a combo, a parry opens a riposte).
            if (entity == mc.player && !payload.authoritative()) {
                data.machine.applyWindows(payload.riposteTicks(), payload.comboAllowed());
                return;
            }
            data.machine.apply(payload.phase(), payload.attackType(), payload.phaseTick(), payload.phaseDuration(),
                    payload.timings(), payload.riposteTicks(), payload.heavy(), payload.morphed(), payload.comboAllowed());
            data.profileKey = payload.profile()
                    .map(location -> ResourceKey.create(WeaponProfiles.REGISTRY_KEY, location))
                    .orElse(null);
            if (payload.authoritative()) {
                data.queuedAttack = null;
            }
        });
    }

    public static void handleStamina(StaminaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                CombatData data = mc.player.getData(ModAttachments.COMBAT);
                data.stamina.setMax(payload.max());
                data.stamina.set(payload.current());
            }
        });
    }
}
