package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.entity.WagonSupport;
import com.sange.tm_wagon.physics.OrientedBox;
import com.sange.tm_wagon.physics.WagonPose;
import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class OrientedCollisionGameTests {
    private static WagonEntity wagon(GameTestHelper h) {
        for(int x=1;x<24;x++)for(int z=1;z<24;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(w);return w;
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void all_body_seat_and_shaft_variants_keep_unsplit_rotated_volumes(GameTestHelper h) {
        var w=wagon(h);
        var seatTypes=new WagonPart[]{WagonPart.SINGLE_SEAT,WagonPart.DOUBLE_SEAT,WagonPart.TRIPLE_SEAT,
            WagonPart.SINGLE_WOODEN_SEAT,WagonPart.DOUBLE_WOODEN_SEAT,WagonPart.TRIPLE_WOODEN_SEAT};
        for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY})
            for(var seat:seatTypes)for(var shafts:new WagonPart[]{WagonPart.SINGLE_HORSE_SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS}) {
                if(seat.seatCapacity()==3&&body!=WagonPart.WIDE_CARGO_BODY)continue;
                var parts=new EnumMap<WagonSlot,WagonPart>(WagonSlot.class);parts.putAll(WagonEntity.defaultParts());
                parts.put(WagonSlot.BODY,body);parts.put(WagonSlot.SEAT,seat);parts.put(WagonSlot.SHAFTS,shafts);
                w.configure(parts,Direction.NORTH);w.applyPose(new WagonPose(w.position(),135,0,0));
                int expected=parts.values().stream().mapToInt(p->WagonGeometry.partBoxes(p).size()).sum();
                h.assertTrue(w.colliders().size()==expected,"Part volumes were subdivided: "+body+" "+seat+" "+shafts);
                h.assertTrue(w.colliders()==w.colliders(),"Stationary OBB cache rebuilt per query");
                double z=body.firstRowZ()+1.4;
                Vec3 middle=w.pose().point(new Vec3(.45,1.85,z));AABB interior=new AABB(middle,middle).inflate(.08);
                h.assertTrue(!w.intersects(interior)&&h.getLevel().noCollision(null,interior),"45-degree bounds filled the cargo interior: "+body);
                Vec3 floor=w.pose().point(new Vec3(0,1.45,z));
                h.assertTrue(w.intersects(new AABB(floor,floor).inflate(.02)),"Rotated deck lost collision");
            }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void walk_slide_and_retreat_along_continuous_diagonal_walls(GameTestHelper h) {
        var w=wagon(h);var player=h.makeMockPlayer(GameType.SURVIVAL);
        for(float yaw:new float[]{165,150,135,120,105}) {
            w.applyPose(new WagonPose(w.position(),yaw,0,0));
            player.setPos(w.pose().point(new Vec3(.55,1.5,-.2)));player.setOnGround(true);
            for(int i=0;i<14;i++) {
                player.move(MoverType.SELF,w.pose().vector(new Vec3(.12,0,.07)).add(0,-.08,0));
                player.setDeltaMovement(Vec3.ZERO);
                h.assertTrue(!w.intersects(player.getBoundingBox().deflate(1e-5)),"Player penetrated diagonal wall at "+yaw);
            }
            Vec3 reached=w.pose().local(player.position());
            h.assertTrue(reached.z>.65&&reached.x<1,"World-axis teeth blocked sliding: "+yaw+" "+reached);
            h.assertTrue(player.onGround()&&WagonSupport.supportedByWagon(player),"Diagonal deck lost ground support");
            Vec3 before=player.position();player.move(MoverType.SELF,w.pose().vector(new Vec3(-.2,0,0)));
            h.assertTrue(player.position().distanceTo(before)>.19,"Player could not retreat from wall: "+yaw);
        }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void continuous_sweep_stops_at_thin_rotated_canvas_at_large_world_coordinates(GameTestHelper h) {
        var pose=new WagonPose(Vec3.atCenterOf(h.absolutePos(new BlockPos(11,7,17))),135,.13F,-.09F);
        var local=new AABB(-1.0/128,0,-5,1.0/128,3,5);
        var cloth=OrientedBox.at(local,pose);
        Vec3 from=pose.point(new Vec3(-3,1.5,0)),motion=pose.vector(new Vec3(6,0,0));
        var playerBox=new AABB(from,from).inflate(.3,.7,.3);
        var hit=cloth.sweep(playerBox,motion);
        h.assertTrue(hit!=null&&hit.time()>0&&hit.time()<.5,"Thin tilted panel allowed tunnelling");
        h.assertTrue(!cloth.intersects(playerBox.move(motion.scale(hit.time())).deflate(1e-5)),"Sweep contact penetrated canvas");
        h.assertTrue(cloth.intersects(playerBox.move(motion.scale(hit.time()+.01))),"Sweep reported a false contact");
        h.assertTrue(cloth.sweep(playerBox,motion.scale(-1))==null,"Moving away from panel was blocked");
        // The same mathematics must handle another wagon part, not only living-entity AABBs.
        var moving=OrientedBox.at(new AABB(-3.3,.8,-.3,-2.7,2.2,.3),pose);
        var rigidHit=cloth.sweep(moving,motion);
        h.assertTrue(rigidHit!=null&&rigidHit.time()<.5,"Rigid part sweep missed rotated canvas");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void full_wide_cargo_uses_one_rotated_box_per_cargo_slot(GameTestHelper h) {
        var w=wagon(h);var parts=new EnumMap<WagonSlot,WagonPart>(WagonSlot.class);parts.putAll(WagonEntity.defaultParts());
        parts.put(WagonSlot.BODY,WagonPart.WIDE_CARGO_BODY);parts.put(WagonSlot.SEAT,WagonPart.TRIPLE_SEAT);
        w.configure(parts,Direction.NORTH);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(w.position().add(-6,0,0));
        int empty=w.colliders().size();
        for(int i=0;i<w.cargo().capacity();i++)h.assertTrue(w.cargo().place(i,new ItemStack(Items.STONE),player)==null,"Cargo placement failed at "+i);
        w.applyPose(new WagonPose(w.position(),135,.12F,-.10F));
        h.assertTrue(w.colliders().size()==empty+32,"32 cargo slots produced extra collision tiles");
        for(int i=0;i<32;i++) {
            Vec3 centre=w.pose().point(w.cargo().slotBounds(i).getCenter());
            h.assertTrue(w.intersects(new AABB(centre,centre).inflate(.02)),"Rotated cargo slot lost collision: "+i);
        }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void walking_can_step_onto_a_half_block_stool_at_45_degrees(GameTestHelper h) {
        var w=wagon(h);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(w.position().add(-4,0,0));
        h.assertTrue(w.cargo().place(0,new ItemStack(WagonContent.STOOL.get()),player)==null,"Stool fixture failed");
        w.applyPose(new WagonPose(w.position(),135,0,0));
        player.setPos(w.pose().point(new Vec3(-.5,1.5,.08)));player.setOnGround(true);
        for(int i=0;i<6;i++)player.move(MoverType.SELF,w.pose().vector(new Vec3(0,0,-.14)).add(0,-.08,0));
        h.assertTrue(player.getY()>w.getY()+1.99,"Rotated half-block stool could not be stepped onto");
        h.assertTrue(!w.intersects(player.getBoundingBox().deflate(1e-5)),"Step penetrated stool or seat");h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void diagonal_vehicle_motion_stops_at_world_walls(GameTestHelper h) {
        var w=wagon(h);w.applyPose(new WagonPose(w.position(),135,0,0));
        for(int z=1;z<24;z++)for(int y=2;y<7;y++)h.setBlock(new BlockPos(6,y,z),Blocks.STONE);
        Vec3 start=w.position(),delta=w.pose().forward().scale(.8);
        try {
            var move=com.sange.tm_wagon.physics.WagonPhysics.class.getDeclaredMethod("move",WagonEntity.class,Vec3.class,float.class,float.class,float.class);
            move.setAccessible(true);var physics=new com.sange.tm_wagon.physics.WagonPhysics();
            for(int i=0;i<7;i++)move.invoke(physics,w,delta,135F,0F,0F);
        }catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        h.assertTrue(w.position().distanceTo(start)>.1&&w.position().distanceTo(start)<7*.8-.1,"World wall did not limit diagonal vehicle travel");
        for(var collider:w.motionCollidersAt(w.pose()))
            for(var shape:h.getLevel().getBlockCollisions(w,collider.bounds()))for(var block:shape.toAabbs())
                h.assertTrue(!collider.intersects(block),"Rotated vehicle part penetrated a world wall");h.succeed();
    }
    private static void accessories(GameTestHelper h,boolean canopy) {
        var pos=h.absolutePos(new BlockPos(11,2,17));
        h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(f.initializeFrame()==null,"Frame failed");
        var parts=new EnumMap<WagonSlot,WagonPart>(WagonSlot.class);parts.putAll(WagonEntity.defaultParts());parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);
        for(var entry:parts.entrySet())h.assertTrue(f.install(entry.getKey(),entry.getValue(),null,new ItemStack(WagonContent.PART_ITEMS.get(entry.getValue()).get()))==null,"Part install failed");
        var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(f.cargoPose().point(new Vec3(-5,0,3)));
        h.assertTrue(f.cargo().cabinet().install(new ItemStack(WagonContent.CABINET.get()),player,new Vec3(-15.5/16,1.7,-1.8))==null,"Cabinet failed");
        h.assertTrue(f.cargo().place(0,new ItemStack(WagonContent.STOOL.get()),player)==null,"Stool failed");
        h.assertTrue(f.cargo().place(8,new ItemStack(WagonContent.STRAW_MAT.get()),player)==null,"Mat failed");
        Vec3 side=new Vec3(-1.15625,1.9,.5);
        if(canopy) {
            h.assertTrue(f.cargo().canopy().install(new ItemStack(WagonContent.CANOPY.get()),player,side)==null,"Canopy failed");
            for(boolean front:new boolean[]{true,false})
                h.assertTrue(f.cargo().canopy().toggle(player,new Vec3(.82,2.8,f.cargo().canopy().curtainZ(f.cargoBody(),front)),front)==null,"Curtain failed");
        }else h.assertTrue(f.cargo().cover().install(new ItemStack(WagonContent.CARGO_COVER.get()),player,side)==null,"Cover failed");
        h.assertTrue(f.toggleFrame(null)==null,"Assembly failed");
        var w=h.getLevel().getEntitiesOfClass(WagonEntity.class,new AABB(pos).inflate(8)).getFirst();
        w.applyPose(new WagonPose(w.position(),135,.15F,-.1F));
        Vec3 cabinet=w.pose().point(w.cargo().cabinet().box().getCenter());
        h.assertTrue(w.cargo().cabinet().installed()&&w.intersects(new AABB(cabinet,cabinet).inflate(.02)),"Rotated cabinet lost its shared lower-seat collider");
        int original=parts.values().stream().mapToInt(p->WagonGeometry.partBoxes(p).size()).sum();
        int roof=(canopy?w.cargo().canopy().boxes(w.cargoBody()):w.cargo().cover().boxes(w.cargoBody())).size();
        h.assertTrue(w.colliders().size()==original+2+roof,"Optional components were subdivided or lost");
        for(AABB local:w.cargo().bodyBoxes()) {
            Vec3 centre=w.pose().point(local.getCenter());
            h.assertTrue(w.intersects(new AABB(centre,centre).inflate(.002)),"Optional rotated component lost collision");
        }
        if(canopy) {
            // Real Entity.move must stop at a thin curtain, without adding it to vanilla shapes.
            var pedestrian=h.makeMockPlayer(GameType.SURVIVAL);
            Vec3 start=w.pose().point(new Vec3(0,2.6,w.cargo().canopy().curtainZ(w.cargoBody(),false)+2));
            pedestrian.setPos(start);pedestrian.move(MoverType.SELF,w.pose().vector(new Vec3(0,0,-4)));
            h.assertTrue(pedestrian.position().distanceTo(start)<2.1,"Entity crossed closed rotated curtain");
        }
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void tilted_canopy_curtains_stool_and_mat_keep_compound_collision(GameTestHelper h) { accessories(h,true); }
    @GameTest(template="assembly_test",timeoutTicks=40)
    public static void tilted_cover_stool_and_mat_keep_compound_collision(GameTestHelper h) { accessories(h,false); }
}
