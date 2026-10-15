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
    /** Centre of the support hand's last two model pixels, instead of its fingertip edge. */
    public static final double SUPPORT_GRIP_DISTANCE = 9;
    /** Furthest the off shoulder slides toward the grip (pixels). */
    public static final double MAX_SHOULDER_REACH = 4;

    public enum TwistAxis { X, Y, Z }

    /** Verified rendered attachment, in model pixels and the canonical right hand's item frame. */
    public record GripAttachment(Vec socket, Vec itemGrip, Vec itemAxis, Vec shoulderShift,
                                 Vec physicalRootShift, boolean slimHand, GripContact mainContact) {
        /** Source-compatible support-only attachment; legacy callers do not acquire a main-mesh constraint. */
        public GripAttachment(Vec socket, Vec itemGrip, Vec itemAxis, Vec shoulderShift) {
            this(socket, itemGrip, itemAxis, shoulderShift, Vec.ZERO, false, null);
        }

        public GripAttachment {
            for (Vec value : new Vec[]{socket, itemGrip, itemAxis, shoulderShift, physicalRootShift}) {
                java.util.Objects.requireNonNull(value);
                if (!Double.isFinite(value.x()) || !Double.isFinite(value.y()) || !Double.isFinite(value.z()))
                    throw new IllegalArgumentException("grip attachment must be finite");
            }
            if (socket.length() < 1e-9 || itemAxis.length() < 1e-9 || !Double.isFinite(itemAxis.length()))
                throw new IllegalArgumentException("grip attachment needs a socket and shaft direction");
            itemAxis = itemAxis.scale(1 / itemAxis.length());
        }

        /** Account for the view-anchored shoulder pivots already applied to a first-person attack. */
        public GripAttachment withViewShoulders(double viewYaw, double viewPitch, boolean leftHanded) {
            Vec mainDelta = viewShoulderOffset(viewYaw, viewPitch, leftHanded);
            Vec offDelta = viewShoulderOffset(viewYaw, viewPitch, !leftHanded);
            Vec difference = mainDelta.subtract(offDelta);
            if (leftHanded) difference = new Vec(-difference.x(), difference.y(), difference.z());
            return new GripAttachment(socket, itemGrip, itemAxis, shoulderShift.add(difference), physicalRootShift,
                    slimHand, mainContact);
        }
    }

    /** A verified socket/mesh anchor refines the two-handed target without changing the blade orientation. */
    public static WeaponRig solve(double bladeYaw, double bladePitch, double gripDegrees, double twistDegrees, TwistAxis twistAxis,
                                  double[] body, double relax, double gripGap, boolean twoHanded, GripAttachment attachment) {
        WeaponRig base = solve(bladeYaw, bladePitch, gripDegrees, twistDegrees, twistAxis, body, relax, gripGap, twoHanded);
        if (!twoHanded || attachment == null) return base;
        Mat3 arm = Mat3.zyx(base.arm[0], base.arm[1], base.arm[2]);
        Mat3 blade = arm.mul(HAND).mul(Mat3.zyx(base.item[0], base.item[1], base.item[2]));
        Vec shaft = blade.apply(attachment.itemAxis);
        Vec constant = attachment.shoulderShift.add(blade.apply(attachment.itemGrip)).subtract(shaft.scale(gripGap));
        Vec centre = LEFT_SHOULDER.subtract(RIGHT_SHOULDER).subtract(constant);
        double radius = attachment.socket.length(); // socket offset, not rendered arm length
        Vec direction = arm.apply(attachment.socket).scale(1 / radius);
        Vec constrained = reachableSocket(direction, centre, radius);
        // Unsupported attachment geometry cannot stretch either arm or escape into the render loop.
        if (constrained == null) return base;
        arm = rotationBetween(direction, constrained).mul(arm);
        Mat3 item = HAND.transpose().mul(arm.transpose()).mul(blade);
        Vec grip = RIGHT_SHOULDER.add(arm.apply(attachment.socket)).add(constant);
        Vec toGrip = grip.subtract(LEFT_SHOULDER);
        double distance = toGrip.length();
        double travel = Math.max(0, Math.min(MAX_SHOULDER_REACH, distance - SUPPORT_GRIP_DISTANCE));
        Vec shoulder = distance < 1e-9 ? Vec.ZERO : toGrip.scale(travel / distance);
        if (attachment.mainContact() != null) {
            Vec minimum = new Vec(attachment.slimHand() ? -2 : -3, 8, -2);
            Vec maximum = new Vec(1, 10, 2);
            Mat3 corrected = preserveOrRollForMainContact(arm, blade, attachment, minimum, maximum);
            if (corrected != arm) {
                arm = corrected;
                item = HAND.transpose().mul(arm.transpose()).mul(blade);
            }
        }
        return new WeaponRig(arm.toZyx(), item.toZyx(), reach(LEFT_SHOULDER, grip), shoulder);
    }

    private static final double MAX_SOCKET_ROLL = Math.toRadians(20);
    private static final double SOCKET_ROLL_STEP = Math.toRadians(.1);
    private static final double CONTACT_INTERIOR_MARGIN = .001;

    static Mat3 preserveOrRollForMainContact(Mat3 arm, Mat3 blade, GripAttachment attachment, Vec minimum, Vec maximum) {
        // Preserve an already-contacting pose bit-for-bit, including valid contact outside the old point anchor.
        if (attachment.mainContact() == null || attachment.mainContact().intersects(arm, blade,
                attachment.physicalRootShift(), attachment.socket(), minimum, maximum, 0)) return arm;
        Mat3 rolled = solveSocketRoll(arm, blade, attachment, minimum, maximum);
        return rolled == null ? arm : rolled;
    }

    /** Rolls the arm around its own socket vector while counter-rotating the item to preserve the blade. */
    static Mat3 solveSocketRoll(Mat3 arm, Mat3 blade, GripAttachment attachment, Vec minimum, Vec maximum) {
        Vec axis = attachment.socket().scale(1 / attachment.socket().length());
        Mat3 best = null;
        double bestMagnitude = Double.POSITIVE_INFINITY;
        double previousMagnitude = 0;
        for (double magnitude = SOCKET_ROLL_STEP; magnitude <= MAX_SOCKET_ROLL + 1e-12; magnitude += SOCKET_ROLL_STEP) {
            for (double sign : new double[]{1, -1}) {
                Mat3 candidate = arm.mul(axisRotation(axis, magnitude * sign));
                if (!attachment.mainContact().intersects(candidate, blade, attachment.physicalRootShift(), attachment.socket(),
                        minimum, maximum, CONTACT_INTERIOR_MARGIN)) continue;
                double low = previousMagnitude, high = magnitude;
                for (int iteration = 0; iteration < 18; iteration++) {
                    double middle = (low + high) * .5;
                    Mat3 middleArm = arm.mul(axisRotation(axis, middle * sign));
                    if (attachment.mainContact().intersects(middleArm, blade, attachment.physicalRootShift(), attachment.socket(),
                            minimum, maximum, CONTACT_INTERIOR_MARGIN)) high = middle;
                    else low = middle;
                }
                Mat3 refined = arm.mul(axisRotation(axis, high * sign));
                if (high < bestMagnitude) { bestMagnitude = high; best = refined; }
            }
            if (best != null) return best;
            previousMagnitude = magnitude;
        }
        return null;
    }

    private static Mat3 axisRotation(Vec axis, double angle) {
        double x = axis.x(), y = axis.y(), z = axis.z();
        double c = Math.cos(angle), s = Math.sin(angle), t = 1 - c;
        return new Mat3(t*x*x+c, t*x*y-s*z, t*x*z+s*y,
                t*x*y+s*z, t*y*y+c, t*y*z-s*x,
                t*x*z-s*y, t*y*z+s*x, t*z*z+c);
    }

    /** Returns null only when the attachment's socket sphere cannot meet the bounded support shell. */
    private static Vec reachableSocket(Vec direction, Vec centre, double radius) {
        double distance = centre.length();
        double maximum = SUPPORT_GRIP_DISTANCE + MAX_SHOULDER_REACH;
        if (!Double.isFinite(radius) || !Double.isFinite(distance)) return null;
        if (distance < 1e-9)
            return radius >= SUPPORT_GRIP_DISTANCE && radius <= maximum ? direction : null;
        Vec axis = centre.scale(1 / distance);
        double lower = (radius * radius + distance * distance - maximum * maximum) / (2 * radius * distance);
        double upper = (radius * radius + distance * distance - SUPPORT_GRIP_DISTANCE * SUPPORT_GRIP_DISTANCE)
                / (2 * radius * distance);
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower > 1 + 1e-12 || upper < -1 - 1e-12)
            return null;
        lower = Math.max(-1, Math.min(1, lower));
        upper = Math.max(-1, Math.min(1, upper));
        if (lower > upper) return null;
        double dot = direction.dot(axis);
        if (dot >= lower && dot <= upper) return direction;
        double projected = Math.max(lower, Math.min(upper, dot));
        Vec perpendicular = direction.subtract(axis.scale(dot));
        if (perpendicular.length() < 1e-9) {
            Vec preferred = Math.abs(axis.z()) < .9 ? new Vec(0,0,-1) : new Vec(0,1,0);
            perpendicular = preferred.subtract(axis.scale(preferred.dot(axis)));
        }
        return axis.scale(projected).add(perpendicular.scale(Math.sqrt(Math.max(0,1-projected*projected)) / perpendicular.length()));
    }

    /** Shortest rotation of the entire arm; reducing this to yaw/pitch would lose the socket's lateral offset. */
    private static Mat3 rotationBetween(Vec from, Vec to) {
        double cosine = Math.max(-1, Math.min(1, from.dot(to)));
        Vec cross = from.cross(to);
        double sine = cross.length();
        if (sine < 1e-12) {
            if (cosine >= 0) return Mat3.IDENTITY;
            Vec preferred = Math.abs(from.z()) < .9 ? new Vec(0,0,1) : new Vec(0,1,0);
            Vec axis = from.cross(preferred); axis = axis.scale(1 / axis.length());
            double x=axis.x(), y=axis.y(), z=axis.z();
            return new Mat3(2*x*x-1,2*x*y,2*x*z,2*y*x,2*y*y-1,2*y*z,2*z*x,2*z*y,2*z*z-1);
        }
        // Axis-normalized Rodrigues stays finite even if cosine rounds to -1 while the cross product is nonzero.
        Vec axis = cross.scale(1 / sine);
        double x=axis.x(), y=axis.y(), z=axis.z(), complement=1-cosine;
        return new Mat3(cosine+x*x*complement,x*y*complement-z*sine,x*z*complement+y*sine,
                y*x*complement+z*sine,cosine+y*y*complement,y*z*complement-x*sine,
                z*x*complement-y*sine,z*y*complement+x*sine,cosine+z*z*complement);
    }

    /** Reflect a canonical right-handed solve across the model's X plane. */
    public WeaponRig mirrored() {
        return new WeaponRig(mirrorAngles(arm), mirrorAngles(item), mirrorAngles(offArm),
                new Vec(-offShoulder.x(), offShoulder.y(), offShoulder.z()));
    }

    public static double[] mirrorAngles(double[] angles) {
        return new double[]{angles[0], -angles[1], -angles[2]};
    }

    /** Convert a view-space ready rig into body space without moving its wrist home toward the body's heading. */
    public WeaponRig fromView(double viewYaw, double viewPitch) {
        Mat3 camera = cameraRotation(viewYaw, viewPitch);
        return new WeaponRig(camera.mul(Mat3.zyx(arm[0],arm[1],arm[2])).toZyx(), item.clone(),
                camera.mul(Mat3.zyx(offArm[0],offArm[1],offArm[2])).toZyx(), camera.apply(offShoulder));
    }

    public static Mat3 cameraRotation(double viewYaw, double viewPitch) {
        return Mat3.rotY(Math.toRadians(viewYaw)).mul(Mat3.rotX(Math.toRadians(viewPitch)));
    }

    /** Shoulder translation needed when a first-person hand is anchored to the view instead of the body's yaw. */
    public static Vec viewShoulderOffset(double viewYaw, double viewPitch, boolean left) {
        Vec shoulder = left ? LEFT_SHOULDER : RIGHT_SHOULDER;
        return cameraRotation(viewYaw,viewPitch).apply(shoulder).subtract(shoulder);
    }

    /** First-person carried item: keep the hand forward of the camera as the fighter's body twists beneath it. */
    public static double[] carryArm(double viewYaw, double viewPitch, double[] body, boolean left) {
        return carryArm(viewYaw, viewPitch, body, left, 0, 0);
    }

    /** Small continuous breathing motion, fading to the stable carry solve as the combat pose takes ownership. */
    public static double[] carryArm(double viewYaw, double viewPitch, double[] body, boolean left,
                                    double ageTicks, double idleWeight) {
        Mat3 camera = Mat3.rotY(Math.toRadians(viewYaw)).mul(Mat3.rotX(Math.toRadians(viewPitch)));
        double breath = Math.sin(ageTicks * 0.075) * Math.max(0, Math.min(1, idleWeight));
        Mat3 holding = Mat3.zyx(-1.1 + breath * 0.015, 0, (left ? -1 : 1) * (0.1 + breath * 0.006));
        return WeaponRig.bodyRotation(body).transpose().mul(camera).mul(holding).toZyx();
    }

    /** Raise a consumable below the eye line (the item extends above the grip), without changing the weapon solve. */
    public static double[] consumeArm(double viewYaw, double viewPitch, double[] body, boolean left, double ageTicks) {
        Vec mouth = new Vec(1.5, 2 + 0.25 * Math.sin(ageTicks * Math.PI / 2), -9);
        double[] angles = reach(LEFT_SHOULDER, mouth);
        if (!left) angles = mirrorAngles(angles);
        Mat3 camera = Mat3.rotY(Math.toRadians(viewYaw)).mul(Mat3.rotX(Math.toRadians(viewPitch)));
        return bodyRotation(body).transpose().mul(camera).mul(Mat3.zyx(angles[0], angles[1], angles[2])).toZyx();
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
        return solve(bladeYaw,bladePitch,gripDegrees,twistDegrees,twistAxis,body,relax,gripGap,false);
    }

    /** Two-handed solves also keep the support target inside the rigid arm's bounded reach. */
    public static WeaponRig solve(double bladeYaw, double bladePitch, double gripDegrees, double twistDegrees, TwistAxis twistAxis,
                                  double[] body, double relax, double gripGap, boolean twoHanded) {
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
        Mat3 bodyRot = bodyRotation(body);
        if (twoHanded) {
            Vec constrained = reachableGripHand(bodyRot.transpose().apply(handDir), bodyRot.transpose().apply(bladeDir), gripGap);
            handDir = bodyRot.apply(constrained);
        }
        double handYaw = Math.toDegrees(Math.atan2(-handDir.x(), -handDir.z()));
        double handPitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, handDir.y()))));
        Mat3 armWorld = armToward(handYaw, handPitch);

        Mat3 arm = bodyRot.transpose().mul(armWorld);
        // armWorld · HAND · item = blade  →  item = HANDᵀ · armWorldᵀ · blade
        Mat3 item = HAND.transpose().mul(armWorld.transpose()).mul(blade);
        // Off hand: on the grip behind the weapon hand, in the body's own (rotated) frame like the arms.
        Vec bladeInBody = bodyRot.transpose().apply(bladeDir);
        Vec weaponHand = RIGHT_SHOULDER.add(arm.apply(new Vec(0, HAND_DISTANCE, 0)));
        Vec grip = weaponHand.subtract(bladeInBody.scale(gripGap));
        Vec toGrip = grip.subtract(LEFT_SHOULDER);
        double supportDistance = twoHanded ? SUPPORT_GRIP_DISTANCE : HAND_DISTANCE;
        double shortBy = toGrip.length() - supportDistance;
        Vec shoulder = shortBy > 0 ? toGrip.scale(Math.min(shortBy, MAX_SHOULDER_REACH) / toGrip.length()) : Vec.ZERO;
        return new WeaponRig(arm.toZyx(), item.toZyx(), reach(LEFT_SHOULDER, grip), shoulder);
    }

    /** Closest main-arm direction whose support target needs 0..MAX_SHOULDER_REACH of shoulder travel. */
    private static Vec reachableGripHand(Vec desired, Vec blade, double gap) {
        Vec centre = LEFT_SHOULDER.add(blade.scale(gap)).subtract(RIGHT_SHOULDER);
        double distance = centre.length();
        if (distance < 1e-9) return desired;
        Vec axis = centre.scale(1 / distance);
        double arm = HAND_DISTANCE, support = SUPPORT_GRIP_DISTANCE, maximum = support + MAX_SHOULDER_REACH;
        double lower = Math.max(-1, (arm * arm + distance * distance - maximum * maximum) / (2 * arm * distance));
        double upper = Math.min(1, (arm * arm + distance * distance - support * support) / (2 * arm * distance));
        double projection = desired.dot(axis);
        if (projection >= lower && projection <= upper) return desired;
        double target = Math.max(lower, Math.min(upper, projection));
        Vec perpendicular = desired.subtract(axis.scale(projection));
        if (perpendicular.length() < 1e-9) {
            Vec preferred = new Vec(0, 0, -1);
            perpendicular = preferred.subtract(axis.scale(preferred.dot(axis)));
            if (perpendicular.length() < 1e-9) {
                preferred = new Vec(0, 1, 0);
                perpendicular = preferred.subtract(axis.scale(preferred.dot(axis)));
            }
        }
        return axis.scale(target).add(perpendicular.scale(Math.sqrt(Math.max(0, 1 - target * target)) / perpendicular.length()));
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
        return Math.max(ArmAim.MIN_POSE_PITCH, Math.min(90, pitch));
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
