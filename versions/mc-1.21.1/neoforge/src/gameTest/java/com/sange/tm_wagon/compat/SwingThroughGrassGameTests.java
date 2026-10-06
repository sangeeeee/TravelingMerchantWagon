package com.sange.tm_wagon.compat;

import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class SwingThroughGrassGameTests {
    private static boolean enabled(GameTestHelper h) {
        if(ModList.get().isLoaded("swingthroughgrass"))return true;
        h.succeed();return false;
    }
    private static WagonEntity wagon(GameTestHelper h) {
        var w=WagonContent.WAGON.get().create(h.getLevel());
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(10,3,12))));h.getLevel().addFreshEntity(w);return w;
    }
    private static void aim(Player p,Vec3 eye,Vec3 target) {
        p.setPos(eye.subtract(0,p.getEyeHeight(),0));
        Vec3 d=target.subtract(eye);p.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));
        p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));p.setYHeadRot(p.getYRot());
    }
    private static Player player(GameTestHelper h,Vec3 eye,Vec3 target) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);aim(p,eye,target);return p;
    }
    private static PlayerInteractEvent.LeftClickBlock click(Player p,BlockPos pos) {
        var event=new PlayerInteractEvent.LeftClickBlock(p,pos,Direction.UP,PlayerInteractEvent.LeftClickBlock.Action.START);
        NeoForge.EVENT_BUS.post(event);return event;
    }
    @GameTest(template="assembly_test")
    public static void swingthroughgrass_does_not_cancel_mining_for_wagon_bounds(GameTestHelper h) {
        if(!enabled(h))return;
        var w=wagon(h);Vec3 eye=w.position().add(-2,.6,0),target=w.position().add(0,.6,0);
        var p=player(h,eye,target);var block=BlockPos.containing(target);
        for(var state:new net.minecraft.world.level.block.state.BlockState[]{Blocks.SHORT_GRASS.defaultBlockState(),Blocks.POWDER_SNOW.defaultBlockState()}) {
            h.getLevel().setBlock(block,state,2);
            for(var item:new net.minecraft.world.item.Item[]{Items.AIR,Items.IRON_AXE,WagonContent.DISMANTLING_HAMMER.get()}) {
                p.setItemInHand(InteractionHand.MAIN_HAND,item.getDefaultInstance());
                h.assertTrue(!click(p,block).isCanceled(),"SwingThroughGrass cancelled visible mining for wagon bounds: "+state+" / "+item);
                h.assertTrue(!w.isRemoved(),"Mining redirected into hammer dismantling");
            }
        }
        var protectedEvent=new PlayerInteractEvent.LeftClickBlock(p,block,Direction.UP,PlayerInteractEvent.LeftClickBlock.Action.START);
        protectedEvent.setCanceled(true);NeoForge.EVENT_BUS.post(protectedEvent);
        h.assertTrue(protectedEvent.isCanceled(),"Compatibility cleared another protection's cancellation");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void swingthroughgrass_server_keeps_grass_broken_inside_rotated_wagon_bounds(GameTestHelper h) {
        if(!enabled(h))return;
        var w=wagon(h);var p=h.makeMockServerPlayerInLevel();p.setGameMode(GameType.SURVIVAL);
        p.setNoGravity(true);p.setItemInHand(InteractionHand.MAIN_HAND,Items.IRON_AXE.getDefaultInstance());
        Vec3 target=w.position().add(0,.6,0);var block=BlockPos.containing(target);
        h.getLevel().setBlock(block.below(),Blocks.DIRT.defaultBlockState(),3);
        for(int yaw:new int[]{0,45,90}) {
            w.setYRot(yaw);Vec3 eye=w.pose().point(new Vec3(-2,.6,0));aim(p,eye,target);
            h.assertTrue(w.getBoundingBox().contains(target)&&w.pick(eye,target).isEmpty(),"Grass is not visible through the wagon's empty space");
            h.getLevel().setBlock(block,Blocks.SHORT_GRASS.defaultBlockState(),3);
            p.gameMode.handleBlockBreakAction(block,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                Direction.UP,h.getLevel().getMaxBuildHeight(),yaw);
            h.assertTrue(h.getLevel().getBlockState(block).isAir(),"Server rejected grass break inside wagon bounds at yaw "+yaw);
            h.assertTrue(!w.isRemoved(),"Mining grass removed the wagon");
        }
        h.runAfterDelay(2,()->{
            h.assertTrue(h.getLevel().getBlockState(block).isAir(),"Server restored the broken grass");h.succeed();
        });
    }
    @GameTest(template="assembly_test")
    public static void swingthroughgrass_still_hits_mobs_but_not_through_wagon_walls(GameTestHelper h) {
        if(!enabled(h))return;
        var w=wagon(h);Vec3 eye=w.position().add(-2.2,2,0),target=w.position().add(0,2,0);
        var p=player(h,eye,target);var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);
        pig.setPos(w.position().add(0,1.6,0));h.getLevel().addFreshEntity(pig);
        var grass=BlockPos.containing(eye.add(.15,0,0));h.getLevel().setBlock(grass,Blocks.SHORT_GRASS.defaultBlockState(),2);
        float health=pig.getHealth();
        h.assertTrue(!click(p,grass).isCanceled()&&pig.getHealth()==health,"Grass redirected attack through wagon wall");
        w.discard();
        var normal=click(p,grass);
        h.assertTrue(normal.isCanceled(),"Ordinary swing-through-grass target was rejected");
        h.assertTrue(pig.getHealth()<health,"Ordinary hit did no damage: before="+health+" after="+pig.getHealth()+" cooldown="+p.getAttackStrengthScale(0));
        h.succeed();
    }
}
