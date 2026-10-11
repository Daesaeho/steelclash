package com.steelclash.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ViewReadyRigTest {
    private static Mat3 arm(double[] a) {return Mat3.zyx(a[0],a[1],a[2]);}
    @Test void bodyLagCannotDragEitherReadyHandOutOfItsViewPosition() {
        for (boolean left:new boolean[]{false,true}) {
            WeaponRig base=WeaponRig.solve(45,-75,-80,0,WeaponRig.TwistAxis.Z,new double[3],.45);
            if (left) base=base.mirrored();
            Vec shoulder=left?WeaponRig.LEFT_SHOULDER:WeaponRig.RIGHT_SHOULDER;
            Vec expected=shoulder.add(arm(base.arm()).apply(new Vec(0,10,0)));
            for (double yaw:new double[]{-170,-75,-50,0,50,75,170}) {
                WeaponRig turned=base.fromView(yaw,0);
                Vec actual=shoulder.add(WeaponRig.viewShoulderOffset(yaw,0,left)).add(arm(turned.arm()).apply(new Vec(0,10,0)));
                actual=WeaponRig.cameraRotation(yaw,0).transpose().apply(actual);
                assertEquals(0,actual.subtract(expected).length(),1e-8,"view position at body lag "+yaw);
                assertEquals(0,WeaponRig.cameraRotation(yaw,0).transpose().mul(turned.bladeOrientation(new double[3])).distance(base.bladeOrientation(new double[3])),1e-8);
            }
        }
    }
    @Test void rotatedTwoHandedGripAndViewOffsetStayTogether() {
        WeaponRig base=WeaponRig.solve(45,-75,-80,0,WeaponRig.TwistAxis.Z,new double[3],.45);
        for (double yaw:new double[]{-90,60,170}) {
            WeaponRig r=base.fromView(yaw,25); Mat3 camera=WeaponRig.cameraRotation(yaw,25);
            Vec expected=WeaponRig.LEFT_SHOULDER.add(base.offShoulder()).add(arm(base.offArm()).apply(new Vec(0,10,0)));
            Vec actual=WeaponRig.LEFT_SHOULDER.add(WeaponRig.viewShoulderOffset(yaw,25,true)).add(r.offShoulder()).add(arm(r.offArm()).apply(new Vec(0,10,0)));
            assertEquals(0,camera.transpose().apply(actual).subtract(expected).length(),1e-8);
        }
    }
}
