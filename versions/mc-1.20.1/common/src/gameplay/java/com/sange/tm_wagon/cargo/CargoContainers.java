package com.sange.tm_wagon.cargo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Conservative adapters for cosmetic vanilla-storage subclasses, not arbitrary item capabilities. */
public final class CargoContainers {
    private static final Map<BlockState,CargoEntry.Kind> TYPES=new ConcurrentHashMap<>();
    private static final Set<String> STORAGE_METHODS=Set.of(
        "getItems","setItems","getContainerSize","getItem","setItem","removeItem","removeItemNoUpdate",
        "clearContent","canPlaceItem","canOpen","createMenu","load","saveAdditional","getMaxStackSize",
        "getSlotsForFace","canPlaceItemThroughFace","canTakeItemThroughFace","stillValid","isEmpty");
    // Forge 1.20.1 uses SRG method names outside the development environment.
    private static final Set<String> SRG_STORAGE_METHODS=Set.of(
        "m_7086_","m_59065_","m_6520_","m_6643_","m_8020_","m_6836_",
        "m_7407_","m_8016_","m_6211_","m_7013_","m_7525_","m_6555_",
        "m_7208_","m_7402_","m_142466_","m_183515_","m_6893_","m_7071_",
        "m_7155_","m_7157_","m_6542_","m_7983_");
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
                for(var method:type.getDeclaredMethods())if(STORAGE_METHODS.contains(method.getName())||SRG_STORAGE_METHODS.contains(method.getName()))return CargoEntry.Kind.ORDINARY;
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
        var tag=com.sange.tm_wagon.cargo.ItemNbt.blockEntity(stack);
        // Do not bypass locks or silently discard an ungenerated loot table.
        return tag.contains("LootTable")||tag.contains("Lock")&&!tag.getString("Lock").isEmpty();
    }
    private CargoContainers(){}
}
