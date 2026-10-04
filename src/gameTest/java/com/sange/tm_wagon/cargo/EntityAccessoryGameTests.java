package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.material.*;
import com.sange.tm_wagon.physics.WagonPose;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class EntityAccessoryGameTests {
    private static void click(GameTestHelper h,WagonEntity w,ServerPlayer p,Vec3 local,boolean sneak) {
        Vec3 target=w.pose().point(local),eye=w.pose().point(local.add(-2,0,0));p.setPos(eye.subtract(0,p.getEyeHeight(),0));
        Vec3 d=target.subtract(eye);p.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));p.setShiftKeyDown(sneak);
        var hit=w.pick(eye,eye.add(d.normalize().scale(p.entityInteractionRange())));
        h.assertTrue(hit.isPresent(),"Accessory side was not pickable");
        h.assertTrue(w.interactAt(p,hit.orElseThrow().subtract(w.position()),InteractionHand.MAIN_HAND).consumesAction(),"Entity accessory click was not handled");
    }
    private static int carried(ServerPlayer p,Item item) { return p.getInventory().items.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    @GameTest(template="assembly_test")
    public static void tilted_entity_accessories_install_remove_with_materials_and_inventory(GameTestHelper h) {
        var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);p.getAbilities().instabuild=false;
        for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY}) {
            p.getInventory().clearContent();
            var w=WagonContent.WAGON.get().create(h.getLevel());var parts=WagonEntity.defaultParts();parts.put(WagonSlot.BODY,body);
            parts.put(WagonSlot.SEAT,body==WagonPart.WIDE_CARGO_BODY?WagonPart.TRIPLE_SEAT:body==WagonPart.LONG_CARGO_BODY?WagonPart.DOUBLE_SEAT:WagonPart.SINGLE_SEAT);
            w.configure(parts,Direction.NORTH);w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,3,17))));h.getLevel().addFreshEntity(w);
            w.applyPose(new WagonPose(w.position(),225,.1F,.08F));var hold=w.cargo();
            var cabinet=new WagonMaterial(WoodMaterial.SPRUCE,DyeColor.WHITE);var stack=cabinet.stack(WagonContent.CABINET.get());stack.setCount(2);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var box=hold.cabinet().box();var end=new Vec3(box.minX,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);
            click(h,w,p,end,false);h.assertTrue(hold.cabinet().installed()&&stack.getCount()==1&&hold.cabinet().material().equals(cabinet),"Entity cabinet install lost item/material");
            hold.cabinet().inventory().setItem(0,new ItemStack(Items.DIAMOND,17));click(h,w,p,end,false);var menu=p.containerMenu;
            h.assertTrue(menu!=p.inventoryMenu,"Entity cabinet could not open");click(h,w,p,end,true);
            h.assertTrue(!hold.cabinet().installed()&&carried(p,WagonContent.CABINET.get())==2&&p.containerMenu==p.inventoryMenu&&!menu.stillValid(p),"Cabinet removal duplicated component or retained menu");
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,w.getBoundingBox().inflate(5),e->e.getItem().is(Items.DIAMOND));
            h.assertTrue(drops.stream().mapToInt(e->e.getItem().getCount()).sum()==17,"Cabinet removal lost/duplicated contents");drops.forEach(e->e.discard());
            var cloth=new WagonMaterial(WoodMaterial.OAK,DyeColor.BLUE);Vec3 side=new Vec3(-1.15625*body.widthScale(),1.9,.5);
            p.setItemInHand(InteractionHand.MAIN_HAND,cloth.stack(WagonContent.CARGO_COVER.get()));click(h,w,p,side,false);
            h.assertTrue(hold.cover().installed()&&p.getMainHandItem().isEmpty()&&hold.cover().material().equals(cloth),"Entity cover install failed");
            var canopy=cloth.stack(WagonContent.CANOPY.get());h.assertTrue(hold.canopy().install(canopy,p,side)!=null&&canopy.getCount()==1&&!hold.canopy().installed(),"Entity roof exclusivity lost");
            click(h,w,p,side,true);h.assertTrue(!hold.cover().installed()&&carried(p,WagonContent.CARGO_COVER.get())==1,"Entity cover removal failed");
            p.setItemInHand(InteractionHand.MAIN_HAND,canopy);click(h,w,p,side,false);
            h.assertTrue(hold.canopy().installed()&&canopy.isEmpty()&&hold.canopy().material().equals(cloth),"Entity canopy install failed");
            click(h,w,p,side,true);h.assertTrue(!hold.canopy().installed()&&carried(p,WagonContent.CANOPY.get())==1,"Entity canopy removal failed");
            w.discard();
        }
        h.succeed();
    }
}
