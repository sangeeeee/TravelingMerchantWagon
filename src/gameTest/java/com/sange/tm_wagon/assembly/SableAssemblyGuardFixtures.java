package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.material.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

final class SableAssemblyGuardFixtures {
    private static final WagonMaterial STYLE=new WagonMaterial(WoodMaterial.SPRUCE,DyeColor.BLUE);
    private static void clean(GameTestHelper h) {
        for(int x=1;x<25;x++)for(int y=2;y<20;y++)for(int z=1;z<25;z++)h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        for(var item:h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(1,1,1))),Vec3.atLowerCornerOf(h.absolutePos(new BlockPos(25,20,25))))))item.discard();
    }
    private static AssemblyFrameBlockEntity frame(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(12,4,16));h.getLevel().setBlock(pos,WagonContent.FRAME.get().defaultBlockState(),3);
        var f=(AssemblyFrameBlockEntity)h.getLevel().getBlockEntity(pos);h.assertTrue(f.initializeFrame()==null,"Frame failed");
        for(var e:WagonEntity.defaultParts().entrySet())h.assertTrue(f.install(e.getKey(),e.getValue(),null,STYLE.stack(WagonContent.PART_ITEMS.get(e.getValue()).get()))==null,"Part failed");
        return f;
    }
    private static List<BlockPos> only(AssemblyFrameBlockEntity f,WagonSlot slot) {
        return f.layout().entrySet().stream().filter(e->!e.getValue().framePart()&&e.getValue().slots().equals(Set.of(slot))).map(Map.Entry::getKey).toList();
    }
    private static List<ItemStack> drops(GameTestHelper h,BlockPos centre) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(centre).inflate(10)).stream().map(ItemEntity::getItem).toList();
    }
    private static int count(List<ItemStack> stacks,Item item) { return stacks.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum(); }
    private static void finish(GameTestHelper h,ServerSubLevel sub) {
        SubLevelContainer.getContainer(h.getLevel()).removeSubLevel(sub,SubLevelRemovalReason.REMOVED);h.succeed();
    }
    static void run(GameTestHelper h,int scenario) {
        clean(h);var level=h.getLevel();
        if(scenario==4||scenario==5||scenario==8) { placed(h,scenario);return; }
        if(scenario==9||scenario==10) { accessory(h,scenario);return; }
        var f=frame(h);var origin=f.getBlockPos();var selected=new ArrayList<BlockPos>();
        var expected=new ArrayList<ItemStack>();
        if(scenario==0||scenario==6) {
            var cells=only(f,WagonSlot.REAR_LEFT);h.assertTrue(cells.size()>1,"Wheel needs multiple cells");selected.addAll(cells);
            expected.add(f.partStack(WagonSlot.REAR_LEFT));
        } else if(scenario==1) {
            for(var slot:List.of(WagonSlot.REAR_LEFT,WagonSlot.SHAFTS,WagonSlot.REAR_RIGHT)) {
                var cells=only(f,slot);h.assertTrue(!cells.isEmpty(),"No exclusive component cells: "+slot);selected.addAll(cells);expected.add(f.partStack(slot));
            }
        } else {
            selected.addAll(scenario==2?only(f,WagonSlot.BODY):f.layout().keySet());
            if(scenario!=2)selected.add(origin);
            f.parts().keySet().forEach(slot->expected.add(f.partStack(slot)));
        }
        h.assertTrue(!selected.isEmpty(),"No selected cells");
        if(scenario==2||scenario==3||scenario==7) {
            var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);p.setPos(f.cargoPose().point(new Vec3(-3,0,-1.8)));
            h.assertTrue(f.cargo().cabinet().install(STYLE.stack(WagonContent.CABINET.get()),p,new Vec3(-8.5/16,1.7,-1.8))==null,"Cabinet failed");
            f.cargo().cabinet().inventory().setItem(0,new ItemStack(Items.EMERALD,11));
            if(scenario!=1) {
                h.assertTrue(f.cargo().place(4,new ItemStack(Items.CHEST),p)==null,"Chest failed");
                f.cargo().entry(4).inventory.setItem(0,new ItemStack(Items.DIAMOND,19));
                h.assertTrue(f.cargo().cover().install(STYLE.stack(WagonContent.CARGO_COVER.get()),p,new Vec3(-1.15625,1.9,.5))==null,"Cover failed");
            }
        }
        var anchor=selected.getFirst();var stone=origin.offset(5,0,0);
        if(scenario!=6) {level.setBlock(stone,Blocks.STONE.defaultBlockState(),3);selected.add(stone);}
        // Repeated positions and several cells of the same component must not duplicate drops.
        selected.add(anchor);Collections.reverse(selected);
        boolean oldDrops=level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        if(scenario==7)level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(false,level.getServer());
        ServerSubLevel sub;
        try { sub=SubLevelAssemblyHelper.assembleBlocks(level,anchor,selected,BoundingBox3i.from(selected)); }
        finally {level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(oldDrops,level.getServer());}
        try {
            var plotAnchor=sub.getPlot().getCenterBlock();
            for(var pos:selected) {
                var block=level.getBlockState(plotAnchor.offset(pos.subtract(anchor))).getBlock();
                h.assertTrue(!(block instanceof AssemblyFrameBlock)&&!(block instanceof AssemblyPartBlock),"Wagon cell copied into structure");
            }
            if(scenario!=6)h.assertTrue(level.getBlockState(plotAnchor.offset(stone.subtract(anchor))).is(Blocks.STONE),"Unrelated stone was lost");
            var found=drops(h,origin);
            if(scenario==7)h.assertTrue(found.isEmpty(),"Disabled block drops still emitted items");
            else {
                for(var stack:expected) {
                    int wanted=expected.stream().filter(s->ItemStack.isSameItemSameComponents(s,stack)).mapToInt(ItemStack::getCount).sum();
                    int actual=found.stream().filter(s->ItemStack.isSameItemSameComponents(s,stack)).mapToInt(ItemStack::getCount).sum();
                    h.assertTrue(actual==wanted,"Wrong component count/material: "+stack+" expected="+wanted+" actual="+actual);
                }
                int total=expected.size();
                if(scenario==2||scenario==3) {
                    h.assertTrue(count(found,WagonContent.CABINET.get())==1&&count(found,Items.EMERALD)==11,"Cabinet or contents duplicated/lost");total+=12;
                }
                if(scenario==2||scenario==3) {
                    h.assertTrue(count(found,Items.CHEST)==1&&count(found,Items.DIAMOND)==19&&count(found,WagonContent.CARGO_COVER.get())==1,"Cargo cascade duplicated/lost");total+=21;
                }
                if(scenario==3)total++;
                h.assertTrue(found.stream().mapToInt(ItemStack::getCount).sum()==total,"Unexpected extra drops: "+found);
            }
            h.assertTrue((scenario==3||scenario==7)?level.getBlockState(origin).isAir():level.getBlockEntity(origin)==f,"Wrong controller removal");
            if(scenario==2)h.assertTrue(f.parts().isEmpty()&&f.cargo().empty(),"Body removal retained cargo or parts");
            if(scenario==0||scenario==1||scenario==6)h.assertTrue(f.has(WagonSlot.BODY),"Partial removal destroyed unrelated body");
            // The authoritative state has already been consumed, even if the same API is retried.
            var before=found.stream().mapToInt(ItemStack::getCount).sum();f.validateLoadedCells();
            h.assertTrue(drops(h,origin).stream().mapToInt(ItemStack::getCount).sum()==before,"Later validation duplicated drops");
        } finally {SubLevelContainer.getContainer(level).removeSubLevel(sub,SubLevelRemovalReason.REMOVED);}
        h.succeed();
    }
    private static void placed(GameTestHelper h,int scenario) {
        var level=h.getLevel();var stone=h.absolutePos(new BlockPos(12,4,16));level.setBlock(stone,Blocks.STONE.defaultBlockState(),3);
        var sub=SubLevelAssemblyHelper.assembleBlocks(level,stone,List.of(stone),BoundingBox3i.from(List.of(stone)));
        var plot=sub.getPlot().getCenterBlock();var target=plot.above();
        if(scenario==4) {
            var p=h.makeMockServerPlayerInLevel();p.setPos(Vec3.atCenterOf(stone).add(4,3,0));p.getAbilities().instabuild=false;
            var stack=new ItemStack(WagonContent.FRAME_ITEM.get(),2);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
            var context=new BlockPlaceContext(level,p,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(plot).add(0,.5,0),Direction.UP,plot,false));
            var result=((AssemblyFrameItem)stack.getItem()).place(context);
            h.assertTrue(result.consumesAction()&&stack.getCount()==1,"Structure placement was not consumed exactly once: "+result+" count="+stack.getCount());
            h.assertTrue(level.getBlockState(target).isAir(),"Placed frame remained physical");
            h.assertTrue(count(drops(h,stone),WagonContent.FRAME_ITEM.get())==1,"Placed frame not returned at visible world position");
            finish(h,sub);
        } else if(scenario==8) {
            var f=frame(h);var cells=only(f,WagonSlot.REAR_LEFT);h.assertTrue(cells.size()>1,"No multiple proxy cells");
            var expected=f.partStack(WagonSlot.REAR_LEFT);var imported=new ArrayList<BlockPos>();
            for(int i=0;i<cells.size();i++) {
                var old=cells.get(i);var destination=target.above(i);imported.add(destination);
                var data=level.getBlockEntity(old).saveWithoutMetadata(level.registryAccess());
                level.setBlock(destination,level.getBlockState(old),3);level.getBlockEntity(destination).loadWithComponents(data,level.registryAccess());
            }
            h.runAfterDelay(3,()->{
                try {
                    for(var pos:imported)h.assertTrue(level.getBlockState(pos).isAir(),"Raw imported proxy survived");
                    h.assertTrue(!f.has(WagonSlot.REAR_LEFT)&&f.has(WagonSlot.BODY),"Raw proxy cleanup removed wrong modules");
                    var found=drops(h,f.getBlockPos());
                    h.assertTrue(found.stream().filter(s->ItemStack.isSameItemSameComponents(s,expected)).mapToInt(ItemStack::getCount).sum()==1,"Raw multi-cell import duplicated/lost component");
                } finally {SubLevelContainer.getContainer(level).removeSubLevel(sub,SubLevelRemovalReason.REMOVED);}
                h.succeed();
            });
        } else {
            // Simulate a schematic/command loading BE data after setting its block.
            level.setBlock(target,WagonContent.FRAME.get().defaultBlockState(),3);
            var f=(AssemblyFrameBlockEntity)level.getBlockEntity(target);
            var tag=f.saveWithoutMetadata(level.registryAccess());var modules=new net.minecraft.nbt.CompoundTag();modules.putString("BODY",WagonPart.CARGO_BODY.name());tag.put("Modules",modules);
            f.loadWithComponents(tag,level.registryAccess());
            h.runAfterDelay(3,()->{
                try {
                    h.assertTrue(level.getBlockState(target).isAir(),"Imported frame escaped deferred cleanup");
                    var found=drops(h,stone);h.assertTrue(count(found,WagonContent.FRAME_ITEM.get())==1&&count(found,WagonContent.PART_ITEMS.get(WagonPart.CARGO_BODY).get())==1,"Imported controller contents lost or duplicated");
                } finally {SubLevelContainer.getContainer(level).removeSubLevel(sub,SubLevelRemovalReason.REMOVED);}
                h.succeed();
            });
        }
    }
    private static void accessory(GameTestHelper h,int scenario) {
        var f=frame(h);var level=h.getLevel();var p=h.makeMockServerPlayerInLevel();p.setNoGravity(true);p.setPos(f.cargoPose().point(new Vec3(-3,0,0)));
        if(scenario==9)h.assertTrue(f.cargo().canopy().install(STYLE.stack(WagonContent.CANOPY.get()),p,new Vec3(-1.15625,1.9,.5))==null,"Canopy failed");
        else {
            h.assertTrue(f.cargo().place(4,new ItemStack(Items.CHEST),p)==null,"Chest failed");
            f.cargo().entry(4).inventory.setItem(0,new ItemStack(Items.DIAMOND,13));
        }
        var base=WagonGeometry.customCells(f.cargo().structuralBodyBoxes(f.cargoBody()),f.facing()).keySet();
        var selected=f.layout().entrySet().stream().filter(e->!e.getValue().framePart()&&e.getValue().slots().equals(Set.of(WagonSlot.BODY))
            &&!base.contains(e.getKey().subtract(f.getBlockPos()))&&(scenario!=9||e.getKey().getY()>f.getBlockPos().getY()+3)).map(Map.Entry::getKey).toList();
        h.assertTrue(!selected.isEmpty(),"No exclusive accessory cells");var anchor=selected.getFirst();
        var sub=SubLevelAssemblyHelper.assembleBlocks(level,anchor,selected,BoundingBox3i.from(selected));
        try {
            h.assertTrue(f.parts().size()==7&&f.has(WagonSlot.BODY),"Accessory-only selection destroyed unrelated parts");
            var found=drops(h,f.getBlockPos());
            if(scenario==9)h.assertTrue(!f.cargo().canopy().installed()&&count(found,WagonContent.CANOPY.get())==1&&found.stream().mapToInt(ItemStack::getCount).sum()==1,"Roof drop duplicated or cascaded");
            else h.assertTrue(f.cargo().entry(4)==null&&count(found,Items.CHEST)==1&&count(found,Items.DIAMOND)==13&&found.stream().mapToInt(ItemStack::getCount).sum()==14,"Cargo drop duplicated or cascaded");
        } finally {SubLevelContainer.getContainer(level).removeSubLevel(sub,SubLevelRemovalReason.REMOVED);}
        h.succeed();
    }
}
