package com.steelclash.core;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderedGripTest {
    private static WeaponRig.GripAttachment handheld(boolean slim) {
        Mat3 display = Mat3.rotY(Math.toRadians(-90)).mul(Mat3.rotZ(Math.toRadians(55)));
        Vec grip = new Vec(0,4,.5).add(display.apply(new Vec(-3.5,-3.5,0).scale(.85)));
        Vec axis = display.apply(new Vec(1,1,0).scale(1/Math.sqrt(2)));
        return new WeaponRig.GripAttachment(new Vec(-1,10,-2),grip,axis,slim?new Vec(.5,0,0):Vec.ZERO);
    }
    private static Mat3 arm(WeaponRig rig) { return Mat3.zyx(rig.arm()[0],rig.arm()[1],rig.arm()[2]); }
    private static Mat3 item(WeaponRig rig) { return arm(rig).mul(WeaponRig.HAND).mul(Mat3.zyx(rig.item()[0],rig.item()[1],rig.item()[2])); }
    private static Vec target(WeaponRig rig, WeaponRig.GripAttachment attachment, double gap) {
        Mat3 blade=item(rig);
        return WeaponRig.RIGHT_SHOULDER.add(attachment.shoulderShift()).add(arm(rig).apply(attachment.socket()))
                .add(blade.apply(attachment.itemGrip())).subtract(blade.apply(attachment.itemAxis()).scale(gap));
    }
    private static Vec palm(WeaponRig rig) {
        return WeaponRig.LEFT_SHOULDER.add(rig.offShoulder())
                .add(Mat3.zyx(rig.offArm()[0],rig.offArm()[1],rig.offArm()[2]).apply(new Vec(0,9,0)));
    }
    private static Vec mirror(Vec value) { return new Vec(-value.x(),value.y(),value.z()); }
    private static void assertSameRig(WeaponRig expected, WeaponRig actual) {
        assertArrayEquals(expected.arm(),actual.arm());assertArrayEquals(expected.item(),actual.item());
        assertArrayEquals(expected.offArm(),actual.offArm());assertEquals(expected.offShoulder(),actual.offShoulder());
    }
    private static WeaponRig.GripAttachment attachmentWithCentre(WeaponRig base, Vec socket, Vec centre, double gap) {
        Vec shaft = new Vec(0,1,0);
        Vec constant = WeaponRig.LEFT_SHOULDER.subtract(WeaponRig.RIGHT_SHOULDER).subtract(centre);
        Vec grip = item(base).transpose().apply(constant).add(shaft.scale(gap));
        return new WeaponRig.GripAttachment(socket,grip,shaft,Vec.ZERO);
    }
    @Test void physicalHandleTargetIsReachedInDeepHeavyWindup() {
        var attachment=handheld(true);double[] body={0,12,4};var point=ArcPath.horizontal(140).windup(.99,true);
        WeaponRig rig=WeaponRig.solve(point.yaw(),point.pitch(),-80,90,WeaponRig.TwistAxis.Z,body,.45,2.5,true,attachment);
        assertEquals(0,palm(rig).subtract(target(rig,attachment,2.5)).length(),1e-8);
        assertTrue(rig.offShoulder().length()<=4+1e-9);
    }
    @Test void physicalSocketConstraintPreservesBladeAndRigidArmLengthAcrossAnglesAndGaps() {
        Random random=new Random(7351);
        for(boolean slim:new boolean[]{false,true})for(int i=0;i<1500;i++) {
            var attachment=handheld(slim);
            double yaw=random.nextDouble()*300-150,pitch=random.nextDouble()*180-110,gap=.1+random.nextDouble()*9.9;
            double[] body={random.nextDouble()*50-25,random.nextDouble()*140-70,random.nextDouble()*40-20};
            var base=WeaponRig.solve(yaw,pitch,-80,-65,WeaponRig.TwistAxis.Z,body,.45,gap,true);
            var rig=WeaponRig.solve(yaw,pitch,-80,-65,WeaponRig.TwistAxis.Z,body,.45,gap,true,attachment);
            assertEquals(0,base.bladeOrientation(body).distance(rig.bladeOrientation(body)),1e-8);
            assertEquals(10,arm(rig).apply(new Vec(0,10,0)).length(),1e-8);
            assertEquals(0,palm(rig).subtract(target(rig,attachment,gap)).length(),1e-8);
            assertTrue(rig.offShoulder().length()<=4+1e-8);
        }
    }
    @Test void absentBindingAndOccupiedHandPreserveLegacyComponentsExactly() {
        double[] body={8,48,11};
        for(boolean two:new boolean[]{false,true}) {
            var base=WeaponRig.solve(125,-35,-80,90,WeaponRig.TwistAxis.Z,body,.45,7,two);
            var rig=WeaponRig.solve(125,-35,-80,90,WeaponRig.TwistAxis.Z,body,.45,7,two,null);
            assertEquals(base.offShoulder(),rig.offShoulder());assertArrayEquals(base.arm(),rig.arm());
            assertArrayEquals(base.item(),rig.item());assertArrayEquals(base.offArm(),rig.offArm());
        }
        var base=WeaponRig.solve(125,-35,-80,90,WeaponRig.TwistAxis.Z,body,.45,7,false);
        var rig=WeaponRig.solve(125,-35,-80,90,WeaponRig.TwistAxis.Z,body,.45,7,false,handheld(true));
        assertArrayEquals(base.arm(),rig.arm());assertArrayEquals(base.item(),rig.item());
        assertArrayEquals(base.offArm(),rig.offArm());assertEquals(base.offShoulder(),rig.offShoulder());
    }
    @Test void mirroredAttachmentTargetsTheSamePhysicalGrip() {
        var attachment=handheld(true);var rig=WeaponRig.solve(125,-35,-80,90,WeaponRig.TwistAxis.Z,new double[]{8,48,11},.45,2.5,true,attachment);
        Vec right=target(rig,attachment,2.5);var mirror=rig.mirrored();
        Vec left=WeaponRig.RIGHT_SHOULDER.add(mirror.offShoulder())
                .add(Mat3.zyx(mirror.offArm()[0],mirror.offArm()[1],mirror.offArm()[2]).apply(new Vec(0,9,0)));
        assertEquals(-right.x(),left.x(),1e-8);assertEquals(right.y(),left.y(),1e-8);assertEquals(right.z(),left.z(),1e-8);
    }
    @Test void malformedAttachmentsFailBeforeSampling() {
        assertThrows(IllegalArgumentException.class,()->new WeaponRig.GripAttachment(Vec.ZERO,Vec.ZERO,new Vec(0,1,0),Vec.ZERO));
        assertThrows(IllegalArgumentException.class,()->new WeaponRig.GripAttachment(new Vec(0,10,0),Vec.ZERO,Vec.ZERO,Vec.ZERO));
        assertThrows(IllegalArgumentException.class,()->new WeaponRig.GripAttachment(new Vec(Double.NaN,10,0),Vec.ZERO,new Vec(0,1,0),Vec.ZERO));
        assertThrows(IllegalArgumentException.class,()->new WeaponRig.GripAttachment(new Vec(0,10,0),Vec.ZERO,new Vec(1e200,0,0),Vec.ZERO));
    }

    @Test void unreachableFiniteAttachmentsReturnTheLegacyBoundedRig() {
        double[] body={0,0,0};double gap=2.5;
        var base=WeaponRig.solve(0,0,-80,0,WeaponRig.TwistAxis.Z,body,.45,gap,true);
        for(var attachment:new WeaponRig.GripAttachment[]{
                attachmentWithCentre(base,new Vec(0,1,0),Vec.ZERO,gap),
                attachmentWithCentre(base,new Vec(0,20,0),Vec.ZERO,gap),
                attachmentWithCentre(base,new Vec(0,10,0),new Vec(1000,0,0),gap),
                attachmentWithCentre(base,new Vec(1e200,0,0),Vec.ZERO,gap)}) {
            var rig=assertDoesNotThrow(()->WeaponRig.solve(0,0,-80,0,WeaponRig.TwistAxis.Z,body,.45,gap,true,attachment));
            assertSameRig(base,rig);
            assertTrue(rig.offShoulder().length()<=WeaponRig.MAX_SHOULDER_REACH);
        }
    }

    @Test void nearAntiparallelSocketCorrectionRemainsFiniteAndKeepsBladeOrientation() {
        double[] body={0,0,0};double gap=2.5;
        var base=WeaponRig.solve(0,0,-80,0,WeaponRig.TwistAxis.Z,body,.45,gap,true);
        // At the outer tangent the socket must reverse; the small lateral component survives even when dot rounds to -1.
        Vec socket=arm(base).transpose().apply(new Vec(0,0,10));
        for(double lateral:new double[]{0,1e-8,-1e-8}) {
            Vec centre=new Vec(lateral,0,-1).scale(23);
            var attachment=attachmentWithCentre(base,socket,centre,gap);
            var rig=WeaponRig.solve(0,0,-80,0,WeaponRig.TwistAxis.Z,body,.45,gap,true,attachment);
            for(double angle:rig.arm())assertTrue(Double.isFinite(angle));
            for(double angle:rig.item())assertTrue(Double.isFinite(angle));
            assertEquals(0,palm(rig).subtract(target(rig,attachment,gap)).length(),1e-7);
            assertEquals(0,base.bladeOrientation(body).distance(rig.bladeOrientation(body)),1e-8);
            assertEquals(10,arm(rig).apply(new Vec(0,10,0)).length(),1e-8);
            assertTrue(rig.offShoulder().length()<=4+1e-8);
        }
    }

    @Test void firstPersonPhysicalGripUsesTheActualViewAnchoredShouldersForBothMainArms() {
        double[] body={0,0,0};double gap=2.5;var original=handheld(true);
        for(boolean left:new boolean[]{false,true})for(double yaw:new double[]{-70,0,70})for(double pitch:new double[]{-35,0,35}) {
            var attachment=original.withViewShoulders(yaw,pitch,left);
            var base=WeaponRig.solve(125,-35,-80,90,WeaponRig.TwistAxis.Z,body,.45,gap,true);
            var rig=WeaponRig.solve(125,-35,-80,90,WeaponRig.TwistAxis.Z,body,.45,gap,true,attachment);
            if(left) { base=base.mirrored();rig=rig.mirrored(); }
            Vec main=(left?WeaponRig.LEFT_SHOULDER:WeaponRig.RIGHT_SHOULDER).add(WeaponRig.viewShoulderOffset(yaw,pitch,left));
            Vec off=(left?WeaponRig.RIGHT_SHOULDER:WeaponRig.LEFT_SHOULDER).add(WeaponRig.viewShoulderOffset(yaw,pitch,!left));
            Vec shift=left?mirror(original.shoulderShift()):original.shoulderShift();
            Vec socket=left?mirror(original.socket()):original.socket();
            Vec grip=left?mirror(original.itemGrip()):original.itemGrip();
            Vec axis=left?mirror(original.itemAxis()):original.itemAxis();
            Vec actualTarget=main.add(shift).add(arm(rig).apply(socket)).add(item(rig).apply(grip))
                    .subtract(item(rig).apply(axis).scale(gap));
            Vec actualPalm=off.add(rig.offShoulder())
                    .add(Mat3.zyx(rig.offArm()[0],rig.offArm()[1],rig.offArm()[2]).apply(new Vec(0,9,0)));
            assertEquals(0,actualTarget.subtract(actualPalm).length(),1e-8,"actual rendered pivots must reach the handle");
            assertEquals(0,base.bladeOrientation(body).distance(rig.bladeOrientation(body)),1e-8);
            assertTrue(rig.offShoulder().length()<=4+1e-8);
        }
    }

    @Test void socketCorrectionRemainsContinuousAcrossTheAuthoredArcs() {
        var attachment=handheld(true);double[] body={8,48,11};
        for(ArcPath path:new ArcPath[]{ArcPath.horizontal(140),ArcPath.horizontal(140).mirrored(),ArcPath.vertical(),ArcPath.thrust()}) {
            Vec previous=null;
            for(int i=0;i<=2000;i++) {
                var point=path.windup(i/2000.,true);
                var rig=WeaponRig.solve(point.yaw(),point.pitch(),-80,90,WeaponRig.TwistAxis.Z,body,.45,2.5,true,attachment);
                Vec socket=arm(rig).apply(attachment.socket());
                if(previous!=null)assertTrue(socket.subtract(previous).length()<.25,"socket must not jump at a reach boundary");
                previous=socket;
            }
        }
    }
}
