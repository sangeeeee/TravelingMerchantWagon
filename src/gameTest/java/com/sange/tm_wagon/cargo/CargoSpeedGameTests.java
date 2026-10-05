package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class CargoSpeedGameTests {
    private static WagonEntity wagon(GameTestHelper h,WagonPart body) {
        var w=WagonContent.WAGON.get().create(h.getLevel());var parts=WagonEntity.defaultParts();parts.put(WagonSlot.BODY,body);
        w.configure(parts,Direction.NORTH);w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,4,17))));return w;
    }
    @GameTest(template="assembly_test")
    public static void body_limits_and_absolute_cargo_penalty(GameTestHelper h) {
        double reference=WagonPhysics.FORWARD_SPEED;
        for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY}) {
            double factor=body==WagonPart.CARGO_BODY?1.2:body==WagonPart.LONG_CARGO_BODY?1:.8;
            double base=WagonSpeed.forward(body,0,false),empty=WagonSpeed.forward(body,0,true);
            h.assertTrue(Math.abs(base-reference*factor)<1e-10&&Math.abs(empty/base-1.8)<1e-10,"Incorrect size/empty boost limit");
            h.assertTrue(Math.abs(empty-WagonSpeed.forward(body,1,true)-.0117)<1e-10,"Slot penalty must be absolute, equal across sizes");
            double previous=empty;
            for(int used=0;used<=body.cargoCapacity();used++) {
                double limit=WagonSpeed.forward(body,used,true);
                h.assertTrue(limit>=base&&limit<=previous&&WagonSpeed.forward(body,used,false)==base,"Cargo lowered ordinary speed or raised boosted cap");previous=limit;
            }
            h.assertTrue(Math.abs(WagonSpeed.reverse(body)-WagonPhysics.REVERSE_SPEED*factor)<1e-10,"Reverse size limit incorrect");
        }
        h.assertTrue(Math.abs(WagonSpeed.forward(WagonPart.LONG_CARGO_BODY,12,true)/reference-1.2)<1e-10,"Full long wagon is not 1.2x");
        h.assertTrue(WagonSpeed.forward(WagonPart.WIDE_CARGO_BODY,32,true)==WagonSpeed.forward(WagonPart.WIDE_CARGO_BODY,0,false),"Full wide wagon fell below ordinary speed");
        var drive=new WagonDrive();drive.load(WagonSpeed.maxForward());
        h.assertTrue(drive.speed()==WagonSpeed.maxForward(),"Save/load truncated new top speed");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void occupied_cells_count_mats_and_survive_transfer_and_reload(GameTestHelper h) {
        for(var body:new WagonPart[]{WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY}) {
            var w=wagon(h,body);var hold=w.cargo();var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(w.position().add(-4,0,3));
            h.assertTrue(hold.place(2*hold.columns(),WagonContent.STRAW_MAT.get().getDefaultInstance(),p)==null,"Mat fixture failed");
            h.assertTrue(hold.place(1,Items.STONE.getDefaultInstance(),p)==null,"Block fixture failed");
            h.assertTrue(hold.place(3,WagonContent.STOOL.get().getDefaultInstance(),p)==null,"Stool fixture failed");
            h.assertTrue(hold.place(hold.capacity()-1,Items.CHEST.getDefaultInstance(),p)==null,"Chest fixture failed");
            hold.entry(hold.capacity()-1).inventory.setItem(0,new ItemStack(Items.DIAMOND,64));
            h.assertTrue(hold.occupiedSlots()==6,"Mat reservations or container items counted incorrectly");
            var target=wagon(h,body);hold.transferTo(target.cargo());
            h.assertTrue(hold.occupiedSlots()==0&&target.cargo().occupiedSlots()==6,"Transfer left stale occupancy");
            var saved=target.cargo().save(h.getLevel().registryAccess(),false);target.cargo().load(saved,h.getLevel().registryAccess());
            h.assertTrue(target.cargo().occupiedSlots()==6,"Reload changed occupied count");
            h.assertTrue(target.cargo().take(0,p)==null&&target.cargo().occupiedSlots()==3,"Removing mat must free all three cells");
        }h.succeed();
    }
}
