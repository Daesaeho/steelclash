package com.steelclash.core;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwoHandedRigTest {
    private static Vec weaponHand(WeaponRig rig) {
        return WeaponRig.RIGHT_SHOULDER.add(Mat3.zyx(rig.arm()[0],rig.arm()[1],rig.arm()[2]).apply(new Vec(0,WeaponRig.HAND_DISTANCE,0)));
    }
    private static Vec supportHand(WeaponRig rig) {
        return WeaponRig.LEFT_SHOULDER.add(rig.offShoulder()).add(Mat3.zyx(rig.offArm()[0],rig.offArm()[1],rig.offArm()[2]).apply(new Vec(0,WeaponRig.SUPPORT_GRIP_DISTANCE,0)));
    }
    private static void assertGrip(WeaponRig rig,double yaw,double pitch,double[] body,double gap) {
        Vec blade=WeaponRig.bodyRotation(body).transpose().apply(ArmAim.modelDirection(yaw,pitch));
        Vec expected=weaponHand(rig).subtract(blade.scale(gap));
        assertEquals(0,supportHand(rig).subtract(expected).length(),1e-8,"support hand must reach its actual grip target");
        assertTrue(rig.offShoulder().length()<=WeaponRig.MAX_SHOULDER_REACH+1e-8,"bounded shoulder travel");
    }

    @Test void deepSwordWindupKeepsSupportHandOnItsGrip() {
        var point=ArcPath.horizontal(140).windup(.5,false);double[] body={0,12,4};
        WeaponRig rig=WeaponRig.solve(point.yaw(),point.pitch(),-80,90,WeaponRig.TwistAxis.Z,body,.45,2.5,true);
        assertGrip(rig,point.yaw(),point.pitch(),body,2.5);
    }

    @Test void bothArmsReachAcrossReviewedAnglesAndGripGapsWithoutChangingBladeOrientation() {
        Random random=new Random(911);
        for(int i=0;i<1500;i++) {
            double yaw=random.nextDouble()*300-150,pitch=random.nextDouble()*180-110,gap=.1+random.nextDouble()*9.9;
            double[] body={random.nextDouble()*50-25,random.nextDouble()*140-70,random.nextDouble()*40-20};
            WeaponRig one=WeaponRig.solve(yaw,pitch,-80,-65,WeaponRig.TwistAxis.Z,body,.45,gap);
            WeaponRig two=WeaponRig.solve(yaw,pitch,-80,-65,WeaponRig.TwistAxis.Z,body,.45,gap,true);
            assertGrip(two,yaw,pitch,body,gap);
            assertEquals(0,one.bladeOrientation(body).distance(two.bladeOrientation(body)),1e-9,"blade remains on the same arc");
        }
    }

    @Test void singleHandedOverloadPreservesEveryLegacyRigComponent() {
        double[] body={8,48,11};
        WeaponRig old=WeaponRig.solve(125,-35,-80,90,WeaponRig.TwistAxis.Z,body,.45,7);
        WeaponRig explicit=WeaponRig.solve(125,-35,-80,90,WeaponRig.TwistAxis.Z,body,.45,7,false);
        assertArrayEquals(old.arm(),explicit.arm());assertArrayEquals(old.item(),explicit.item());
        assertArrayEquals(old.offArm(),explicit.offArm());assertEquals(old.offShoulder(),explicit.offShoulder());
    }

    @Test void constrainedRigKeepsBothArmLengthsAndMirrorsItsGrip() {
        WeaponRig rig=WeaponRig.solve(120,-32,-80,85,WeaponRig.TwistAxis.Z,new double[]{8,48,11},.45,7,true);
        WeaponRig mirror=rig.mirrored();
        for(double[] arm:new double[][]{rig.arm(),rig.offArm(),mirror.arm(),mirror.offArm()})
            assertEquals(10,Mat3.zyx(arm[0],arm[1],arm[2]).apply(new Vec(0,10,0)).length(),1e-9);
        Vec right=weaponHand(rig),left=WeaponRig.LEFT_SHOULDER.add(Mat3.zyx(mirror.arm()[0],mirror.arm()[1],mirror.arm()[2]).apply(new Vec(0,10,0)));
        assertEquals(-right.x(),left.x(),1e-9);assertEquals(right.y(),left.y(),1e-9);assertEquals(right.z(),left.z(),1e-9);
    }

    @Test void ordinaryArcAndWindupSamplesDoNotJumpWhenTheReachConstraintEngages() {
        double[] body={8,48,11};
        for(ArcPath path:new ArcPath[]{ArcPath.horizontal(140),ArcPath.horizontal(140).mirrored(),ArcPath.vertical(),ArcPath.thrust()})
            for(double gap:new double[]{2.5,7,10})for(boolean windup:new boolean[]{false,true}) {
                Vec previous=null;
                for(int i=0;i<=2000;i++) {
                    var point=windup?path.windup(i/2000.,true):path.sample(i/2000.);
                    WeaponRig rig=WeaponRig.solve(point.yaw(),point.pitch(),-80,90,WeaponRig.TwistAxis.Z,body,.45,gap,true);
                    Vec hand=weaponHand(rig);
                    if(previous!=null)assertTrue(hand.subtract(previous).length()<.25,"hand must not jump at a reach boundary");
                    previous=hand;
                }
            }
    }
}
