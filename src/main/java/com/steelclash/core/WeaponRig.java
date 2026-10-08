package com.steelclash.core;

/**
 * Solves the weapon arm and the held weapon for one frame so that the <em>blade</em> lies exactly where the straight
 * arc-locked arm would put it (see {@link ArmAim#aimArm}), while the arm itself is free to do something more natural:
 * the hand stays nearer the body (the wrist is cocked instead of the arm and blade forming one straight spear), and
 * the whole body may twist and lean under it (hip and shoulder rotation from the pose clips).
 * <p>
 * Frames: model space (+Y down, -Z forward, -X the right side). An arm's rotation is a model part's
 * {@code Rz·Ry·Rx}. Vanilla turns the held item in the hand by {@link #HAND} ({@code ItemInHandLayer}: X -90°, Y 180°);
 * the in-hand rotation solved here comes after that, as {@code Rz·Ry·Rx} too ("hand frame", where the arm's length is
 * Z, so Z rolls the blade). The whole-body rotation is the pose clip's {@code body} offset, which the renderer applies
 * in entity space ({@code Rz·Ry·Rx} of the clip angles), before the model's flip of X and Y; in model space that's
 * {@code Rz(z)·Ry(-y)·Rx(-x)} ({@link #bodyRotation}).
 *
 * @param arm    weapon arm rotation {x, y, z}, radians, relative to the (possibly rotated) body
 * @param item   held weapon rotation in the hand frame {x, y, z}, radians
 * @param offArm      off arm rotation reaching for the grip just behind the weapon hand (two-handed weapons), radians
 * @param offShoulder how far the off shoulder moves toward the grip when the arm alone is too short (pixels, model
 *                    space): the shoulder reaching across the chest
 */
public record WeaponRig(double[] arm, double[] item, double[] offArm, Vec offShoulder) {
    static final Mat3 HAND = Mat3.rotX(Math.toRadians(-90)).mul(Mat3.rotY(Math.toRadians(180)));

    /** Where a relaxed hand drifts to: in front of the chest, a little low. Degrees, view-relative. */
    public static final double HOME_YAW = 0;
    public static final double HOME_PITCH = 25;

    /** Shoulder pivots of the player model (pixels, model space) and how far down the arm the hand grips. */
    public static final Vec RIGHT_SHOULDER = new Vec(-5, 2, 0);
    public static final Vec LEFT_SHOULDER = new Vec(5, 2, 0);
    public static final double HAND_DISTANCE = 10;
    /** Furthest the off shoulder slides toward the grip (pixels). */
    public static final double MAX_SHOULDER_REACH = 4;

    public enum TwistAxis { X, Y, Z }

    /** Reflect a canonical right-handed solve across the model's X plane. */
    public WeaponRig mirrored() {
        return new WeaponRig(mirrorAngles(arm), mirrorAngles(item), mirrorAngles(offArm),
                new Vec(-offShoulder.x(), offShoulder.y(), offShoulder.z()));
    }

    public static double[] mirrorAngles(double[] angles) {
        return new double[]{angles[0], -angles[1], -angles[2]};
    }

    /**
     * @param bladeYaw   blade direction yaw relative to the body, degrees (positive = the body's right)
     * @param bladePitch blade direction pitch, degrees (positive = down)
     * @param gripDegrees in-hand pitch that lays a held weapon's blade along the arm (config, about -80)
     * @param twistDegrees roll of the blade about its length (edge leading)
     * @param twistAxis  hand-frame axis the twist turns about
     * @param body       whole-body clip rotation {x, y, z}, degrees, as the clip gives it (already weighted)
     * @param relax      0 = arm straight along the blade (the old look), 1 = hand all the way at {@link #HOME_PITCH}
     */
    public static WeaponRig solve(double bladeYaw, double bladePitch, double gripDegrees, double twistDegrees, TwistAxis twistAxis,
                                  double[] body, double relax) {
        return solve(bladeYaw, bladePitch, gripDegrees, twistDegrees, twistAxis, body, relax, AnimationSet.DEFAULT_GRIP_GAP);
    }

