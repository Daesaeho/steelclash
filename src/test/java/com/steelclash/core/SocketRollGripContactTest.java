package com.steelclash.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SocketRollGripContactTest {
    private static final Vec SOCKET = new Vec(-1, 10, -2);
    private static final Vec PHYSICAL_SHIFT = new Vec(.5, 0, 0);
    private static final Vec MIN = new Vec(-2, 8, -2);
    private static final Vec MAX = new Vec(1, 10, 2);
    private static final double GAP = 2.5;

    private static final List<int[]> STOCK_GRIP_TEXELS = List.of(
            new int[]{4,10},new int[]{5,10},new int[]{3,11},new int[]{4,11},new int[]{5,11},
            new int[]{2,12},new int[]{3,12},new int[]{4,12},new int[]{0,13},new int[]{1,13},
            new int[]{2,13},new int[]{3,13},new int[]{0,14},new int[]{1,14},new int[]{2,14},
            new int[]{0,15},new int[]{1,15},new int[]{2,15});

    private record Pose(String name, Mat3 arm, Mat3 blade) {}

    private static GripContact stockContact() {
        Mat3 display = Mat3.rotY(Math.toRadians(-90)).mul(Mat3.rotZ(Math.toRadians(55)));
        Vec translation = new Vec(0, 4, .5);
        double scale = .85;
        Vec halfX = display.apply(new Vec(.5 * scale, 0, 0));
        Vec halfY = display.apply(new Vec(0, .5 * scale, 0));
        Vec halfZ = display.apply(new Vec(0, 0, .5 * scale));
        List<GripContact.Prism> texels = new ArrayList<>();
        for (int[] pixel : STOCK_GRIP_TEXELS) {
            Vec local = new Vec(pixel[0] - 7.5, 7.5 - pixel[1], 0).scale(scale);
            texels.add(new GripContact.Prism(translation.add(display.apply(local)), halfX, halfY, halfZ));
        }
        return new GripContact(texels);
    }

    private static WeaponRig.GripAttachment attachment(GripContact contact) {
        Mat3 display = Mat3.rotY(Math.toRadians(-90)).mul(Mat3.rotZ(Math.toRadians(55)));
        Vec grip = new Vec(0, 4, .5).add(display.apply(new Vec(-3.5, -3.5, 0).scale(.85)));
        Vec shaft = display.apply(new Vec(1, 1, 0).scale(1 / Math.sqrt(2)));
        return new WeaponRig.GripAttachment(SOCKET, grip, shaft, PHYSICAL_SHIFT, PHYSICAL_SHIFT, true, contact);
    }

    private static Pose regularBack() {
        return new Pose("slash_recovery_000_back",
                new Mat3(-.020568240916171446,.45216344309524287,-.8916979130814815,
                        .09559171762819735,.8886899750131636,.4484332189211605,
                        .9952081043613468,-.07601545263535317,-.061501869680011076),
                new Mat3(-.03777317274276171,.5922856376367285,.8048421651915358,
                        .9545497559403904,.25967644497664405,-.14629732519233976,
                        -.2956483567603357,.7627357782178217,-.5751749140664666));
    }

    private static Pose regularFront() {
        Pose pose = regularBack();
        return new Pose("slash_recovery_000_front", pose.arm(), pose.blade());
    }

    private static Pose heavyFirst() {
        return new Pose("slash_heavy_recovery_000_first",
                new Mat3(-.797051312171613,.15957302097653717,-.5824479862974302,
                        -.016428376753869628,.9583729128978588,.28504643176315325,
                        .6036880934743891,.2367653074250843,-.7612508620666136),
                new Mat3(-.04004779457317348,-.0018471046588698498,.9991960580157448,
                        .9836532993984579,.17563075767293976,.03974950995617965,
                        -.1755629822381478,.98445437942223,-.0052167139119850756));
    }

    private static Pose validRelease() {
        return new Pose("slash_release_035_back",
                new Mat3(.956481984360149,.2907877832474427,.024180130448575893,
                        -.08913869208517029,.2122820808588393,.9731344263356355,
                        .2778425942288494,-.9329409323533188,.2289644286169872),
                new Mat3(-.029744891914981836,.35243235202772166,-.9353644630031517,
                        .9976282529381361,-.04764523323385142,-.04967696337030513,
                        -.062073427040632956,-.9346236509923661,-.350179269319489));
    }

    private static double angleDegrees(Mat3 a, Mat3 b) {
        Mat3 relative = a.transpose().mul(b);
        double cosine = Math.max(-1, Math.min(1, (relative.m00() + relative.m11() + relative.m22() - 1) / 2));
        return Math.toDegrees(Math.acos(cosine));
    }

    private static Mat3 socketRoll(Mat3 arm, double degrees) {
        Vec axis = SOCKET.scale(1 / SOCKET.length());
        double x=axis.x(),y=axis.y(),z=axis.z(),a=Math.toRadians(degrees),c=Math.cos(a),s=Math.sin(a),t=1-c;
        Mat3 roll = new Mat3(t*x*x+c,t*x*y-s*z,t*x*z+s*y,
                t*x*y+s*z,t*y*y+c,t*y*z-s*x,
                t*x*z-s*y,t*y*z+s*x,t*z*z+c);
        return arm.mul(roll);
    }

    private static double firstContactAngle(Pose pose, GripContact contact, double margin) {
        for (int index=1; index<=400; index++) {
            double magnitude=index*.05;
            for (double sign : new double[]{1,-1}) {
                Mat3 candidate=socketRoll(pose.arm(),sign*magnitude);
                if (contact.intersects(candidate,pose.blade(),PHYSICAL_SHIFT,SOCKET,MIN,MAX,margin))
                    return sign*magnitude;
            }
        }
        return Double.NaN;
    }

    private static Vec supportTarget(Mat3 arm, Mat3 blade, WeaponRig.GripAttachment attachment) {
        return WeaponRig.RIGHT_SHOULDER.add(arm.apply(attachment.socket())).add(attachment.shoulderShift())
                .add(blade.apply(attachment.itemGrip())).subtract(blade.apply(attachment.itemAxis()).scale(GAP));
    }

    @Test void stockFailurePosesGainTexturedContactWithSmallFreeSocketRoll() {
        GripContact contact = stockContact();
        WeaponRig.GripAttachment attachment = attachment(contact);
        for (Pose pose : List.of(regularBack(), regularFront(), heavyFirst())) {
            assertFalse(contact.intersects(pose.arm(), pose.blade(), PHYSICAL_SHIFT, SOCKET, MIN, MAX, 0), pose.name());
            double zeroMarginAngle = firstContactAngle(pose, contact, 0);
            double interiorAngle = firstContactAngle(pose, contact, .001);
            if (pose.name().contains("heavy")) assertTrue(Math.abs(zeroMarginAngle) >= 11.9 && Math.abs(zeroMarginAngle) <= 12.2);
            else assertTrue(Math.abs(zeroMarginAngle) >= 3.2 && Math.abs(zeroMarginAngle) <= 3.4);
            Mat3 corrected = WeaponRig.preserveOrRollForMainContact(pose.arm(), pose.blade(), attachment, MIN, MAX);
            assertTrue(contact.intersects(corrected, pose.blade(), PHYSICAL_SHIFT, SOCKET, MIN, MAX, .001), pose.name());
            double roll = angleDegrees(pose.arm(), corrected);
            assertTrue(roll > 0 && roll < (pose.name().contains("heavy") ? 14 : 5), pose.name() + " roll=" + roll);
            assertTrue(Math.abs(roll - Math.abs(interiorAngle)) < .25, pose.name() + " first-contact refinement=" + roll);

            // Counter-rotation changes the hand frame but leaves the blade orientation unchanged.
            Mat3 item = WeaponRig.HAND.transpose().mul(corrected.transpose()).mul(pose.blade());
            assertTrue(corrected.mul(WeaponRig.HAND).mul(item).distance(pose.blade()) < 1e-10, pose.name());
            assertTrue(corrected.apply(SOCKET).subtract(pose.arm().apply(SOCKET)).length() < 1e-10, pose.name());
            assertTrue(supportTarget(corrected, pose.blade(), attachment)
                    .subtract(supportTarget(pose.arm(), pose.blade(), attachment)).length() < 1e-10, pose.name());
            assertEquals(10, corrected.apply(new Vec(0, 10, 0)).length(), 1e-10, pose.name());
        }
    }

    @Test void alreadyMatchedMainContactPreservesTheOriginalArmExactly() {
        GripContact contact = stockContact();
        WeaponRig.GripAttachment attachment = attachment(contact);
        Pose pose = validRelease();
        assertTrue(contact.intersects(pose.arm(), pose.blade(), PHYSICAL_SHIFT, SOCKET, MIN, MAX, 0));
        assertSame(pose.arm(), WeaponRig.preserveOrRollForMainContact(pose.arm(), pose.blade(), attachment, MIN, MAX));
    }

    @Test void gripMetadataIsImmutableFiniteAndRejectsMalformedGeometry() {
        List<GripContact.Prism> input = new ArrayList<>();
        input.add(new GripContact.Prism(Vec.ZERO, new Vec(.4,0,0), new Vec(0,.4,0), new Vec(0,0,.4)));
        GripContact contact = new GripContact(input);
        input.clear();
        assertEquals(1, contact.texels().size());
        assertThrows(UnsupportedOperationException.class, () -> contact.texels().clear());
        assertThrows(IllegalArgumentException.class, () -> new GripContact(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GripContact.Prism(new Vec(Double.NaN,0,0),
                new Vec(1,0,0), new Vec(0,1,0), new Vec(0,0,1)));
    }

    @Test void viewShoulderCompensationDoesNotMoveThePhysicalMainHandFrame() {
        WeaponRig.GripAttachment original = attachment(stockContact());
        for (boolean left : new boolean[]{false, true}) {
            WeaponRig.GripAttachment view = original.withViewShoulders(left ? 70 : -70, 35, left);
            assertNotEquals(original.shoulderShift(), view.shoulderShift());
            assertEquals(PHYSICAL_SHIFT, view.physicalRootShift());
            assertEquals(original.socket(), view.socket());
            assertEquals(original.itemGrip(), view.itemGrip());
            assertTrue(view.slimHand());
            assertSame(original.mainContact(), view.mainContact());
        }
    }

    @Test void correctionRemainsBoundedAndContinuousAcrossDenseWindupReleaseAndRecoverySamples() {
        GripContact contact = stockContact();
        WeaponRig.GripAttachment attachment = attachment(contact);
        WeaponRig.GripAttachment supportOnly = new WeaponRig.GripAttachment(attachment.socket(), attachment.itemGrip(),
                attachment.itemAxis(), attachment.shoulderShift());
        int correctedSamples = 0;
        ArcPath arc = ArcPath.horizontal(140);
        for (int phase=0; phase<3; phase++) {
            Mat3 previous = null;
            for (int i = 0; i <= 400; i++) {
                double t = i / 400.0;
                ArcPath.Keyframe point = phase == 0 ? arc.windup(t, false) : phase == 1 ? arc.sample(t) : arc.sample(1);
                double[] body = switch (phase) {
                    case 0 -> new double[]{4.9*t, 12+15.68*t, 4+3.92*t};
                    case 1 -> new double[]{5-1.9833333333333334*t, 28-9.916666666666666*t, 8-2.975*t};
                    default -> new double[]{-2+2*t, -12+12*t, -4+4*t};
                };
                double yaw = -70 + point.yaw(), pitch = point.pitch();
                double twist = arc.edgeAngle(phase == 0 ? 0 : phase == 1 ? t : 1);
                WeaponRig rig = WeaponRig.solve(yaw, pitch, -80, twist, WeaponRig.TwistAxis.Z,
                        body, .45, GAP, true, attachment);
                Mat3 current = Mat3.zyx(rig.arm()[0], rig.arm()[1], rig.arm()[2]);
                WeaponRig baseline = WeaponRig.solve(yaw, pitch, -80, twist, WeaponRig.TwistAxis.Z, body, .45, GAP, true, supportOnly);
                Mat3 baselineArm = Mat3.zyx(baseline.arm()[0], baseline.arm()[1], baseline.arm()[2]);
                assertTrue(angleDegrees(baselineArm, current) <= 20.001, "bounded roll in phase " + phase + " at " + t);
                Mat3 blade = current.mul(WeaponRig.HAND).mul(Mat3.zyx(rig.item()[0], rig.item()[1], rig.item()[2]));
                Mat3 baselineBlade = baselineArm.mul(WeaponRig.HAND)
                        .mul(Mat3.zyx(baseline.item()[0], baseline.item()[1], baseline.item()[2]));
                assertTrue(blade.distance(baselineBlade) < 1e-9, "main correction must preserve the rendered blade");
                assertTrue(current.apply(SOCKET).subtract(baselineArm.apply(SOCKET)).length() < 1e-9,
                        "main correction must preserve the rendered socket position");
                assertArrayEquals(baseline.offArm(), rig.offArm(), 1e-12);
                assertEquals(baseline.offShoulder(), rig.offShoulder());
                if (current.distance(baselineArm) > 1e-6) {
                    correctedSamples++;
                    assertTrue(contact.intersects(current, blade, PHYSICAL_SHIFT, SOCKET, MIN, MAX, .001),
                            "every applied roll must obtain opaque handle contact");
                }
                if (previous != null) assertTrue(angleDegrees(previous, current) < 8,
                        "pose jump in phase " + phase + " at progress " + t);
                previous = current;
            }
        }
        assertTrue(correctedSamples > 0, "dense animation phases must exercise an actual main-hand correction");
    }
}
