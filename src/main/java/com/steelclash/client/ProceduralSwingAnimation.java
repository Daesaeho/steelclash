package com.steelclash.client;

import com.steelclash.Config;
import com.steelclash.SteelClash;
import com.steelclash.client.anim.CombatPose;
import com.steelclash.client.anim.CombatPresentation;
import com.steelclash.client.dev.PoseSheet;
import com.steelclash.client.dev.LiveCapture;
import com.steelclash.combat.Downed;
import com.steelclash.core.RotationBlend;
import com.steelclash.core.AttackType;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
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
    private static final FirstPersonConfiguration[] FIRST_PERSON = firstPersonConfigurations();
    private static final double[] NO_TURN = {0, 0, 0};
    /** First-person sideways travel of the arms at full spread (width 2) and the swing at 90 degrees, pixels. */
    private static final double SWEEP_PIXELS = 12;
    /** First-person rise of the arms per degree of swing lift, pixels. */
    private static final double LIFT_PIXELS_PER_DEGREE = 0.05;

    private final AbstractClientPlayer player;
    @Nullable
    private CombatPose pose;
    private WeaponRig rig = WeaponRig.solve(0, 0, 0, 0, WeaponRig.TwistAxis.Z, new double[3], 0);
    /** First person only: the ready stance attacks blend from and back to, instead of vanilla's arm pose. */
    @Nullable
    private CombatPose ready;
    private WeaponRig readyRig = rig;
    /** First person only: the swing spread across the screen ({@link CombatPose#spreadForView}); null otherwise. */
    @Nullable
    private WeaponRig viewRig;
    /** Occupied non-shield hand, countering the body turn rather than following the weapon. */
    private double[] carriedArm = NO_TURN;
    private double[] carriedReference = NO_TURN;
    /** PAL chooses its camera pass before setupAnim; keep ownership stable until its next tick. */
    private boolean firstPersonOwned;

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
        return player.isAlive() && !Downed.isDowned(player) && (firstPersonOwned || CombatPresentation.hasTail(player)
                || CombatPose.isAvailable(player) || wantsReadyStance());
    }

    @Override
    public void tick(AnimationData state) {
        firstPersonOwned = player.isAlive() && !Downed.isDowned(player) && (CombatPose.isAvailable(player)
                || CombatPresentation.get(player, 0).isPresent() || wantsReadyStance());
    }

    @Override
    public void setupAnim(AnimationData state) {
        pose = CombatPresentation.get(player, state.getPartialTick()).orElse(null);
        if (pose != null && !pose.kick()) {
            rig = rigFor(pose);
        }
        Minecraft mc = Minecraft.getInstance();
        viewRig = pose != null && !pose.kick() && player == mc.player && mc.options.getCameraType().isFirstPerson()
                ? rigFor(pose.spreadForView(Config.Client.FIRST_PERSON_SWING_WIDTH.get(), Config.Client.FIRST_PERSON_SWING_LIFT.get(),
                        player.getViewXRot(state.getPartialTick())))
                : null;
        ready = wantsReadyStance() ? CombatPose.ready(player, state.getPartialTick()).orElse(null) : null;
        if (ready != null) {
            readyRig = rigFor(ready);
        }
        CombatPose carried = pose != null ? pose : ready;
        if (carried != null && mc.options.getCameraType().isFirstPerson()
                && !player.getOffhandItem().isEmpty() && !player.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK)) {
            carriedArm = WeaponRig.carryArm(carried.aimYaw() - carried.relativeYaw(), player.getViewXRot(state.getPartialTick()),
                    carried.bodyDegrees(), !carried.leftHanded(), player.tickCount + (double) state.getPartialTick(),
                    pose == null ? 1 : 1 - pose.weight());
            if (LiveCapture.recording()) {
                carriedReference = WeaponRig.carryArm(carried.aimYaw() - carried.relativeYaw(),
                        player.getViewXRot(state.getPartialTick()), carried.bodyDegrees(), !carried.leftHanded());
            }
        }
        PoseSheet.recordRenderedPose(player, pose);
    }

    private static WeaponRig rigFor(CombatPose pose) {
        return pose.rig(Config.Client.WEAPON_GRIP_PITCH.get(), Config.Client.BLADE_TWIST.get(),
                WeaponRig.TwistAxis.valueOf(Config.Client.BLADE_TWIST_AXIS.get().name()));
    }

    /**
     * The local player in first person holding a weapon, with no shield or item in use: show a ready stance so
     * attacks do not switch hand renderers. A carried non-shield item uses its own holding arm.
     */
    private boolean wantsReadyStance() {
        Minecraft mc = Minecraft.getInstance();
        return !PoseSheet.photographingMob() && Config.Client.FIRST_PERSON_READY_STANCE.get()
                && player == mc.player && mc.options.getCameraType().isFirstPerson()
                && player.isAlive() && !Downed.isDowned(player)
                && !player.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK)
                && !player.isUsingItem() && !player.isSpectator()
                && ClientInput.holdsWeapon(mc.player);
    }

    @Override
    public PlayerAnimBone get3DTransform(@NotNull PlayerAnimBone bone) {
        LiveCapture.recordRenderedPose(player, pose, ready != null);
        // In first person the ready stance stands in for vanilla's arm pose: attacks blend from it and back to it.
        boolean readyBase = ready != null && FirstPersonMode.isFirstPersonPass();
        if (readyBase) {
            applyReady(bone);
        }
        if (pose == null) {
            return bone;
        }
        float w = (float) pose.weight();
        WeaponRig rig = viewRig != null && FirstPersonMode.isFirstPersonPass() ? viewRig : this.rig;
        switch (bone.getName()) {
            case "right_arm", "left_arm" -> {
                boolean left = bone.getName().equals("left_arm");
                boolean main = left == pose.leftHanded();
                String channel = left ? "leftArm" : "rightArm";
                if (FirstPersonMode.isFirstPersonPass() && !main && !player.getOffhandItem().isEmpty()
                        && !player.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK)
                        && pose.phase().isAttack() && !pose.kick()) {
                    // PAL replaces both vanilla hands. The free-arm clip and weapon translation can otherwise
                    // carry a torch below the camera. Shields, bash/kick and guard keep their own choreography.
                    // Keep carrying the item as the weapon recovers; PAL handles the vanilla/model hand transition.
                    blendRotation(bone, carriedArm, 1);
                    LiveCapture.recordCarriedRotation(player, bone.getRotX(), bone.getRotY(), bone.getRotZ(), carriedReference);
                    break;
                }
                firstPersonOffset(bone, pose, readyBase ? 0 : w, w); // over the ready stance, it already moved them
                if (viewRig != null && FirstPersonMode.isFirstPersonPass()) {
                    viewSweep(bone, w);
                }
                bone.setBend(pose.offset(channel + "Bend", 0)); // PAL 1.21.1 has no bend drawing backend
                if (main && !pose.kick()) {
                    blendRotation(bone, rig.arm(), w);
                    add(bone, channel);
                } else if (!main && pose.twoHanded() && !pose.kick()) {
                    // Off hand on the grip: aim the arm at it, sliding the shoulder across if the arm is too short.
                    double[] grip = rig.offArm();
                    blendRotation(bone, grip, w);
                    // PAL bone positions: X and Z as the model part, Y flipped. Over a ready stance's shoulder: blend.
                    Vec shoulder = rig.offShoulder().subtract(readyBase && ready.twoHanded() ? readyRig.offShoulder() : Vec.ZERO);
                    bone.setPosX(bone.getPosX() + (float) shoulder.x() * w);
                    bone.setPosY(bone.getPosY() - (float) shoulder.y() * w);
                    bone.setPosZ(bone.getPosZ() + (float) shoulder.z() * w);
                } else {
                    add(bone, channel);
                }
            }
            // Turn the held weapon so its blade lies along the arm (along the arc), then roll it so the edge leads.
            case "right_item", "left_item" -> {
                boolean left = bone.getName().equals("left_item");
                if (left == pose.leftHanded() && !pose.kick()) {
                    itemRotation(bone, readyBase ? readyRig.item() : NO_TURN, rig.item(), w);
                } else if (readyBase && left == ready.leftHanded()) {
                    itemRotation(bone, NO_TURN, readyRig.item(), 1);
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

    /** First person: the arms forward and down, away from the camera, by {@code base}; a thrust's retraction by {@code w}. */
    private static void firstPersonOffset(PlayerAnimBone bone, CombatPose pose, float base, float w) {
        if (FirstPersonMode.isFirstPersonPass()) {
            double retraction = pose.type() == AttackType.STAB || pose.type() == AttackType.JAB || pose.type() == AttackType.SPECIAL
                    ? pose.firstPerson().retraction() * (1 - Math.max(0, Math.min(1, pose.extension()))) : 0;
            bone.setPosZ(bone.getPosZ() - (float) (pose.firstPerson().forward() * base - retraction * w));
            bone.setPosY(bone.getPosY() - (float) pose.firstPerson().down() * base); // PAL's Y is up
        }
    }

    /**
     * First person, with the swing spread across the view: both arms slide toward the side the weapon is on and rise with
     * the lift, so the hands travel across the screen with the blade instead of pivoting in one spot below the camera.
     */
    private void viewSweep(PlayerAnimBone bone, float w) {
        double side = Math.max(-1, Math.min(1, pose.relativeYaw() / 90)); // + = the fighter's right
        double width = Config.Client.FIRST_PERSON_SWING_WIDTH.get() - 1;
        bone.setPosX(bone.getPosX() - (float) (side * width * SWEEP_PIXELS * w)); // the model's right is -X
        bone.setPosY(bone.getPosY() + (float) (Config.Client.FIRST_PERSON_SWING_LIFT.get() * LIFT_PIXELS_PER_DEGREE * w));
    }

    /** The first-person ready stance, in place of vanilla's arm pose: weapon arm, grip hand, and the weapon when idle. */
    private void applyReady(PlayerAnimBone bone) {
        String name = bone.getName();
        if (!name.equals("right_arm") && !name.equals("left_arm")) {
            if (pose == null && name.equals(ready.leftHanded() ? "left_item" : "right_item")) {
                itemRotation(bone, NO_TURN, readyRig.item(), 1);
            }
            return;
        }
        if (name.equals("left_arm") != ready.leftHanded() && !player.getOffhandItem().isEmpty()) {
            blendRotation(bone, carriedArm, 1);
            LiveCapture.recordCarriedRotation(player, bone.getRotX(), bone.getRotY(), bone.getRotZ(), carriedReference);
            return;
        }
        firstPersonOffset(bone, ready, 1, 1);
        if (name.equals("left_arm") == ready.leftHanded()) {
            bone.updateRotation((float) readyRig.arm()[0], (float) readyRig.arm()[1], (float) readyRig.arm()[2]);
        } else if (ready.twoHanded()) {
            double[] grip = readyRig.offArm();
            bone.updateRotation((float) grip[0], (float) grip[1], (float) grip[2]);
            Vec shoulder = readyRig.offShoulder();
            bone.setPosX(bone.getPosX() + (float) shoulder.x());
            bone.setPosY(bone.getPosY() - (float) shoulder.y());
            bone.setPosZ(bone.getPosZ() + (float) shoulder.z());
        }
    }

    private static void blendRotation(PlayerAnimBone bone, double[] target, float weight) {
        double[] rotation = RotationBlend.blend(bone.getRotX(), bone.getRotY(), bone.getRotZ(), target, weight);
        bone.updateRotation((float) rotation[0], (float) rotation[1], (float) rotation[2]);
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
    private static void itemRotation(PlayerAnimBone bone, double[] from, double[] to, float w) {
        double[] rotation = RotationBlend.blend(from[0], from[1], from[2], to, w);
        float x = (float) rotation[0];
        float y = (float) rotation[1];
        float z = (float) rotation[2];
        bone.addRot(-x, -z, -y);
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode() {
        return firstPersonOwned && player.isAlive() && !Downed.isDowned(player)
                ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
    }

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration() {
        boolean left = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
        boolean occupied = !player.getOffhandItem().isEmpty();
        boolean both = occupied || (pose != null ? pose.twoHanded() : ready != null && ready.twoHanded());
        int mask = (!left || both ? 1 : 0) | (left || both ? 2 : 0)
                | (!left || occupied ? 4 : 0) | (left || occupied ? 8 : 0);
        return FIRST_PERSON[mask];
    }

    private static FirstPersonConfiguration[] firstPersonConfigurations() {
        FirstPersonConfiguration[] configurations = new FirstPersonConfiguration[16];
        for (int mask = 0; mask < configurations.length; mask++) {
            configurations[mask] = new FirstPersonConfiguration((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0);
        }
        return configurations;
    }
}
