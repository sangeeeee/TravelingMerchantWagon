package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.TravelingMerchantWagon;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WagonContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TravelingMerchantWagon.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TravelingMerchantWagon.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TravelingMerchantWagon.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TravelingMerchantWagon.MODID);
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,TravelingMerchantWagon.MODID);
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<com.sange.tm_wagon.entity.WagonEntity>> WAGON = ENTITIES.register("wagon",() ->
        net.minecraft.world.entity.EntityType.Builder.<com.sange.tm_wagon.entity.WagonEntity>of(com.sange.tm_wagon.entity.WagonEntity::new,net.minecraft.world.entity.MobCategory.MISC)
            .sized(3,3).clientTrackingRange(12).updateInterval(3).fireImmune().build("tm_wagon:wagon"));
    public static final DeferredBlock<AssemblyFrameBlock> FRAME = BLOCKS.register("wagon_assembly_frame", () -> new AssemblyFrameBlock(properties()));
    public static final DeferredItem<BlockItem> FRAME_ITEM = ITEMS.register("wagon_assembly_frame", () -> new AssemblyFrameItem(FRAME.get(), new Item.Properties()));
    public static final DeferredBlock<AssemblyPartBlock> PROXY = BLOCKS.register("assembly_proxy", () -> new AssemblyPartBlock(properties()));
    public static final Map<WagonPart, DeferredBlock<AssemblyPartBlock>> PART_BLOCKS = new EnumMap<>(WagonPart.class);
    public static final Map<WagonPart, DeferredItem<WagonPartItem>> PART_ITEMS = new EnumMap<>(WagonPart.class);
    static {
        for (WagonPart part : WagonPart.values()) {
            var block = BLOCKS.register(part.id, () -> new AssemblyPartBlock(properties()));
            PART_BLOCKS.put(part, block);
            PART_ITEMS.put(part, ITEMS.register(part.id, () -> new WagonPartItem(block.get(), part, new Item.Properties())));
        }
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyFrameBlockEntity>> FRAME_ENTITY = BLOCK_ENTITIES.register("assembly_frame", () -> BlockEntityType.Builder.of(AssemblyFrameBlockEntity::new, FRAME.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyCellBlockEntity>> CELL_ENTITY = BLOCK_ENTITIES.register("assembly_cell", () -> {
        Block[] blocks = new Block[PART_BLOCKS.size() + 1];
        blocks[0] = PROXY.get(); int i = 1;
        for (var block : PART_BLOCKS.values()) blocks[i++] = block.get();
        return BlockEntityType.Builder.of(AssemblyCellBlockEntity::new, blocks).build(null);
    });
    public static final DeferredItem<Item> ICON = ITEMS.registerSimpleItem("wagon_icon");
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("wagons", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.tm_wagon"))
        .icon(() -> ICON.get().getDefaultInstance())
        .displayItems((parameters, output) -> {
            output.accept(FRAME_ITEM.get());
            for (WagonPart part : WagonPart.values()) output.accept(PART_ITEMS.get(part).get());
        }).build());
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD)
            .strength(2.0F).noOcclusion().pushReaction(PushReaction.BLOCK);
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); TABS.register(bus); ENTITIES.register(bus);
    }
    private WagonContent() {}
}
