package com.sange.tm_wagon.cargo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Conservative adapters for cosmetic vanilla-storage subclasses, not arbitrary item capabilities. */
public final class CargoContainers {
    private static final Map<BlockState,CargoEntry.Kind> TYPES=new ConcurrentHashMap<>();
    private static final Set<String> STORAGE_METHODS=Set.of(
        "getItems","setItems","getContainerSize","getItem","setItem","removeItem","removeItemNoUpdate",
        "clearContent","canPlaceItem","canOpen","createMenu","loadAdditional","saveAdditional",
        "applyImplicitComponents","collectImplicitComponents","removeComponentsFromTag","getMaxStackSize",
        "getSlotsForFace","canPlaceItemThroughFace","canTakeItemThroughFace","stillValid","isEmpty");
    public static CargoEntry.Kind kind(BlockState state) {
        if(!(state.getBlock() instanceof ChestBlock||state.getBlock() instanceof BarrelBlock))return CargoEntry.Kind.ORDINARY;
        return TYPES.computeIfAbsent(state,CargoContainers::inspect);
    }
    private static CargoEntry.Kind inspect(BlockState state) {
        try {
            var be=((EntityBlock)state.getBlock()).newBlockEntity(BlockPos.ZERO,state);
            Class<?> base;CargoEntry.Kind kind;
            if(state.getBlock() instanceof ChestBlock&&be instanceof ChestBlockEntity){base=ChestBlockEntity.class;kind=CargoEntry.Kind.CHEST;}
            else if(state.getBlock() instanceof BarrelBlock&&be instanceof BarrelBlockEntity){base=BarrelBlockEntity.class;kind=CargoEntry.Kind.BARREL;}
            else return CargoEntry.Kind.ORDINARY;
            if(((net.minecraft.world.Container)be).getContainerSize()!=27)return CargoEntry.Kind.ORDINARY;
            // Storage, permissions, codecs and custom menus require an explicit adapter.
            // Cosmetic changes such as the BE type, name or renderer may be inherited freely.
            for(Class<?> type=be.getClass();type!=base;type=type.getSuperclass()) {
                if(type==TrappedChestBlockEntity.class)continue;
                for(var method:type.getDeclaredMethods())if(STORAGE_METHODS.contains(method.getName()))return CargoEntry.Kind.ORDINARY;
            }
            return kind;
        } catch(RuntimeException|LinkageError unsupported){return CargoEntry.Kind.ORDINARY;}
    }
    public static boolean protectedContents(ItemStack stack) {
        var tag=stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA,CustomData.EMPTY).copyTag();
        // Do not bypass locks or silently discard an ungenerated loot table.
        return tag.contains("LootTable")||tag.contains("Lock")&&!tag.getString("Lock").isEmpty();
    }
    private CargoContainers(){}
}
