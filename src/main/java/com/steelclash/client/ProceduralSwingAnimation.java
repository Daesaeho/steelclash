package com.steelclash.client;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.client.anim.CombatPose;
import com.steelclash.core.Vec;
import com.steelclash.core.WeaponRig;
import com.zigythebird.playeranim.neoforge.event.PlayerAnimationRegisterEvent;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.Minecraft;
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
    /** Share of the whole-body turn the legs take back (see {@link #hipsLag}). */
    private static final float HIP_LAG = 0.5f;
    private static final FirstPersonConfiguration FIRST_PERSON_ONE_HAND = new FirstPersonConfiguration(true, false, true, false);
    private static final FirstPersonConfiguration FIRST_PERSON_TWO_HANDS = new FirstPersonConfiguration(true, true, true, false);

    private final AbstractClientPlayer player;
    @Nullable
    private CombatPose pose;
    /**
     * First person only: both arms (and so the weapon) sit this far forward and down (pixels), away from the camera,
     * which is otherwise between the shoulders and gets a raised arm or a backhand windup right in the eye. The blade's
     * direction doesn't change.
     */
    private static final float FIRST_PERSON_FORWARD = 4;
    private static final float FIRST_PERSON_DOWN = 1.5f;

    private WeaponRig rig = WeaponRig.solve(0, 0, 0, 0, WeaponRig.TwistAxis.Z, new double[3], 0);
    /** First person only: the ready stance attacks blend from and back to, instead of vanilla's arm pose. */
    @Nullable
    private CombatPose ready;
    private WeaponRig readyRig = rig;

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
        return pose != null || ready != null || CombatPose.isAvailable(player) || wantsReadyStance();
    }

    @Override
    public void setupAnim(AnimationData state) {
        float partialTick = ClientFeel.animationPartialTick(player, state.getPartialTick());
        pose = CombatPose.of(player, partialTick).orElse(null);
        if (pose != null) {
            rig = rigFor(pose);
        }
        ready = wantsReadyStance() ? CombatPose.ready(player, partialTick).orElse(null) : null;
        if (ready != null) {
            readyRig = rigFor(ready);
        }
    }

    private static WeaponRig rigFor(CombatPose pose) {
        return pose.rig(Config.Client.WEAPON_GRIP_PITCH.get(), Config.Client.BLADE_TWIST.get(),
                WeaponRig.TwistAxis.valueOf(Config.Client.BLADE_TWIST_AXIS.get().name()));
    }

    /**
     * The local player in first person, holding a weapon, with nothing in the offhand (vanilla keeps drawing a shield or
     * food) and no item in use: show the arms in a ready stance rather than vanilla's hand, so attacks don't cut.
     */
    private boolean wantsReadyStance() {
        Minecraft mc = Minecraft.getInstance();
        return Config.Client.FIRST_PERSON_READY_STANCE.get() && player == mc.player && mc.options.getCameraType().isFirstPerson()
                && player.getOffhandItem().isEmpty() && !player.isUsingItem() && !player.isSpectator()
                && ClientInput.holdsWeapon(mc.player);
    }

    @Override
    public PlayerAnimBone get3DTransform(@NotNull PlayerAnimBone bone) {
        // In first person the ready stance stands in for vanilla's arm pose: attacks blend from it and back to it.
        boolean readyBase = ready != null && FirstPersonMode.isFirstPersonPass();
        if (pose == null && !readyBase) {
            return bone;
        }
        if (readyBase) {
            applyReady(bone);
        }
        if (pose == null) {
            return bone;
        }
        float w = (float) pose.weight();
        switch (bone.getName()) {
            case "right_arm" -> {
                firstPersonOffset(bone, readyBase ? 0 : w); // the ready stance already moved it
                bone.setBend(pose.offset("rightArmBend", 0)); // kept for later: PAL on 1.21.1 doesn't draw bends
                if (pose.kick()) {
                    add(bone, "rightArm");
                } else {
                    bone.updateRotation(
                            Mth.lerp(w, bone.getRotX(), (float) rig.arm()[0]) + pose.offset("rightArm", 0),
                            Mth.lerp(w, bone.getRotY(), (float) rig.arm()[1]) + pose.offset("rightArm", 1),
                            Mth.lerp(w, bone.getRotZ(), (float) rig.arm()[2]) + pose.offset("rightArm", 2));
                }
            }
            case "left_arm" -> {
                firstPersonOffset(bone, readyBase ? 0 : w);
                bone.setBend(pose.offset("leftArmBend", 0));
                if (pose.twoHanded() && !pose.kick()) {
                    // Off hand on the grip: aim the arm at it, sliding the shoulder across if the arm is too short.
                    double[] grip = rig.offArm();
                    bone.updateRotation(Mth.lerp(w, bone.getRotX(), (float) grip[0]), Mth.lerp(w, bone.getRotY(), (float) grip[1]),
                            Mth.lerp(w, bone.getRotZ(), (float) grip[2]));
                    // PAL bone positions: X and Z as the model part, Y flipped. Over a ready stance's shoulder: blend.
                    Vec shoulder = rig.offShoulder().subtract(readyBase && ready.twoHanded() ? readyRig.offShoulder() : Vec.ZERO);
                    bone.setPosX(bone.getPosX() + (float) shoulder.x() * w);
                    bone.setPosY(bone.getPosY() - (float) shoulder.y() * w);
                    bone.setPosZ(bone.getPosZ() + (float) shoulder.z() * w);
                } else {
                    add(bone, "leftArm");
                }
            }
            // Turn the held weapon so its blade lies along the arm (along the arc), then roll it so the edge leads.
            case "right_item" -> {
                if (readyBase) {
                    itemRotation(bone, readyRig, -w); // take the ready stance's turn back out as the attack takes over
                }
                if (!pose.kick()) {
                    itemRotation(bone, rig, w);
                }
            }
            case "body" -> wholeBody(bone);
            case "torso" -> add(bone, "torso");
            case "head" -> add(bone, "head");
            case "right_leg" -> {
                add(bone, "rightLeg");
                hipsLag(bone);
            }
            case "left_leg" -> {
                add(bone, "leftLeg");
                hipsLag(bone);
            }
            default -> {
            }
        }
        return bone;
    }

    private static void firstPersonOffset(PlayerAnimBone bone, float w) {
        if (FirstPersonMode.isFirstPersonPass()) {
            bone.setPosZ(bone.getPosZ() - FIRST_PERSON_FORWARD * w); // model forward is -Z
            bone.setPosY(bone.getPosY() - FIRST_PERSON_DOWN * w); // PAL's Y is up
        }
    }

    /** Additive clip offset for a model part (the clips keep their playerAnimator-era camelCase names). */
    private void add(PlayerAnimBone bone, String clipPart) {
        bone.addRot(pose.offset(clipPart, 0), pose.offset(clipPart, 1), pose.offset(clipPart, 2));
    }

    /**
     * The whole model turns with the shoulders; turning the legs part of the way back keeps the feet pointing nearer
     * the target, so the twist reads as shoulders over hips rather than the fighter pivoting on the spot.
     */
    private void hipsLag(PlayerAnimBone bone) {
        bone.addRot(0, HIP_LAG * pose.offset("body", 1), 0);
    }

    /**
     * Whole-model lean. playerAnimator rotated it Z·Y·X as given; PAL negates X and Y first, so the clip's X and Y go in
     * negated to lean the same way.
     */
    private void wholeBody(PlayerAnimBone bone) {
        bone.addRot(-pose.offset("body", 0), -pose.offset("body", 1), pose.offset("body", 2));
    }

    /**
     * Held weapon, turned in the hand so the blade lies on the arc and its edge leads (solved by {@link WeaponRig} in
     * the hand frame playerAnimator used: Z, then Y, then X). PAL applies the item bone as Z(-rotY), Y(-rotZ), X(-rotX),
     * so each hand-frame angle goes into the matching PAL channel negated.
     */
    private static void itemRotation(PlayerAnimBone bone, WeaponRig rig, float w) {
        float x = (float) rig.item()[0] * w;
        float y = (float) rig.item()[1] * w;
        float z = (float) rig.item()[2] * w;
        bone.addRot(-x, -z, -y);
    }

    /** The first-person ready stance, in place of vanilla's arm pose: weapon arm, grip hand and the turned weapon. */
    private void applyReady(PlayerAnimBone bone) {
        switch (bone.getName()) {
            case "right_arm" -> {
                firstPersonOffset(bone, 1);
                bone.updateRotation((float) readyRig.arm()[0], (float) readyRig.arm()[1], (float) readyRig.arm()[2]);
            }
            case "left_arm" -> {
                firstPersonOffset(bone, 1);
                if (ready.twoHanded()) {
                    double[] grip = readyRig.offArm();
                    bone.updateRotation((float) grip[0], (float) grip[1], (float) grip[2]);
                    Vec shoulder = readyRig.offShoulder();
                    bone.setPosX(bone.getPosX() + (float) shoulder.x());
                    bone.setPosY(bone.getPosY() - (float) shoulder.y());
                    bone.setPosZ(bone.getPosZ() + (float) shoulder.z());
                }
            }
            case "right_item" -> itemRotation(bone, readyRig, 1);
            default -> {
            }
        }
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode() {
        return pose != null || ready != null ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
    }

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration() {
        boolean twoHanded = pose != null ? pose.twoHanded() : ready != null && ready.twoHanded();
        return twoHanded ? FIRST_PERSON_TWO_HANDS : FIRST_PERSON_ONE_HAND;
    }
}
