package com.sange.tm_wagon.entity;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class WagonInteractionGameTests {
    private static WagonEntity wagon(GameTestHelper h) {
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);return w;
    }
    private static void canopy(GameTestHelper h,WagonEntity w) {
        var tag=w.cargo().save(h.getLevel().registryAccess(),false);var roof=new CompoundTag();roof.putBoolean("Installed",true);tag.put("Canopy",roof);
        w.cargo().load(tag,h.getLevel().registryAccess());w.cargoGeometryChanged();
    }
    private static void aim(Player p,Vec3 eye,Vec3 target) {
        p.setPos(eye.subtract(0,p.getEyeHeight(),0));Vec3 d=target.subtract(eye).normalize();
        p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.asin(d.y)));
    }
    @GameTest(template="assembly_test",timeoutTicks=35)
    public static void rotated_horse_wagon_ray_hits_tailgate_and_curtains_at_their_real_surfaces(GameTestHelper h) {
        for(int x=1;x<24;x++)for(int z=1;z<24;z++)h.setBlock(new BlockPos(x,1,z),net.minecraft.world.level.block.Blocks.STONE);
        var w=wagon(h);canopy(h,w);var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);
        var horse=EntityType.HORSE.create(h.getLevel());horse.setNoAi(true);horse.setPos(w.horsePosition(0));h.getLevel().addFreshEntity(horse);horse.setLeashedTo(p,true);
        p.setPos(horse.position());String attachment=w.attachHorse(p,horse,0);h.assertTrue(attachment==null,"Horse setup failed: "+attachment);
        for(var angles:new float[][]{{29.676F,0,0},{213,.14F,-.09F},{137,-.10F,.07F}}) {
            w.applyPose(new WagonPose(w.position(),angles[0],angles[1],angles[2]));
            var tail=WagonGeometry.partBoxes(w.cargoBody()).get(4);
            Vec3 target=w.pose().point(new Vec3(.3,1.9,tail.maxZ)),eye=w.pose().point(new Vec3(.3,2.1,tail.maxZ+1.2));aim(p,eye,target);
            var hit=w.pick(eye,target.add(target.subtract(eye).normalize().scale(.08))).orElseThrow();
            Vec3 local=w.pose().local(hit);
            h.assertTrue(!w.containsPickPoint(eye),"Empty space behind the gate was treated as a solid hit");
            var nativeHit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p,eye,target.add(target.subtract(eye).normalize().scale(.08)),w.getBoundingBox().inflate(2),e->e==w,16);
            h.assertTrue(nativeHit!=null&&nativeHit.getLocation().distanceTo(hit)<.0001,"Native entity picking did not use the actual surface");
            h.assertTrue(tail.inflate(.0001).contains(local),"World collision bounds produced a false tailgate hit at "+local+" for yaw "+angles[0]);
            for(boolean front:new boolean[]{false,true}) {
                double end=front?CargoCanopy.FRONT:w.cargo().canopy().back(w.cargoBody());
                target=w.pose().point(new Vec3(.82,3.4,end));eye=w.pose().point(new Vec3(.82,3.4,end+(front?-1:1)));aim(p,eye,target);
                h.assertTrue(w.interactAt(p,target.subtract(w.position()),InteractionHand.MAIN_HAND).consumesAction()&&w.cargo().canopy().closed(front),"Angled curtain click failed for yaw "+angles[0]);
                target=w.pose().point(new Vec3(0,3.4,w.cargo().canopy().curtainZ(w.cargoBody(),front)+CargoCanopy.THICK/2));eye=w.pose().point(new Vec3(0,3.4,end+(front?-1:1)));aim(p,eye,target);
                h.assertTrue(w.interactAt(p,target.subtract(w.position()),InteractionHand.MAIN_HAND).consumesAction()&&!w.cargo().canopy().closed(front),"Angled curtain reopen failed: front="+front+", yaw="+angles[0]+", hit="+w.pick(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(3))).map(w.pose()::local));
            }
            h.assertTrue(!w.cargoBusy(),"Attaching a horse created an assembly lock");
        }
        h.assertTrue(w.cargo().toggleGate()==null,"Gate did not start opening");
        String ignored=w.cargo().toggleGate();h.assertTrue(ignored!=null&&!"message.tm_wagon.assembly_busy".equals(ignored),"Animation click was incorrectly reported as assembly in progress");
        h.succeed();
    }
}
