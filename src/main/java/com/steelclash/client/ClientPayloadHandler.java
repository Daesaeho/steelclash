package com.steelclash.client;

import com.steelclash.combat.CombatData;
import com.steelclash.combat.ModAttachments;
import com.steelclash.core.LagMath;
import com.steelclash.net.CombatStatePayload;
import com.steelclash.net.DownedPayload;
import com.steelclash.net.FeedbackPayload;
import com.steelclash.net.StaminaPayload;
import com.steelclash.profile.WeaponProfiles;
import com.steelclash.client.anim.CombatPresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
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
            data.machine.apply(payload.phase(), payload.attackType(), payload.phaseElapsedUs(), payload.phaseDurationUs(),
                    payload.timings(), payload.riposteTicks(), payload.heavy(), payload.morphed(), payload.comboAllowed(),
                    payload.variant(), payload.mirrored(), payload.thwacked(), payload.recoverFrom());
            data.machine.applyPredictionState(payload.predictionState());
            data.profileKey = payload.profile()
                    .map(location -> ResourceKey.create(WeaponProfiles.REGISTRY_KEY, location))
                    .orElse(null);
            if (payload.authoritative()) {
                CombatPresentation.corrected(entity);
                data.queuedAttack = null;
                if (entity == mc.player) {
                    // The correction is half a round trip old: catch up, so a stagger ends here when it ends on the
                    // server and the next input (another half trip away) arrives after it.
                    for (int tick = LagMath.oneWayTicks(ownLatency(mc)); tick > 0; tick--) {
                        data.machine.tick();
                    }
                }
            }
        });
    }

    private static int ownLatency(Minecraft mc) {
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    /** Downed state of a player: the crawl pose (the local player poses itself) and the HUD read it. */
    public static void handleDowned(DownedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || !(mc.level.getEntity(payload.entityId()) instanceof Player player)) {
                return;
            }
            CombatData data = player.getData(ModAttachments.COMBAT);
            data.downedTicksLeft = payload.ticksLeft();
            data.reviveTicks = payload.reviveTicks();
            data.reviverId = payload.reviverId();
            player.setForcedPose(data.isDowned() ? Pose.SWIMMING : null);
        });
    }

    public static void handleFeedback(FeedbackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientFeel.onFeedback(payload));
    }

    public static void handleStamina(StaminaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                CombatData data = mc.player.getData(ModAttachments.COMBAT);
                data.stamina.setMax(payload.max());
                data.stamina.set(payload.current());
                data.stamina.setExhausted(payload.exhausted()); // so feints, morphs and dashes are predicted as the server rules
            }
        });
    }
}
