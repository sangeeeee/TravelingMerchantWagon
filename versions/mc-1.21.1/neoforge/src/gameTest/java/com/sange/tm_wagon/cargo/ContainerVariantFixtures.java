package com.sange.tm_wagon.cargo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Test-only cosmetic and deliberately incompatible container subclasses. */
@EventBusSubscriber(modid="tm_wagon",bus=EventBusSubscriber.Bus.MOD)
public final class ContainerVariantFixtures {
    public static Block CHEST,BARREL,LARGE,CUSTOM;
    public static BlockEntityType<CosmeticChest> CHEST_TYPE;
    public static BlockEntityType<CosmeticBarrel> BARREL_TYPE;
    public static class CosmeticChest extends ChestBlockEntity {
        public CosmeticChest(BlockPos pos,BlockState state){super(CHEST_TYPE,pos,state);}
    }
    public static class LargeChest extends CosmeticChest {
        public LargeChest(BlockPos pos,BlockState state){super(pos,state);}
        @Override public int getContainerSize(){return 54;}
    }
    public static class CustomChest extends CosmeticChest {
        public CustomChest(BlockPos pos,BlockState state){super(pos,state);}
        @Override public boolean canPlaceItem(int slot,ItemStack stack){return stack.is(Items.DIAMOND);}
    }
    public static class CosmeticBarrel extends BarrelBlockEntity {
        public CosmeticBarrel(BlockPos pos,BlockState state){super(pos,state);}
        @Override public BlockEntityType<?> getType(){return BARREL_TYPE;}
    }
    private static class VariantChest extends ChestBlock {
        private final int kind;
        VariantChest(int kind){super(BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST),()->BlockEntityType.CHEST);this.kind=kind;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return switch(kind){case 1->new LargeChest(pos,state);case 2->new CustomChest(pos,state);default->new CosmeticChest(pos,state);};}
    }
    @SubscribeEvent public static void register(RegisterEvent event) {
        if(event.getRegistryKey().equals(Registries.BLOCK)) {
            CHEST=new VariantChest(0);LARGE=new VariantChest(1);CUSTOM=new VariantChest(2);
            BARREL=new BarrelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL)) { @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new CosmeticBarrel(pos,state);} };
            event.register(Registries.BLOCK,h->{h.register(id("chest"),CHEST);h.register(id("barrel"),BARREL);h.register(id("large"),LARGE);h.register(id("custom"),CUSTOM);});
        } else if(event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            CHEST_TYPE=BlockEntityType.Builder.of(CosmeticChest::new,CHEST,LARGE,CUSTOM).build(null);
            BARREL_TYPE=BlockEntityType.Builder.of(CosmeticBarrel::new,BARREL).build(null);
            event.register(Registries.BLOCK_ENTITY_TYPE,h->{h.register(id("chest"),CHEST_TYPE);h.register(id("barrel"),BARREL_TYPE);});
        } else if(event.getRegistryKey().equals(Registries.ITEM)) {
            event.register(Registries.ITEM,h->{h.register(id("chest"),new BlockItem(CHEST,new Item.Properties()));h.register(id("barrel"),new BlockItem(BARREL,new Item.Properties()));h.register(id("large"),new BlockItem(LARGE,new Item.Properties()));h.register(id("custom"),new BlockItem(CUSTOM,new Item.Properties()));});
        }
    }
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("cargo_fixture",name);}
}
