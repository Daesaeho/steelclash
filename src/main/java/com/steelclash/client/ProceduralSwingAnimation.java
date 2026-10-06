package com.steelclash.client;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.client.anim.CombatPose;
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
 * The player's combat animation layer. The weapon arm follows the live arc (what you see is what the server traces)
 * and the held weapon is rotated in the hand to line up with it; torso, head, legs and off arm come from the
 * archetype's pose clips ({@code assets/steelclash/steelclash_animations}). Also renders the arms in first person.
 */
public class ProceduralSwingAnimation implements IAnimation {
    public static final ResourceLocation LAYER_ID = SteelClash.id("swing");
    private static final FirstPersonConfiguration FIRST_PERSON_ONE_HAND = new FirstPersonConfiguration(true, false, true, false);
    private static final FirstPersonConfiguration FIRST_PERSON_TWO_HANDS = new FirstPersonConfiguration(true, true, true, false);

    private final AbstractClientPlayer player;
    @Nullable
    private CombatPose pose;
    private double[] weaponArm = {0, 0, 0};

    public ProceduralSwingAnimation(AbstractClientPlayer player) {
        this.player = player;
    }

    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1000, ProceduralSwingAnimation::new);
    }

    @Override
    public boolean isActive() {
        return pose != null || CombatPose.of(player, 0f).isPresent();
    }

    @Override
    public void setupAnim(float tickDelta) {
        pose = CombatPose.of(player, ClientFeel.animationPartialTick(player, tickDelta)).orElse(null);
        if (pose != null) {
            weaponArm = pose.weaponArmForRotatedItem();
        }
    }

    @Override
    public Vec3f get3DTransform(String part, TransformType type, float tickDelta, Vec3f value) {
        if (pose == null) {
            return value;
        }
        float w = (float) pose.weight();
        if (type == TransformType.BEND) {
            return switch (part) {
                case "rightArm" -> new Vec3f(0f, pose.offset("rightArmBend", 0), 0f);
                case "leftArm" -> new Vec3f(0f, pose.offset("leftArmBend", 0), 0f);
                default -> value;
            };
        }
        if (type != TransformType.ROTATION) {
            return value;
        }
        return switch (part) {
            case "rightArm" -> pose.kick() ? add(value, part) : new Vec3f(
                    Mth.lerp(w, value.getX(), (float) weaponArm[0]) + pose.offset("rightArm", 0),
                    Mth.lerp(w, value.getY(), (float) weaponArm[1]) + pose.offset("rightArm", 1),
                    Mth.lerp(w, value.getZ(), (float) weaponArm[2]) + pose.offset("rightArm", 2));
            case "leftArm" -> {
                if (pose.twoHanded() && !pose.kick()) {
                    double[] grip = pose.gripArm(weaponArm);
                    yield new Vec3f(Mth.lerp(w, value.getX(), (float) grip[0]), Mth.lerp(w, value.getY(), (float) grip[1]),
                            Mth.lerp(w, value.getZ(), (float) grip[2]));
                }
                yield add(value, part);
            }
            // Turn the held weapon so its blade lies along the arm (along the arc), then roll it so the edge leads.
            case "rightItem" -> pose.kick() ? value : itemRotation(value, w);
            case "body", "torso", "head", "rightLeg", "leftLeg" -> add(value, part);
            default -> value;
        };
    }

    /**
     * playerAnimator applies the item rotation as Z, then Y, then X (so X, the grip pitch, acts on the weapon first),
     * after vanilla's -90° hand rotation, which leaves the arm's length on Z: rolling about Z twists the blade.
     */
    private Vec3f itemRotation(Vec3f value, float w) {
        float x = value.getX() + (float) Math.toRadians(Config.Client.WEAPON_GRIP_PITCH.get()) * w;
        float y = value.getY();
        float z = value.getZ();
        float twist = (float) Math.toRadians(pose.bladeTwist() * Config.Client.BLADE_TWIST.get()) * w;
        switch (Config.Client.BLADE_TWIST_AXIS.get()) {
            case X -> x += twist;
            case Y -> y += twist;
            case Z -> z += twist;
        }
        return new Vec3f(x, y, z);
    }

    private Vec3f add(Vec3f value, String part) {
        return new Vec3f(value.getX() + pose.offset(part, 0), value.getY() + pose.offset(part, 1), value.getZ() + pose.offset(part, 2));
    }

    @Override
    public FirstPersonMode getFirstPersonMode(float tickDelta) {
        return pose != null ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
    }

    @Override
    public FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
        return pose != null && pose.twoHanded() ? FIRST_PERSON_TWO_HANDS : FIRST_PERSON_ONE_HAND;
    }
}
