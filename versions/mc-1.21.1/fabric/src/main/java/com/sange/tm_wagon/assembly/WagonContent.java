package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.platform.FabricRegistry;

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
import net.fabricmc.loader.api.FabricLoader;

public final class WagonContent {
    public static final FabricRegistry<net.minecraft.sounds.SoundEvent> SOUNDS=FabricRegistry.create(Registries.SOUND_EVENT,TravelingMerchantWagon.MODID);
    public static final java.util.function.Supplier<net.minecraft.sounds.SoundEvent> ROLL=SOUNDS.register("wagon_roll",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"wagon_roll")));
    public static final FabricRegistry<net.minecraft.world.level.block.Block> BLOCKS = FabricRegistry.createBlocks(TravelingMerchantWagon.MODID);
    public static final FabricRegistry<net.minecraft.world.item.Item> ITEMS = FabricRegistry.createItems(TravelingMerchantWagon.MODID);
    public static final Optional<java.util.function.Supplier<com.sange.tm_wagon.handbook.CoachmansManualItem>> MANUAL =
        FabricLoader.getInstance().isModLoaded("patchouli")
            ? Optional.of(ITEMS.register("coachmans_manual",com.sange.tm_wagon.handbook.CoachmansManualItem::new))
            : Optional.empty();
    public static final java.util.function.Supplier<DismantlingHammerItem> DISMANTLING_HAMMER = ITEMS.register("dismantling_hammer",DismantlingHammerItem::new);
    public static final java.util.function.Supplier<com.sange.tm_wagon.cargo.WagonCabinetItem> CABINET = ITEMS.register("wagon_cabinet",()->new com.sange.tm_wagon.cargo.WagonCabinetItem(new Item.Properties().stacksTo(16)));
    public static final java.util.function.Supplier<com.sange.tm_wagon.cargo.StrawMatItem> STRAW_MAT = ITEMS.register("wagon_straw_mat",()->new com.sange.tm_wagon.cargo.StrawMatItem(new Item.Properties().stacksTo(16)));
    public static final java.util.function.Supplier<com.sange.tm_wagon.cargo.WagonStoolItem> STOOL = ITEMS.register("wagon_stool",()->new com.sange.tm_wagon.cargo.WagonStoolItem(new Item.Properties().stacksTo(16)));
    public static final java.util.function.Supplier<com.sange.tm_wagon.cargo.WagonCoverItem> CARGO_COVER = ITEMS.register("wagon_cargo_cover",()->new com.sange.tm_wagon.cargo.WagonCoverItem(new Item.Properties().stacksTo(16)));
    public static final java.util.function.Supplier<com.sange.tm_wagon.cargo.WagonCanopyItem> CANOPY = ITEMS.register("wagon_canopy",()->new com.sange.tm_wagon.cargo.WagonCanopyItem(new Item.Properties().stacksTo(16)));
    public static final FabricRegistry<BlockEntityType<?>> BLOCK_ENTITIES = FabricRegistry.create(Registries.BLOCK_ENTITY_TYPE, TravelingMerchantWagon.MODID);
    public static final FabricRegistry<CreativeModeTab> TABS = FabricRegistry.create(Registries.CREATIVE_MODE_TAB, TravelingMerchantWagon.MODID);
    public static final FabricRegistry<net.minecraft.world.entity.EntityType<?>> ENTITIES = FabricRegistry.create(Registries.ENTITY_TYPE,TravelingMerchantWagon.MODID);
    public static final java.util.function.Supplier<net.minecraft.world.entity.EntityType<com.sange.tm_wagon.entity.WagonEntity>> WAGON = ENTITIES.register("wagon",() ->
        net.minecraft.world.entity.EntityType.Builder.<com.sange.tm_wagon.entity.WagonEntity>of(com.sange.tm_wagon.entity.WagonEntity::new,net.minecraft.world.entity.MobCategory.MISC)
            .sized(3,3).clientTrackingRange(12).updateInterval(2).build("tm_wagon:wagon"));
    public static final java.util.function.Supplier<net.minecraft.world.entity.EntityType<com.sange.tm_wagon.entity.CargoSeatEntity>> CARGO_SEAT = ENTITIES.register("cargo_seat",()->
        net.minecraft.world.entity.EntityType.Builder.<com.sange.tm_wagon.entity.CargoSeatEntity>of(com.sange.tm_wagon.entity.CargoSeatEntity::new,net.minecraft.world.entity.MobCategory.MISC)
            .sized(.01F,.01F).noSave().noSummon().clientTrackingRange(12).updateInterval(20).build("tm_wagon:cargo_seat"));
    public static final java.util.function.Supplier<AssemblyFrameBlock> FRAME = BLOCKS.register("wagon_assembly_frame", () -> new AssemblyFrameBlock(properties()));
    public static final java.util.function.Supplier<BlockItem> FRAME_ITEM = ITEMS.register("wagon_assembly_frame", () -> new AssemblyFrameItem(FRAME.get(), new Item.Properties()));
    public static final java.util.function.Supplier<AssemblyPartBlock> PROXY = BLOCKS.register("assembly_proxy", () -> new AssemblyPartBlock(properties()));
    public static final Map<WagonPart, java.util.function.Supplier<AssemblyPartBlock>> PART_BLOCKS = new EnumMap<>(WagonPart.class);
    public static final Map<WagonPart, java.util.function.Supplier<WagonPartItem>> PART_ITEMS = new EnumMap<>(WagonPart.class);
    static {
        for (WagonPart part : WagonPart.values()) {
            var block = BLOCKS.register(part.id, () -> new AssemblyPartBlock(properties()));
            PART_BLOCKS.put(part, block);
            PART_ITEMS.put(part, ITEMS.register(part.id, () -> new WagonPartItem(block.get(), part, new Item.Properties())));
        }
    }
    public static final java.util.function.Supplier<BlockEntityType<AssemblyFrameBlockEntity>> FRAME_ENTITY = BLOCK_ENTITIES.register("assembly_frame", () -> BlockEntityType.Builder.of(AssemblyFrameBlockEntity::new, FRAME.get()).build(null));
    public static final java.util.function.Supplier<BlockEntityType<AssemblyCellBlockEntity>> CELL_ENTITY = BLOCK_ENTITIES.register("assembly_cell", () -> {
        Block[] blocks = new Block[PART_BLOCKS.size() + 1];
        blocks[0] = PROXY.get(); int i = 1;
        for (var block : PART_BLOCKS.values()) blocks[i++] = block.get();
        return BlockEntityType.Builder.of(AssemblyCellBlockEntity::new, blocks).build(null);
    });
    public static final java.util.function.Supplier<Item> ICON = ITEMS.registerSimpleItem("wagon_icon");
    public static final java.util.function.Supplier<Item> MAID_TASK_ICON = ITEMS.registerSimpleItem("maid_wagon_companion_icon");
    public static final java.util.function.Supplier<CreativeModeTab> TAB = TABS.register("wagons", () -> net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
        .title(Component.translatable("itemGroup.tm_wagon"))
        .icon(() -> ICON.get().getDefaultInstance())
        .displayItems((parameters, output) -> {
            MANUAL.ifPresent(manual->output.accept(manual.get()));
            output.accept(FRAME_ITEM.get());
            output.accept(DISMANTLING_HAMMER.get());
            for (WagonPart part : new WagonPart[]{
                WagonPart.CARGO_BODY,WagonPart.LONG_CARGO_BODY,WagonPart.WIDE_CARGO_BODY,
                WagonPart.SINGLE_WOODEN_SEAT,WagonPart.DOUBLE_WOODEN_SEAT,WagonPart.TRIPLE_WOODEN_SEAT,
                WagonPart.SINGLE_SEAT,WagonPart.DOUBLE_SEAT,WagonPart.TRIPLE_SEAT,
                WagonPart.SINGLE_HORSE_SHAFTS,WagonPart.DOUBLE_HORSE_SHAFTS,
                WagonPart.SMALL_WHEEL,WagonPart.LARGE_WHEEL
            }) com.sange.tm_wagon.material.WagonMaterial.creative(output,PART_ITEMS.get(part).get());
            output.accept(STRAW_MAT.get());
            com.sange.tm_wagon.material.WagonMaterial.creative(output,STOOL.get());
            com.sange.tm_wagon.material.WagonMaterial.creative(output,CARGO_COVER.get());
            com.sange.tm_wagon.material.WagonMaterial.creative(output,CANOPY.get());
            com.sange.tm_wagon.material.WagonMaterial.creative(output,CABINET.get());
        }).build());
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD)
            .strength(2.0F,3.0F).noOcclusion().pushReaction(PushReaction.BLOCK);
    }
    public static void register() {}
    private WagonContent() {}
}
