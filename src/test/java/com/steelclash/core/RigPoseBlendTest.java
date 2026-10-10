package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RigPoseBlendTest {
    private static final RigPoseBlend.Bone WEAPON = new RigPoseBlend.Bone(-1.4, -1.5, .5, 5, -2, -4, 0);
    private static final RigPoseBlend.Bone KICK = new RigPoseBlend.Bone(-2.1, .2, 0, 0, -3, -4, 0);
    private static void same(RigPoseBlend.Bone a, RigPoseBlend.Bone b) {
        assertEquals(0, Mat3.zyx(a.rx(),a.ry(),a.rz()).distance(Mat3.zyx(b.rx(),b.ry(),b.rz())), 1e-8);
        assertEquals(a.x(),b.x(),1e-9); assertEquals(a.y(),b.y(),1e-9); assertEquals(a.z(),b.z(),1e-9);
    }
    @Test void enteringKickStartsFromTheDisplayedBoneAndSettles() {
        var blend = new RigPoseBlend(); blend.begin(1, 0, false, false, "sword"); blend.apply("arm",WEAPON);
        blend.begin(2, 50_000_000, true, false, "sword"); same(WEAPON,blend.apply("arm",KICK));
        blend.begin(3, 150_000_000, true, false, "sword");
        var middle=blend.apply("arm",KICK); assertEquals(2.5,middle.x(),1e-9);
        blend.begin(4, 250_000_000, true, false, "sword"); same(KICK,blend.apply("arm",KICK));
    }
    @Test void reversalUsesTheLastDisplayedPose() {
        var blend=new RigPoseBlend(); blend.begin(1,0,false,false,"sword"); blend.apply("arm",WEAPON);
        blend.begin(2,50_000_000,true,false,"sword"); blend.apply("arm",KICK);
        blend.begin(3,150_000_000,true,false,"sword"); var middle=blend.apply("arm",KICK);
        blend.begin(4,160_000_000,false,false,"sword"); same(middle,blend.apply("arm",WEAPON));
    }
    @Test void weaponReleaseAndItemReplacementNeverRetainAnOldRig() {
        var blend=new RigPoseBlend(); blend.begin(1,0,true,false,"sword"); blend.apply("arm",KICK);
        blend.begin(2,50_000_000,false,false,"sword"); same(KICK,blend.apply("arm",WEAPON));
        blend.begin(2,50_000_001,false,true,"sword"); same(WEAPON,blend.apply("arm",WEAPON));
        blend.begin(3,100_000_000,true,false,"sword"); blend.apply("arm",KICK);
        blend.begin(4,120_000_000,false,false,"axe"); same(WEAPON,blend.apply("arm",WEAPON));
    }
    @Test void weaponReleaseOnANewFrameAlsoBypassesTheBlend() {
        var blend=new RigPoseBlend(); blend.begin(1,0,true,false,"sword"); blend.apply("arm",KICK);
        blend.begin(2,50_000_000,false,true,"sword"); same(WEAPON,blend.apply("arm",WEAPON));
    }
    @Test void heldItemsBlendInTheirPhysicalHandFrame() {
        var a=new RigPoseBlend.Bone(-.6,1.1,-.2,0,0,0,0);
        var b=new RigPoseBlend.Bone(.8,-.3,1.3,0,0,0,0);
        var blend=new RigPoseBlend(); blend.begin(1,0,false,false,"sword"); blend.apply("right_item",a);
        blend.begin(2,50_000_000,true,false,"sword"); blend.apply("right_item",b);
        blend.begin(3,150_000_000,true,false,"sword"); var mid=blend.apply("right_item",b);
        Mat3 from=Mat3.rotZ(-a.ry()).mul(Mat3.rotY(-a.rz())).mul(Mat3.rotX(-a.rx()));
        Mat3 to=Mat3.rotZ(-b.ry()).mul(Mat3.rotY(-b.rz())).mul(Mat3.rotX(-b.rx()));
        Mat3 actual=Mat3.rotZ(-mid.ry()).mul(Mat3.rotY(-mid.rz())).mul(Mat3.rotX(-mid.rx()));
        assertEquals(angle(from,actual),angle(to,actual),1e-8,"midpoint is equally far from both physical orientations");
        assertTrue(angle(from,actual)<angle(from,to));
    }
    private static double angle(Mat3 a, Mat3 b) {
        Mat3 relative=a.transpose().mul(b);
        return Math.acos(Math.max(-1,Math.min(1,(relative.m00()+relative.m11()+relative.m22()-1)/2)));
    }
    @Test void repeatedBonesInOneFrameShareTheSameFractionAndResetClearsHistory() {
        var blend=new RigPoseBlend(); blend.begin(1,0,false,false,"sword"); blend.apply("arm",WEAPON);
        blend.begin(2,50_000_000,true,false,"sword"); same(WEAPON,blend.apply("arm",KICK));
        blend.begin(2,180_000_000,true,false,"sword"); same(WEAPON,blend.apply("arm",KICK));
        blend.reset(); blend.begin(3,200_000_000,true,false,"sword"); same(KICK,blend.apply("arm",KICK));
        blend.begin(8,250_000_000,false,false,"sword"); same(WEAPON,blend.apply("arm",WEAPON));
    }
}