    /** @param gripGap pixels between the hands on a two-handed grip, the off hand toward the pommel */
    public static WeaponRig solve(double bladeYaw, double bladePitch, double gripDegrees, double twistDegrees, TwistAxis twistAxis,
                                  double[] body, double relax, double gripGap) {
        Mat3 straight = armToward(bladeYaw, bladePitch);
        double gx = Math.toRadians(gripDegrees), gy = 0, gz = 0;
        double twist = Math.toRadians(twistDegrees);
        switch (twistAxis) {
            case X -> gx += twist;
            case Y -> gy += twist;
            case Z -> gz += twist;
        }
        Mat3 inHand = Mat3.zyx(gx, gy, gz);
        // The blade's model-space orientation with the straight arm: that's what must not change.
        Mat3 blade = straight.mul(HAND).mul(inHand);

        Vec bladeDir = ArmAim.modelDirection(bladeYaw, clampPitch(bladePitch));
        Vec handDir = towards(bladeDir, ArmAim.modelDirection(HOME_YAW, HOME_PITCH), relax);
        double handYaw = Math.toDegrees(Math.atan2(-handDir.x(), -handDir.z()));
        double handPitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, handDir.y()))));
        Mat3 armWorld = armToward(handYaw, handPitch);

        Mat3 bodyRot = bodyRotation(body);
        Mat3 arm = bodyRot.transpose().mul(armWorld);
        // armWorld · HAND · item = blade  →  item = HANDᵀ · armWorldᵀ · blade
        Mat3 item = HAND.transpose().mul(armWorld.transpose()).mul(blade);
        // Off hand: on the grip behind the weapon hand, in the body's own (rotated) frame like the arms.
        Vec bladeInBody = bodyRot.transpose().apply(bladeDir);
        Vec weaponHand = RIGHT_SHOULDER.add(arm.apply(new Vec(0, HAND_DISTANCE, 0)));
        Vec grip = weaponHand.subtract(bladeInBody.scale(gripGap));
        Vec toGrip = grip.subtract(LEFT_SHOULDER);
        double shortBy = toGrip.length() - HAND_DISTANCE;
        Vec shoulder = shortBy > 0 ? toGrip.scale(Math.min(shortBy, MAX_SHOULDER_REACH) / toGrip.length()) : Vec.ZERO;
        return new WeaponRig(arm.toZyx(), item.toZyx(), reach(LEFT_SHOULDER, grip), shoulder);
    }

    /** Arm rotation (no roll) pointing an arm from its shoulder at a point (pixels, model space). */
    public static double[] reach(Vec shoulder, Vec target) {
        Vec d = target.subtract(shoulder);
        double len = d.length();
        if (len < 1e-6) {
            return new double[]{0, 0, 0};
        }
        d = d.scale(1 / len);
        // armDirection(x, y) = (sin x sin y, cos x, sin x cos y), with x in [-180°, 0]
        double x = -Math.acos(Math.max(-1, Math.min(1, d.y())));
        double y = Math.abs(Math.sin(x)) < 1e-9 ? 0 : Math.atan2(-d.x(), -d.z());
        return new double[]{x, y, 0};
    }

    /** Model-space rotation of the whole body for a clip's {@code body} offset (degrees). */
    public static Mat3 bodyRotation(double[] body) {
        return Mat3.rotZ(Math.toRadians(body[2])).mul(Mat3.rotY(-Math.toRadians(body[1]))).mul(Mat3.rotX(-Math.toRadians(body[0])));
    }

    /** Arm rotation (no roll) pointing the arm along (yaw, pitch), as {@link ArmAim#aimArm}. */
    static Mat3 armToward(double yaw, double pitch) {
        double[] a = ArmAim.aimArm(yaw, pitch);
        return Mat3.zyx(a[0], a[1], a[2]);
    }

    /** Model-space direction of the held blade for a rig (with the body rotation applied), for tests and debugging. */
    public Mat3 bladeOrientation(double[] body) {
        return bodyRotation(body).mul(Mat3.zyx(arm[0], arm[1], arm[2])).mul(HAND).mul(Mat3.zyx(item[0], item[1], item[2]));
    }

    private static double clampPitch(double pitch) {
        return Math.max(-90, Math.min(90, pitch));
    }

    /** Turns unit vector {@code from} a fraction of the way toward {@code to} (spherical, robust when they're opposite). */
    static Vec towards(Vec from, Vec to, double fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        double cos = Math.max(-1, Math.min(1, from.dot(to)));
        double angle = Math.acos(cos);
        if (angle < 1e-6 || f == 0) {
            return from;
        }
        Vec ortho = to.subtract(from.scale(cos));
        double len = ortho.length();
        if (len < 1e-6) { // opposite: no preferred way round, so stay put
            return from;
        }
        ortho = ortho.scale(1 / len);
        double a = angle * f;
        return from.scale(Math.cos(a)).add(ortho.scale(Math.sin(a)));
    }
}
