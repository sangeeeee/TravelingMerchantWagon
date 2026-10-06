package com.sange.tm_wagon.assembly;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import com.sange.tm_wagon.entity.WagonEntity;
public class DismantlingHammerGameTests {
    public static ItemStack strike(GameTestHelper h,WagonEntity wagon) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);var hammer=WagonContent.DISMANTLING_HAMMER.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND,hammer);
        Vec3 eye=wagon.pose().point(new Vec3(-2.8,2,0)),target=wagon.pose().point(new Vec3(0,2,0));
        player.setPos(eye.subtract(0,player.getEyeHeight(),0));Vec3 delta=target.subtract(eye);
        player.setYRot((float)-Math.toDegrees(Math.atan2(delta.x,delta.z)));player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        player.attack(wagon);h.assertTrue(wagon.isRemoved(),"Hammer did not dismantle wagon with a direct attack");return hammer;
    }
}
