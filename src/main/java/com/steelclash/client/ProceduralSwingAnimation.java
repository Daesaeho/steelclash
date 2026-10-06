package com.steelclash.client;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.client.anim.CombatPose;
import com.zigythebird.playeranim.neoforge.event.PlayerAnimationRegisterEvent;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The player's combat animation layer (Player Animation Library). The weapon arm follows the live arc (what you see is
 * what the server traces) and the held weapon is rotated in the hand to line up with it; torso, head, legs and off arm
 * come from the archetype's pose clips ({@code assets/steelclash/steelclash_animations}). Also renders the arms in
 * first person.
 * <p>
 * Bones arrive holding vanilla's pose in radians (PAL copies the model parts in), like playerAnimator's parts did, so
 * part rotations carry over unchanged. Two bones are applied differently by PAL and are converted in
 * {@link #wholeBody} and {@link #itemRotation} so the result looks the same (see docs/spikes.md, "PAL").
 */
@EventBusSubscriber(modid = SteelClash.MOD_ID, value = Dist.CLIENT)
public class ProceduralSwingAnimation implements IAnimation {
    /** Above ordinary emote/animation layers, as with playerAnimator. */
    public static final int PRIORITY = 1000;
    private static final FirstPersonConfiguration FIRST_PERSON_ONE_HAND = new FirstPersonConfiguration(true, false, true, false);
    private static final FirstPersonConfiguration FIRST_PERSON_TWO_HANDS = new FirstPersonConfiguration(true, true, true, false);

    private final AbstractClientPlayer player;
    @Nullable
    private CombatPose pose;
    private double[] weaponArm = {0, 0, 0};

    public ProceduralSwingAnimation(AbstractClientPlayer player) {
        this.player = player;
    }

    /** PAL fires this once for every player as it's created (and again if it's recreated). */
    @SubscribeEvent
    static void onRegisterAnimations(PlayerAnimationRegisterEvent event) {
        event.getAnimManager().addAnimLayer(PRIORITY, new ProceduralSwingAnimation(event.getClientPlayer()));
    }

    @Override
    public boolean isActive() {
        return pose != null || CombatPose.of(player, 0f).isPresent();
    }

    @Override
    public void setupAnim(AnimationData state) {
        pose = CombatPose.of(player, ClientFeel.animationPartialTick(player, state.getPartialTick())).orElse(null);
        if (pose != null) {
            weaponArm = pose.weaponArmForRotatedItem();
        }
    }

    @Override
    public PlayerAnimBone get3DTransform(@NotNull PlayerAnimBone bone) {
        if (pose == null) {
            return bone;
        }
        float w = (float) pose.weight();
        switch (bone.getName()) {
            case "right_arm" -> {
                bone.setBend(pose.offset("rightArmBend", 0)); // kept for later: PAL on 1.21.1 doesn't draw bends
                if (pose.kick()) {
                    add(bone, "rightArm");
                } else {
                    bone.updateRotation(
                            Mth.lerp(w, bone.getRotX(), (float) weaponArm[0]) + pose.offset("rightArm", 0),
                            Mth.lerp(w, bone.getRotY(), (float) weaponArm[1]) + pose.offset("rightArm", 1),
                            Mth.lerp(w, bone.getRotZ(), (float) weaponArm[2]) + pose.offset("rightArm", 2));
                }
            }
            case "left_arm" -> {
                bone.setBend(pose.offset("leftArmBend", 0));
                if (pose.twoHanded() && !pose.kick()) {
                    double[] grip = pose.gripArm(weaponArm);
                    bone.updateRotation(Mth.lerp(w, bone.getRotX(), (float) grip[0]), Mth.lerp(w, bone.getRotY(), (float) grip[1]),
                            Mth.lerp(w, bone.getRotZ(), (float) grip[2]));
                } else {
                    add(bone, "leftArm");
                }
            }
            // Turn the held weapon so its blade lies along the arm (along the arc), then roll it so the edge leads.
            case "right_item" -> {
                if (!pose.kick()) {
                    itemRotation(bone, w);
                }
            }
            case "body" -> wholeBody(bone);
            case "torso" -> add(bone, "torso");
            case "head" -> add(bone, "head");
            case "right_leg" -> add(bone, "rightLeg");
            case "left_leg" -> add(bone, "leftLeg");
            default -> {
            }
        }
        return bone;
    }

    /** Additive clip offset for a model part (the clips keep their playerAnimator-era camelCase names). */
    private void add(PlayerAnimBone bone, String clipPart) {
        bone.addRot(pose.offset(clipPart, 0), pose.offset(clipPart, 1), pose.offset(clipPart, 2));
    }

    /**
     * Whole-model lean. playerAnimator rotated it Z·Y·X as given; PAL negates X and Y first, so the clip's X and Y go in
     * negated to lean the same way.
     */
    private void wholeBody(PlayerAnimBone bone) {
        bone.addRot(-pose.offset("body", 0), -pose.offset("body", 1), pose.offset("body", 2));
    }

    /**
     * Held weapon. The angles are worked out in the hand frame playerAnimator used (rotated Z, then Y, then X, so X, the
     * grip pitch, acts on the item first; the arm's length lies on Z, so rolling about Z twists the blade). PAL applies
     * the item bone as Z(-rotY), Y(-rotZ), X(-rotX), so each hand-frame angle goes into the matching PAL channel negated.
     */
    private void itemRotation(PlayerAnimBone bone, float w) {
        float x = (float) Math.toRadians(Config.Client.WEAPON_GRIP_PITCH.get()) * w;
        float y = 0;
        float z = 0;
        float twist = (float) Math.toRadians(pose.bladeTwist() * Config.Client.BLADE_TWIST.get()) * w;
        switch (Config.Client.BLADE_TWIST_AXIS.get()) {
            case X -> x += twist;
            case Y -> y += twist;
            case Z -> z += twist;
        }
        bone.addRot(-x, -z, -y);
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode() {
        return pose != null ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
    }

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration() {
        return pose != null && pose.twoHanded() ? FIRST_PERSON_TWO_HANDS : FIRST_PERSON_ONE_HAND;
    }
}
