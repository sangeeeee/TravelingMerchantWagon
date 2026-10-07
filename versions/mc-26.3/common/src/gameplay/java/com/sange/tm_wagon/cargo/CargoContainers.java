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
            else if(state.getBlock() instanceof BarrelBlock&&be instanceof RandomizableContainerBlockEntity
                &&(base=bclibBarrelBase(be.getClass()))!=null){kind=CargoEntry.Kind.BARREL;}
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
    private static Class<?> bclibBarrelBase(Class<?> type) {
        // BCLib 21.0.x duplicates vanilla barrel storage instead of extending BarrelBlockEntity.
        // Its 27-slot Items codec and three-row menu use the existing cargo barrel adapter.
        // Match only this audited implementation; subclasses still pass the override checks above.
        // No class loading or mandatory dependency when BCLib is absent.
        for(Class<?> current=type;current!=null;current=current.getSuperclass()) {
            if(current.getName().equals("org.betterx.bclib.blockentities.BaseBarrelBlockEntity")
                &&current.getSuperclass()==RandomizableContainerBlockEntity.class)return current;
        }
        return null;
    }
    public static boolean protectedContents(ItemStack stack) {
        var tag=com.sange.tm_wagon.cargo.CargoNbt.blockTag(stack);
        // Do not bypass locks or silently discard an ungenerated loot table.
        return stack.has(DataComponents.CONTAINER_LOOT)
            ||!stack.getOrDefault(DataComponents.LOCK,net.minecraft.world.LockCode.NO_LOCK).equals(net.minecraft.world.LockCode.NO_LOCK)
            ||tag.contains("LootTable")||tag.contains("lock");
    }
    private CargoContainers(){}
}
