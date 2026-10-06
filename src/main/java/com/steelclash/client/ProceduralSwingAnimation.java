package com.steelclash.client;

import com.steelclash.SteelClash;
import com.steelclash.combat.ModAttachments;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholder attack animation (M1): points the right arm along the live arc, so what you see is what the server
 * traces. Also renders the arm in first person. Replaced by authored Blockbench animations in M4.
 */
public class ProceduralSwingAnimation implements IAnimation {
    public static final ResourceLocation LAYER_ID = SteelClash.id("swing");
    private static final FirstPersonConfiguration FIRST_PERSON = new FirstPersonConfiguration(true, false, true, false);

    private final AbstractClientPlayer player;
    @Nullable
    private SwingPose pose;
    private float armYaw;
    private float armPitch;

    public ProceduralSwingAnimation(AbstractClientPlayer player) {
        this.player = player;
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1000, ProceduralSwingAnimation::new);
    }

    @Override
    public boolean isActive() {
        return player.hasData(ModAttachments.COMBAT) && player.getData(ModAttachments.COMBAT).machine.isBusy();
    }

    @Override
    public void setupAnim(float tickDelta) {
        pose = player.hasData(ModAttachments.COMBAT)
                ? SwingPose.of(player, player.getData(ModAttachments.COMBAT), tickDelta).orElse(null)
                : null;
        if (pose != null) {
            // Arm angles are relative to the body, the arc is relative to the view: add the head's turn on the body.
            float headYaw = Mth.wrapDegrees(player.getViewYRot(tickDelta) - Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot));
            armYaw = (float) Math.toRadians(headYaw + pose.yaw());
            armPitch = (float) Math.toRadians(Mth.clamp(player.getViewXRot(tickDelta) + pose.pitch(), -90, 90));
        }
    }

    @Override
    public Vec3f get3DTransform(String modelName, TransformType type, float tickDelta, Vec3f value) {
        if (pose == null || type != TransformType.ROTATION) {
            return value;
        }
        float w = (float) pose.weight();
        return switch (modelName) {
            // Same convention vanilla uses for aiming a bow: -90° points the arm along the view.
            case "rightArm" -> new Vec3f(
                    Mth.lerp(w, value.getX(), -Mth.HALF_PI + armPitch),
                    Mth.lerp(w, value.getY(), armYaw),
                    Mth.lerp(w, value.getZ(), 0f));
            // Twist the torso into the swing.
            case "body" -> new Vec3f(value.getX(), Mth.lerp(w, value.getY(), (float) Math.toRadians(pose.yaw() * 0.3)), value.getZ());
            default -> value;
        };
    }

    @Override
    public FirstPersonMode getFirstPersonMode(float tickDelta) {
        return isActive() ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
    }

    @Override
    public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
        return FIRST_PERSON;
    }
}
