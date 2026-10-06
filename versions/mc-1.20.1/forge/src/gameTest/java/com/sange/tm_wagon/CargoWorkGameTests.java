package com.sange.tm_wagon;

import com.sange.tm_wagon.assembly.*;
import com.sange.tm_wagon.cargo.*;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraftforge.gametest.*;

/** Match cargo work blocks against independently ticking vanilla block entities. */
@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class CargoWorkGameTests {
    @GameTest(template="assembly_test",timeoutTicks=480)
    public static void block_cargo_furnaces_and_brewing_match_world_blocks(GameTestHelper h) {
        checkWorkBlocks(h,false);
    }
    @GameTest(template="assembly_test",timeoutTicks=480)
    public static void entity_cargo_furnaces_and_brewing_match_world_blocks(GameTestHelper h) {
        checkWorkBlocks(h,true);
    }
    private static void checkWorkBlocks(GameTestHelper h,boolean entity) {
        var wagon=entity?PortGameTests.wagon(h,WagonPart.CARGO_BODY):null;
        var hold=entity?wagon.cargo():InteractionGameTests.body(h,WagonPart.CARGO_BODY,net.minecraft.core.Direction.NORTH).cargo();
        var player=PortGameTests.player(h,hold);
        var blocks=new net.minecraft.world.level.block.Block[]{Blocks.FURNACE,Blocks.SMOKER,Blocks.BLAST_FURNACE,Blocks.BREWING_STAND};
        var inputs=new Item[]{Items.RAW_IRON,Items.PORKCHOP,Items.RAW_IRON};
        var fuels=new Item[]{Items.COAL,Items.COAL,Items.LAVA_BUCKET};
        var refs=new ArrayList<Container>();
        for(int i=0;i<blocks.length;i++) {
            h.assertTrue(hold.place(i,new ItemStack(blocks[i]),player)==null,"Work block placement failed: "+blocks[i]);
            var pos=h.absolutePos(new BlockPos(3+i*2,2,3));h.getLevel().setBlock(pos,blocks[i].defaultBlockState(),3);
            var reference=(Container)h.getLevel().getBlockEntity(pos);refs.add(reference);
            var inventory=hold.entry(i).inventory;
            if(i<3) {
                inventory.setItem(0,new ItemStack(inputs[i]));inventory.setItem(1,new ItemStack(fuels[i]));
                reference.setItem(0,new ItemStack(inputs[i]));reference.setItem(1,new ItemStack(fuels[i]));
            } else {
                // Nether wart transforms only water; an existing awkward potion
                // and an empty third slot must survive the same brewing batch.
                for(int bottle=0;bottle<2;bottle++) {
                    var potion=PotionUtils.setPotion(new ItemStack(Items.POTION),bottle==0?Potions.WATER:Potions.AWKWARD);
                    inventory.setItem(bottle,potion.copy());reference.setItem(bottle,potion.copy());
                }
                inventory.setItem(3,new ItemStack(Items.NETHER_WART));inventory.setItem(4,new ItemStack(Items.BLAZE_POWDER));
                reference.setItem(3,new ItemStack(Items.NETHER_WART));reference.setItem(4,new ItemStack(Items.BLAZE_POWDER));
            }
        }
        h.runAfterDelay(2,()->{
            for(int i=0;i<3;i++)h.assertTrue(hold.entry(i).burn>0&&hold.entry(i).cook>0
                &&((AbstractFurnaceBlockEntity)refs.get(i)).getBlockState().getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT),"Fuel did not ignite: "+blocks[i]);
            h.assertTrue(hold.entry(3).brewTime>0,"Brewing did not start");
        });
        h.runAfterDelay(420,()->{
            for(int i=0;i<refs.size();i++)for(int slot=0;slot<refs.get(i).getContainerSize();slot++) {
                var actual=hold.entry(i).inventory.getItem(slot);var expected=refs.get(i).getItem(slot);
                h.assertTrue(ItemStack.matches(actual,expected),"Work inventory differs from vanilla: "+blocks[i]+" slot "+slot+" actual="+actual+" expected="+expected);
            }
            h.assertTrue(hold.entry(0).inventory.getItem(2).is(Items.IRON_INGOT)
                &&hold.entry(1).inventory.getItem(2).is(Items.COOKED_PORKCHOP)
                &&hold.entry(2).inventory.getItem(2).is(Items.IRON_INGOT),"Cooking completed without output");
            h.assertTrue(PotionUtils.getPotion(hold.entry(3).inventory.getItem(0))==Potions.AWKWARD
                &&PotionUtils.getPotion(hold.entry(3).inventory.getItem(1))==Potions.AWKWARD
                &&hold.entry(3).inventory.getItem(2).isEmpty(),"Brewing lost an unmatched bottle");
            if(wagon!=null)wagon.discard();h.succeed();
        });
    }
}
