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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WagonContent {
    public static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,TravelingMerchantWagon.MODID);
    public static final DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> ROLL=SOUNDS.register("wagon_roll",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.Identifier.fromNamespaceAndPath(TravelingMerchantWagon.MODID,"wagon_roll")));
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TravelingMerchantWagon.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TravelingMerchantWagon.MODID);
    public static final DeferredItem<DismantlingHammerItem> DISMANTLING_HAMMER = ITEMS.registerItem("dismantling_hammer",DismantlingHammerItem::new);
    public static final DeferredItem<com.sange.tm_wagon.cargo.StrawMatItem> STRAW_MAT = ITEMS.registerItem("wagon_straw_mat",com.sange.tm_wagon.cargo.StrawMatItem::new,()->new Item.Properties().stacksTo(16));
    public static final DeferredItem<com.sange.tm_wagon.cargo.WagonCabinetItem> CABINET = ITEMS.registerItem("wagon_cabinet",com.sange.tm_wagon.cargo.WagonCabinetItem::new,()->new Item.Properties().stacksTo(16));
    public static final DeferredItem<com.sange.tm_wagon.cargo.WagonStoolItem> STOOL = ITEMS.registerItem("wagon_stool",com.sange.tm_wagon.cargo.WagonStoolItem::new,()->new Item.Properties().stacksTo(16));
    public static final DeferredItem<com.sange.tm_wagon.cargo.WagonCoverItem> CARGO_COVER = ITEMS.registerItem("wagon_cargo_cover",com.sange.tm_wagon.cargo.WagonCoverItem::new,()->new Item.Properties().stacksTo(16));
    public static final DeferredItem<com.sange.tm_wagon.cargo.WagonCanopyItem> CANOPY = ITEMS.registerItem("wagon_canopy",com.sange.tm_wagon.cargo.WagonCanopyItem::new,()->new Item.Properties().stacksTo(16));
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TravelingMerchantWagon.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TravelingMerchantWagon.MODID);
    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,TravelingMerchantWagon.MODID);
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<com.sange.tm_wagon.entity.WagonEntity>> WAGON = ENTITIES.register("wagon",() ->
        net.minecraft.world.entity.EntityType.Builder.<com.sange.tm_wagon.entity.WagonEntity>of(com.sange.tm_wagon.entity.WagonEntity::new,net.minecraft.world.entity.MobCategory.MISC)
            .sized(3,3).clientTrackingRange(12).updateInterval(2).build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE,net.minecraft.resources.Identifier.fromNamespaceAndPath("tm_wagon","wagon"))));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>,net.minecraft.world.entity.EntityType<com.sange.tm_wagon.entity.CargoSeatEntity>> CARGO_SEAT = ENTITIES.register("cargo_seat",()->
        net.minecraft.world.entity.EntityType.Builder.<com.sange.tm_wagon.entity.CargoSeatEntity>of(com.sange.tm_wagon.entity.CargoSeatEntity::new,net.minecraft.world.entity.MobCategory.MISC)
            .sized(.01F,.01F).noSave().noSummon().clientTrackingRange(12).updateInterval(20).build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE,net.minecraft.resources.Identifier.fromNamespaceAndPath("tm_wagon","cargo_seat"))));
    public static final DeferredBlock<AssemblyFrameBlock> FRAME = BLOCKS.registerBlock("wagon_assembly_frame", AssemblyFrameBlock::new, WagonContent::properties);
    public static final DeferredItem<BlockItem> FRAME_ITEM = ITEMS.registerItem("wagon_assembly_frame", props -> new AssemblyFrameItem(FRAME.get(), props.useBlockDescriptionPrefix()));
    public static final DeferredBlock<AssemblyPartBlock> PROXY = BLOCKS.registerBlock("assembly_proxy", AssemblyPartBlock::new, WagonContent::properties);
    public static final Map<WagonPart, DeferredBlock<AssemblyPartBlock>> PART_BLOCKS = new EnumMap<>(WagonPart.class);
    public static final Map<WagonPart, DeferredItem<WagonPartItem>> PART_ITEMS = new EnumMap<>(WagonPart.class);
    static {
        for (WagonPart part : WagonPart.values()) {
            var block = BLOCKS.registerBlock(part.id, AssemblyPartBlock::new, WagonContent::properties);
            PART_BLOCKS.put(part, block);
            PART_ITEMS.put(part, ITEMS.registerItem(part.id, props -> new WagonPartItem(block.get(), part, props.useBlockDescriptionPrefix())));
        }
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyFrameBlockEntity>> FRAME_ENTITY = BLOCK_ENTITIES.register("assembly_frame", () -> new BlockEntityType<>(AssemblyFrameBlockEntity::new, FRAME.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AssemblyCellBlockEntity>> CELL_ENTITY = BLOCK_ENTITIES.register("assembly_cell", () -> {
        Block[] blocks = new Block[PART_BLOCKS.size() + 1];
        blocks[0] = PROXY.get(); int i = 1;
        for (var block : PART_BLOCKS.values()) blocks[i++] = block.get();
        return new BlockEntityType<>(AssemblyCellBlockEntity::new, blocks);
    });
    public static final DeferredItem<Item> ICON = ITEMS.registerSimpleItem("wagon_icon");
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("wagons", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.tm_wagon"))
        .icon(() -> ICON.get().getDefaultInstance())
        .displayItems((parameters, output) -> {
            output.accept(FRAME_ITEM.get());
            output.accept(DISMANTLING_HAMMER.get());
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
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); TABS.register(bus); ENTITIES.register(bus);SOUNDS.register(bus);
    }
    private WagonContent() {}
}
