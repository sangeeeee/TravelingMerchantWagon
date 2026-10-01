package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.entity.*;
import com.sange.tm_wagon.physics.*;
import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class WagonDrivingGameTests {
    private static void floor(GameTestHelper h) {
        for(int x=1;x<24;x++)for(int z=1;z<24;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
    }
    private static WagonEntity wagon(GameTestHelper h,boolean doubleSeat,boolean doubleHorse) {
        floor(h);var wagon=WagonContent.WAGON.get().create(h.getLevel());
        var parts=new EnumMap<WagonSlot,WagonPart>(WagonSlot.class);parts.putAll(WagonEntity.defaultParts());
        if(doubleSeat)parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
        if(doubleHorse)parts.put(WagonSlot.SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS);
        wagon.configure(parts,Direction.NORTH);wagon.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(wagon);return wagon;
    }
    private static AbstractHorse attach(GameTestHelper h,WagonEntity wagon,Player player,int slot) {
        AbstractHorse horse=(slot==0?EntityType.HORSE:EntityType.DONKEY).create(h.getLevel());
        horse.setNoAi(true);horse.setPos(wagon.horsePosition(slot));h.getLevel().addFreshEntity(horse);horse.setLeashedTo(player,true);
        String error=wagon.attachHorse(player,horse,slot);h.assertTrue(error==null,"Horse binding failed: "+error);return horse;
    }
    private static Player driver(GameTestHelper h,WagonEntity wagon) {
        Player p=h.makeMockPlayer(GameType.SURVIVAL);h.assertTrue(p.startRiding(wagon),"Driver did not board");return p;
    }
    private static void drive(WagonEntity wagon,Player player,int forward,int steer,int ticks) {
        for(int i=0;i<ticks;i++) { wagon.acceptInput(player,forward,steer);wagon.tick(); }
    }
    private static int leads(GameTestHelper h,WagonEntity wagon) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,wagon.getBoundingBox().inflate(15)).stream()
            .filter(e->e.getItem().is(Items.LEAD)).mapToInt(e->e.getItem().getCount()).sum();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void left_driver_only_and_no_sideways_motion(GameTestHelper h) {
        var w=wagon(h,true,false);var right=h.makeMockPlayer(GameType.SURVIVAL);var left=h.makeMockPlayer(GameType.SURVIVAL);
        w.interactAt(right,new Vec3(.45,2.2,-1.875),InteractionHand.MAIN_HAND);attach(h,w,right,0);
        Vec3 start=w.position();drive(w,right,1,1,8);h.assertTrue(w.position().distanceToSqr(start)<.001&&w.driver()==null,"Right seat controlled wagon");
        w.interactAt(left,new Vec3(-.45,2.2,-1.875),InteractionHand.MAIN_HAND);h.assertTrue(w.driver()==left,"Left seat is not driver");
        drive(w,left,0,1,10);h.assertTrue(w.position().distanceToSqr(start)<.001&&Math.abs(w.getYRot()-180)<.001,"Steering strafed or spun stationary wagon");
        drive(w,left,1,0,10);h.assertTrue(w.getZ()<start.z-1&&w.getX()>=start.x,"Forward driving failed");
        left.stopRiding();Vec3 parked=w.position();drive(w,right,1,0,5);h.assertTrue(w.position().distanceToSqr(parked)<.001,"Right seat took over absent driver");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void forward_reverse_turn_and_distance_matched_wheels(GameTestHelper h) {
        h.assertTrue(Math.abs(WagonPhysics.FORWARD_SPEED-.234)<1e-9&&Math.abs(WagonPhysics.REVERSE_SPEED-.0585)<1e-9,"Speeds were not increased by 1.3");
        var w=wagon(h,false,false);var p=driver(h,w);attach(h,w,p,0);Vec3 start=w.position();
        drive(w,p,1,0,12);double forward=start.z-w.getZ();
        h.assertTrue(Math.abs(forward-12*WagonPhysics.FORWARD_SPEED)<.025,"Wrong forward speed: "+forward);
        h.assertTrue(Math.abs(w.renderWheel(0,1)+forward/WagonPhysics.radius(0))<.025,"Front wheel slides");
        h.assertTrue(Math.abs(w.renderWheel(2,1)+forward/WagonPhysics.radius(2))<.025,"Rear wheel slides");
        double previous=w.getZ();drive(w,p,-1,0,12);double reverse=w.getZ()-previous;
        h.assertTrue(Math.abs(reverse-12*WagonPhysics.REVERSE_SPEED)<.025&&reverse<forward/3,"Reverse speed incorrect");
        float yaw=w.getYRot();drive(w,p,1,1,8);h.assertTrue(w.getYRot()>yaw+2,"Moving steering did not turn");
        drive(w,p,-1,1,8);h.assertTrue(w.getYRot()<yaw+15,"Reverse steering direction incorrect");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void adults_only_double_hitches_scissors_and_single_refunds(GameTestHelper h) {
        var w=wagon(h,false,true);var p=driver(h,w);var a=attach(h,w,p,0);
        h.assertTrue(!w.readyToPull(),"Half-filled double hitch became powered");var b=attach(h,w,p,1);h.assertTrue(w.readyToPull(),"Two horses did not power wagon");
        Vec3 pos=a.position();a.travel(new Vec3(1,0,1));h.assertTrue(a.position().equals(pos),"Attached horse moved independently");
        var baby=EntityType.HORSE.create(h.getLevel());baby.setAge(-100);h.assertTrue(!HorseHarness.eligible(baby),"Baby can pull wagon");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.SHEARS));
        var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(p,InteractionHand.MAIN_HAND,a);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        h.assertTrue(event.isCanceled()&&!w.hasHorse(a.getUUID())&&!HorseHarness.attached(a)&&!a.isNoGravity(),"Scissors did not restore horse control");
        h.assertTrue(leads(h,w)==1,"Scissors refund duplicated or missing");w.discard();h.assertTrue(!HorseHarness.attached(b)&&leads(h,w)==2,"Destroy cleanup lost or duplicated leads");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void climbs_and_descends_single_block_with_pitch(GameTestHelper h) {
        var w=wagon(h,false,false);var p=driver(h,w);
        for(int x=1;x<24;x++)for(int z=1;z<=14;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
        attach(h,w,p,0);float maximum=0;
        for(int i=0;i<48;i++) { drive(w,p,1,0,1);maximum=Math.max(maximum,w.pitch()); }
        double top=h.absolutePos(new BlockPos(0,3,0)).getY();
        h.assertTrue(w.getZ()<h.absolutePos(new BlockPos(0,10,11)).getZ(),"Wagon could not climb step: "+w.position());
        h.assertTrue(Math.abs(w.getY()-top)<.2&&maximum>.05&&maximum<=WagonPhysics.NORMAL_PITCH+.01,"Uphill height/pitch incorrect: "+w.getY()+" pitch "+maximum);
        // Reverse over the same edge: back wheels descend first.
        float minimum=0;for(int i=0;i<205;i++) { drive(w,p,-1,0,1);minimum=Math.min(minimum,w.pitch()); }
        h.assertTrue(!w.falling()&&Math.abs(w.roll())<.05,"Gentle descent became uncontrolled fall");
        h.assertTrue(Math.abs(w.getY()-(top-1))<.2,"Descent did not return to lower ground: "+w.position());h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void lateral_slope_and_fence_obstruction(GameTestHelper h) {
        var w=wagon(h,false,false);var p=driver(h,w);
        for(int x=12;x<24;x++)for(int z=1;z<24;z++)h.setBlock(new BlockPos(x,2,z),Blocks.STONE);
        attach(h,w,p,0);drive(w,p,0,0,12);
        h.assertTrue(w.roll()>.05&&w.roll()<=WagonPhysics.NORMAL_ROLL+.01,"Uneven wheels did not roll body: "+w.roll());
        for(int x=1;x<24;x++)h.setBlock(new BlockPos(x,3,10),Blocks.OAK_FENCE);
        drive(w,p,1,0,45);h.assertTrue(w.horsePosition(0).z>h.absolutePos(new BlockPos(0,0,10)).getZ()+1,"Horse crossed fence");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void unsupported_horse_times_out_without_suspending_wagon(GameTestHelper h) {
        var w=wagon(h,false,false);var p=driver(h,w);var horse=attach(h,w,p,0);Vec3 initial=w.position();
        cliff(h,13);for(int i=0;i<20;i++)w.tick();
        h.assertTrue(w.hasHorse(horse.getUUID())&&w.shaftPitch()<-.1&&Math.abs(w.getY()-initial.y)<.05,"Horse did not hang on lowered shafts");
        for(int i=0;i<25;i++)w.tick();
        h.assertTrue(!HorseHarness.attached(horse)&&!w.hasHorse(horse.getUUID())&&leads(h,w)==1,"Hanging horse did not detach once");
        double y=horse.getY();horse.setNoAi(false);for(int i=0;i<3;i++)horse.travel(Vec3.ZERO);h.assertTrue(horse.getY()<y,"Detached horse did not fall");h.succeed();
    }
    private static void cliff(GameTestHelper h,int edge) {
        for(int x=1;x<24;x++)for(int z=1;z<=edge;z++) {
            for(int y=-4;y<=1;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
            h.setBlock(new BlockPos(x,-4,z),Blocks.STONE);
        }
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void front_wheels_over_cliff_tip_fall_and_land(GameTestHelper h) {
        var w=wagon(h,false,false);var p=driver(h,w);attach(h,w,p,0);cliff(h,13);
        w.setPos(w.position().add(0,0,-3));float lowestPitch=0;boolean fell=false;
        for(int i=0;i<90;i++) { w.tick();lowestPitch=Math.min(lowestPitch,w.pitch());fell|=w.falling(); }
        double landing=h.absolutePos(new BlockPos(0,-3,0)).getY();
        h.assertTrue(fell&&lowestPitch<-.2,"Front unsupported wagon never tipped");
        h.assertTrue(w.getY()<h.absolutePos(new BlockPos(0,0,0)).getY(),"Rear support kept wagon suspended: "+w.position());
        h.assertTrue(!w.falling()&&Math.abs(w.getY()-landing)<.35,"Wagon did not settle after fall: "+w.position()+" pitch "+w.pitch()+" mask "+w.supportMask());h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void save_links_pose_and_damage_drops_materials(GameTestHelper h) {
        var w=wagon(h,false,false);var p=driver(h,w);var horse=attach(h,w,p,0);drive(w,p,1,1,5);
        var saved=new net.minecraft.nbt.CompoundTag();w.saveWithoutId(saved);var copy=WagonContent.WAGON.get().create(h.getLevel());copy.load(saved);
        h.assertTrue(copy.hasHorse(horse.getUUID())&&Math.abs(copy.getYRot()-w.getYRot())<.001,"Save lost horse or continuous heading");
        w.hurt(w.damageSources().generic(),5);h.assertTrue(!w.isRemoved(),"Wagon is no stronger than a boat");w.hurt(w.damageSources().generic(),15);
        h.assertTrue(w.isRemoved()&&!p.isPassenger()&&!HorseHarness.attached(horse)&&leads(h,w)==1,"Destruction cleanup incorrect");
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(w.position(),w.position()).inflate(10));
        h.assertTrue(drops.stream().anyMatch(e->e.getItem().is(Items.OAK_PLANKS))&&drops.stream().anyMatch(e->e.getItem().is(Items.GREEN_WOOL)),"Destruction lacks wood/wool");
        h.assertTrue(drops.stream().noneMatch(e->e.getItem().getItem() instanceof WagonPartItem),"Destroyed wagon returned complete components");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void rotated_collision_and_wall_stop(GameTestHelper h) {
        var w=wagon(h,false,false);var p=driver(h,w);attach(h,w,p,0);
        w.applyPose(new WagonPose(w.position(),215,.12F,.08F));
        Vec3 floor=w.pose().point(new Vec3(0,1.43,0));h.assertTrue(w.intersects(new AABB(floor,floor).inflate(.025)),"Rotated floor lost collision");
        Vec3 interior=w.pose().point(new Vec3(0,1.85,0));h.assertTrue(!w.intersects(new AABB(interior,interior).inflate(.02)),"Rotated cargo interior became solid");
        w.applyPose(new WagonPose(w.position(),180,0,0));
        for(int x=1;x<24;x++)for(int y=2;y<7;y++)h.setBlock(new BlockPos(x,y,10),Blocks.STONE);
        drive(w,p,1,0,35);h.assertTrue(w.getZ()>h.absolutePos(new BlockPos(0,0,15)).getZ(),"Wagon shafts passed through wall");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=80)
    public static void block_conversion_releases_horse_without_duplicate_rope(GameTestHelper h) {
        floor(h);BlockPos root=h.absolutePos(new BlockPos(11,2,17));h.getLevel().setBlock(root,WagonContent.FRAME.get().defaultBlockState(),3);
        var frame=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(root);h.assertTrue(frame.initializeFrame()==null,"Frame failed");
        var parts=WagonEntity.defaultParts();
        for(WagonSlot slot:WagonSlot.values()) {
            var part=parts.get(slot);h.assertTrue(frame.install(slot,part,null,new ItemStack(WagonContent.PART_ITEMS.get(part).get()))==null,"Part failed");
        }
        h.assertTrue(frame.toggleFrame(null)==null,"Assembly failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(root).inflate(3)).getFirst();var p=driver(h,w);var horse=attach(h,w,p,0);
        h.runAtTickTime(24,()->h.assertTrue(frame.toggleFrame(null)==null,"Bound horse blocked lift animation"));
        h.runAtTickTime(49,()->{
            h.assertTrue(w.isRemoved()&&!HorseHarness.attached(horse)&&!p.isPassenger(),"Conversion kept passenger/horse attached");
            h.assertTrue(leads(h,w)==1&&h.getLevel().noCollision(horse,horse.getBoundingBox()),"Conversion lost/doubled rope or trapped horse");
            h.assertTrue(AssemblyFrameBlockEntity.complete(frame.parts()),"Conversion lost parts");h.succeed();
        });
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void wheel_contact_limits_cannot_climb_two_blocks(GameTestHelper h) {
        var w=wagon(h,false,false);var p=driver(h,w);attach(h,w,p,0);
        for(int x=1;x<24;x++)for(int y=2;y<=3;y++)h.setBlock(new BlockPos(x,y,10),Blocks.STONE);
        drive(w,p,1,0,35);h.assertTrue(w.getZ()>h.absolutePos(new BlockPos(0,0,15)).getZ(),"Climbed a two-block obstacle");h.succeed();
    }

    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void one_front_wheel_missing_keeps_support_and_releases_hanging_horse(GameTestHelper h) {
        var w=wagon(h,false,false);var p=driver(h,w);var horse=attach(h,w,p,0);cliff(h,13);
        for(int x=9;x<=10;x++)for(int z=15;z<=16;z++)for(int y=-4;y<=1;y++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        for(int i=0;i<45;i++)w.tick();
        h.assertTrue(!w.falling()&&w.supportMask()==14,"One missing front wheel caused forward collapse");
        h.assertTrue(!HorseHarness.attached(horse)&&leads(h,w)==1,"One-wheel support changed hanging timeout/refund");h.succeed();
    }

    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void moving_wagon_cannot_pass_through_parked_wagon(GameTestHelper h) {
        var moving=wagon(h,false,false);var p=driver(h,moving);attach(h,moving,p,0);
        var parked=WagonContent.WAGON.get().create(h.getLevel());parked.configure(WagonEntity.defaultParts(),Direction.NORTH);
        parked.setPos(moving.position().add(0,0,-12));h.getLevel().addFreshEntity(parked);
        drive(moving,p,1,0,80);h.assertTrue(moving.getZ()>parked.getZ()+3.5,"Moving wagon passed through parked vehicle");h.succeed();
    }

    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void continuous_one_block_stairs(GameTestHelper h) { continuousStairs(h,1); }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void continuous_spaced_one_block_stairs(GameTestHelper h) { continuousStairs(h,2); }
    private static void continuousStairs(GameTestHelper h,int spacing) {
        var w=wagon(h,false,false);var p=driver(h,w);var horse=attach(h,w,p,0);
        for(int x=1;x<24;x++)for(int z=1;z<=11;z++) {
            int height=Math.min(3,1+(11-z)/spacing);
            for(int y=2;y<2+height;y++)h.setBlock(new BlockPos(x,y,z),Blocks.STONE);
        }
        drive(w,p,1,0,45);
        h.assertTrue(w.getZ()<h.absolutePos(new BlockPos(0,0,8)).getZ(),"Continuous stairs stalled: "+w.position()+" pitch "+w.pitch()+" mask "+w.supportMask()+" horse "+horse.position());
        h.assertTrue(w.hasHorse(horse.getUUID())&&!w.falling(),"Continuous one-block steps lost traction or became a cliff");
        h.assertTrue(Math.abs(horse.getY()-h.absolutePos(new BlockPos(0,5,0)).getY())<.01,"Horse did not climb successive steps");
        h.assertTrue(w.getY()>h.absolutePos(new BlockPos(0,4,0)).getY(),"Wagon body did not follow the ascending horses");
        // Terrain contacts must survive saving while the rear wheels hang above earlier stairs.
        var saved=new net.minecraft.nbt.CompoundTag();w.saveWithoutId(saved);w.load(saved);drive(w,p,0,0,1);
        h.assertTrue(!w.falling(),"Reload forgot the independently tracked axle contacts");
        drive(w,p,-1,0,210);
        h.assertTrue(!w.falling()&&Math.abs(w.getY()-h.absolutePos(new BlockPos(0,2,0)).getY())<.2,"Continuous descent failed: "+w.position());h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void driver_recovers_sideways_without_horses(GameTestHelper h) { recover(h,0,(float)Math.PI/2); }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void driver_recovers_inverted_without_horses(GameTestHelper h) { recover(h,0,(float)Math.PI); }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void driver_recovers_front_tip_without_horses(GameTestHelper h) { recover(h,-1.2F,0); }
    private static void recover(GameTestHelper h,float pitch,float roll) {
        var w=wagon(h,false,false);var p=driver(h,w);Vec3 start=w.position();
        WagonPose tilted=new WagonPose(start,180,pitch,roll);
        double min=w.boxesAt(tilted).stream().mapToDouble(b->b.minY).min().orElseThrow();
        w.applyPose(new WagonPose(start.add(0,start.y-min+.001,0),180,pitch,roll));
        float yaw=w.getYRot();
        for(int i=0;i<180;i++) {
            drive(w,p,i%100<20?1:-1,1,1);
            for(var box:w.motionBoxesAt(w.pose()))h.assertTrue(box.minY>=start.y-.03,"Recovery penetrated floor: "+w.position()+" min "+box.minY+" tick "+i+" roll "+w.roll()+" pitch "+w.pitch());
        }
        h.assertTrue(Math.abs(w.pitch())<.15&&Math.abs(w.roll())<.15&&!w.falling(),"Driver could not recover tipped wagon: "+w.position()+" pitch "+w.pitch()+" roll "+w.roll());
        h.assertTrue(Math.abs(w.getYRot()-yaw)>.5,"Tipped wagon ignored steering");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void falling_wagon_still_accepts_driver_input(GameTestHelper h) {
        var w=wagon(h,false,false);var p=driver(h,w);attach(h,w,p,0);cliff(h,13);
        w.setPos(w.position().add(0,0,-3));w.tick();h.assertTrue(w.falling(),"Fixture did not start falling");
        float yaw=w.getYRot();double z=w.getZ();drive(w,p,-1,1,12);
        h.assertTrue(w.getYRot()<yaw-.5&&w.getZ()>z,"Falling wagon ignored reverse or steering input");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void airborne_unpowered_wagon_cannot_right_itself(GameTestHelper h) {
        var w=wagon(h,false,false);var p=driver(h,w);
        w.applyPose(new WagonPose(w.position().add(0,8,0),180,0,(float)Math.PI));
        double y=w.getY();drive(w,p,1,1,5);
        h.assertTrue(w.getY()<y&&Math.abs(w.roll()-(float)Math.PI)<.01&&Math.abs(w.getYRot()-180)<.01,"Recovery ignored gravity or allowed unpowered flight");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void lowering_shafts_does_not_turn_two_block_cliff_into_steps(GameTestHelper h) {
        var w=wagon(h,false,false);var p=driver(h,w);var horse=attach(h,w,p,0);cliff(h,13);
        for(int x=1;x<24;x++)for(int z=1;z<=13;z++)h.setBlock(new BlockPos(x,-1,z),Blocks.STONE);
        for(int i=0;i<45;i++)w.tick();
        h.assertTrue(!w.hasHorse(horse.getUUID())&&leads(h,w)==1&&!w.falling(),"Shaft motion gave hanging horse fictitious intermediate steps");h.succeed();
    }

}
