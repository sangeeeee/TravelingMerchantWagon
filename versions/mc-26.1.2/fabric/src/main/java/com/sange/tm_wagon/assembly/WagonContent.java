package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.TravelingMerchantWagon;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
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

public final class WagonContent {
    public static final com.sange.tm_wagon.platform.FabricRegistry<net.minecraft.sounds.SoundEvent> SOUNDS=com.sange.tm_wagon.platform.FabricRegistry.create(Registries.SOUND_EVENT,TravelingMerchantWagon.MODID);
    public static final java.util.function.Supplier<net.minecraft.sounds.SoundEvent> ROLL=SOUNDS.register("wagon_roll",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"wagon_roll")));
    public static final com.sange.tm_wagon.platform.FabricRegistry<net.minecraft.world.level.block.Block> BLOCKS = com.sange.tm_wagon.platform.FabricRegistry.createBlocks(TravelingMerchantWagon.MODID);
    public static final com.sange.tm_wagon.platform.FabricRegistry<net.minecraft.world.item.Item> ITEMS = com.sange.tm_wagon.platform.FabricRegistry.createItems(TravelingMerchantWagon.MODID);
    public static final java.util.function.Supplier<DismantlingHammerItem> DISMANTLING_HAMMER = ITEMS.registerItem("dismantling_hammer",DismantlingHammerItem::new);
    public static final java.util.function.Supplier<com.sange.tm_wagon.cargo.StrawMatItem> STRAW_MAT=ITEMS.registerItem("wagon_straw_mat",com.sange.tm_wagon.cargo.StrawMatItem::new,()->new Item.Properties().stacksTo(16));
    public static final java.util.function.Supplier<com.sange.tm_wagon.cargo.WagonCabinetItem> CABINET = ITEMS.registerItem("wagon_cabinet",com.sange.tm_wagon.cargo.WagonCabinetItem::new,()->new Item.Properties().stacksTo(16));
    public static final java.util.function.Supplier<com.sange.tm_wagon.cargo.WagonStoolItem> STOOL = ITEMS.registerItem("wagon_stool",com.sange.tm_wagon.cargo.WagonStoolItem::new,()->new Item.Properties().stacksTo(16));
    public static final java.util.function.Supplier<com.sange.tm_wagon.cargo.WagonCoverItem> CARGO_COVER = ITEMS.registerItem("wagon_cargo_cover",com.sange.tm_wagon.cargo.WagonCoverItem::new,()->new Item.Properties().stacksTo(16));
    public static final java.util.function.Supplier<com.sange.tm_wagon.cargo.WagonCanopyItem> CANOPY = ITEMS.registerItem("wagon_canopy",com.sange.tm_wagon.cargo.WagonCanopyItem::new,()->new Item.Properties().stacksTo(16));
    public static final com.sange.tm_wagon.platform.FabricRegistry<BlockEntityType<?>> BLOCK_ENTITIES = com.sange.tm_wagon.platform.FabricRegistry.create(Registries.BLOCK_ENTITY_TYPE, TravelingMerchantWagon.MODID);
    public static final com.sange.tm_wagon.platform.FabricRegistry<CreativeModeTab> TABS = com.sange.tm_wagon.platform.FabricRegistry.create(Registries.CREATIVE_MODE_TAB, TravelingMerchantWagon.MODID);
    public static final com.sange.tm_wagon.platform.FabricRegistry<net.minecraft.world.entity.EntityType<?>> ENTITIES = com.sange.tm_wagon.platform.FabricRegistry.create(Registries.ENTITY_TYPE,TravelingMerchantWagon.MODID);
    public static final java.util.function.Supplier<net.minecraft.world.entity.EntityType<com.sange.tm_wagon.entity.WagonEntity>> WAGON = ENTITIES.register("wagon",() ->
        net.minecraft.world.entity.EntityType.Builder.<com.sange.tm_wagon.entity.WagonEntity>of(com.sange.tm_wagon.entity.WagonEntity::new,net.minecraft.world.entity.MobCategory.MISC)
            .sized(3,3).clientTrackingRange(12).updateInterval(2).build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE,net.minecraft.resources.Identifier.fromNamespaceAndPath("tm_wagon","wagon"))));
    public static final java.util.function.Supplier<net.minecraft.world.entity.EntityType<com.sange.tm_wagon.entity.CargoSeatEntity>> CARGO_SEAT = ENTITIES.register("cargo_seat",()->
        net.minecraft.world.entity.EntityType.Builder.<com.sange.tm_wagon.entity.CargoSeatEntity>of(com.sange.tm_wagon.entity.CargoSeatEntity::new,net.minecraft.world.entity.MobCategory.MISC)
            .sized(.01F,.01F).noSave().noSummon().clientTrackingRange(12).updateInterval(20).build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE,net.minecraft.resources.Identifier.fromNamespaceAndPath("tm_wagon","cargo_seat"))));
    public static final java.util.function.Supplier<AssemblyFrameBlock> FRAME = BLOCKS.registerBlock("wagon_assembly_frame", AssemblyFrameBlock::new, WagonContent::properties);
    public static final java.util.function.Supplier<BlockItem> FRAME_ITEM = ITEMS.registerItem("wagon_assembly_frame", props -> new AssemblyFrameItem(FRAME.get(), props.useBlockDescriptionPrefix()));
    public static final java.util.function.Supplier<AssemblyPartBlock> PROXY = BLOCKS.registerBlock("assembly_proxy", AssemblyPartBlock::new, WagonContent::properties);
    public static final Map<WagonPart, java.util.function.Supplier<AssemblyPartBlock>> PART_BLOCKS = new EnumMap<>(WagonPart.class);
    public static final Map<WagonPart, java.util.function.Supplier<WagonPartItem>> PART_ITEMS = new EnumMap<>(WagonPart.class);
    static {
        for (WagonPart part : WagonPart.values()) {
            var block = BLOCKS.registerBlock(part.id, AssemblyPartBlock::new, WagonContent::properties);
            PART_BLOCKS.put(part, block);
            PART_ITEMS.put(part, ITEMS.registerItem(part.id, props -> new WagonPartItem(block.get(), part, props.useBlockDescriptionPrefix())));
        }
    }
    public static final java.util.function.Supplier<BlockEntityType<AssemblyFrameBlockEntity>> FRAME_ENTITY = BLOCK_ENTITIES.register("assembly_frame", () -> net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(AssemblyFrameBlockEntity::new,FRAME.get()).build());
    public static final java.util.function.Supplier<BlockEntityType<AssemblyCellBlockEntity>> CELL_ENTITY = BLOCK_ENTITIES.register("assembly_cell", () -> {
        Block[] blocks = new Block[PART_BLOCKS.size() + 1];
        blocks[0] = PROXY.get(); int i = 1;
        for (var block : PART_BLOCKS.values()) blocks[i++] = block.get();
        return net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(AssemblyCellBlockEntity::new,blocks).build();
    });
    public static final java.util.function.Supplier<Item> MAID_TASK_ICON = ITEMS.registerSimpleItem("maid_wagon_companion_icon");
    public static final Optional<java.util.function.Supplier<com.sange.tm_wagon.handbook.CoachmansManualItem>> MANUAL = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("patchouli") ? Optional.of(ITEMS.registerItem("coachmans_manual",com.sange.tm_wagon.handbook.CoachmansManualItem::new)) : Optional.empty();
    public static final java.util.function.Supplier<Item> ICON = ITEMS.registerSimpleItem("wagon_icon");
    public static final java.util.function.Supplier<CreativeModeTab> TAB = TABS.register("wagons", () -> net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
        .title(Component.translatable("itemGroup.tm_wagon"))
        .icon(() -> ICON.get().getDefaultInstance())
        .displayItems((parameters, output) -> {
            output.accept(FRAME_ITEM.get());
            output.accept(DISMANTLING_HAMMER.get());
            MANUAL.ifPresent(book -> output.accept(book.get()));
            for (WagonPart part : new WagonPart[]{
                WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY,
                WagonPart.SINGLE_WOODEN_SEAT,WagonPart.DOUBLE_WOODEN_SEAT,WagonPart.TRIPLE_WOODEN_SEAT,
                WagonPart.SINGLE_SEAT,WagonPart.DOUBLE_SEAT,WagonPart.TRIPLE_SEAT,
                WagonPart.SINGLE_HORSE_SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS,
                WagonPart.SMALL_WHEEL,WagonPart.LARGE_WHEEL
            }) com.sange.tm_wagon.material.WagonMaterial.creative(output,PART_ITEMS.get(part).get());
            output.accept(WagonContent.STRAW_MAT.get());
            com.sange.tm_wagon.material.WagonMaterial.creative(output,STOOL.get());
            com.sange.tm_wagon.material.WagonMaterial.creative(output,CARGO_COVER.get());
            com.sange.tm_wagon.material.WagonMaterial.creative(output,CANOPY.get());
            com.sange.tm_wagon.material.WagonMaterial.creative(output,CABINET.get());
        }).build());
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD)
            .strength(2.0F,3.0F).noOcclusion().pushReaction(PushReaction.BLOCK);
    }
    public static void register() {
        BLOCKS.register(); ITEMS.register(); BLOCK_ENTITIES.register(); TABS.register(); ENTITIES.register();SOUNDS.register();
    }
    private WagonContent() {}
}
