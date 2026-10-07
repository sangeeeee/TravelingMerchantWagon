package com.sange.tm_wagon.compat.backpack;

import com.sange.tm_wagon.cargo.*;
import com.sange.tm_wagon.network.WagonNetwork;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlock;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.IContextAwareContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;
import net.p3pp3rf1y.sophisticatedcore.common.gui.SophisticatedMenuProvider;

/** A hidden native inventory provider preserves settings and nested-backpack navigation. */
public final class SophisticatedCargo {
    public static final String HANDLER="tm_wagon_cargo";
    private static final java.util.Map<IBackpackWrapper,java.lang.ref.WeakReference<CargoHold>> BOUND=new java.util.WeakHashMap<>();
    public static boolean matches(ItemStack stack){return stack.getItem() instanceof BackpackItem;}
    public static Block block(ItemStack stack) {
        var block=BuiltInRegistries.BLOCK.getValue(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return block instanceof BackpackBlock?block:null;
    }
    public static void register(IEventBus bus) {
        bus.addListener((FMLCommonSetupEvent event)->event.enqueueWork(()->
            PlayerInventoryProvider.get().addPlayerInventoryHandler(HANDLER,p->java.util.Set.of(),(p,id)->1,
                (p,id,slot)->slot==0?BackpackSessions.stack(p,parse(id)):ItemStack.EMPTY,false,false,false,false)));
    }
    public static UUID parse(String id){try{return UUID.fromString(id);}catch(IllegalArgumentException failure){return null;}}
    public static ItemStack item(Block block) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(BuiltInRegistries.BLOCK.getKey(block)));
    }
    public static UUID menuId(AbstractContainerMenu menu) {
        return menu instanceof IContextAwareContainer container&&container.getBackpackContext() instanceof BackpackSessions.Context context
            ?context.wagonBackpackId():null;
    }
    public static void open(CargoHold hold,CargoEntry entry,Player player) {
        if(!(player instanceof ServerPlayer server)||!hold.valid(entry,player))return;
        BackpackSessions.track(player,hold,entry);
        var context=new BackpackContext.Item(HANDLER,entry.id.toString(),0);
        // Initial native wrapper setup can create storage IDs / apply starter data.
        context.getBackpackWrapper(player);
        PacketDistributor.sendToPlayer(server,new WagonNetwork.BackpackView(entry.id,entry.item.copy()));
        player.openMenu(new SophisticatedMenuProvider((id,inventory,p)->new BackpackContainer(id,p,context),entry.item.getHoverName(),false),
            buf->context.toBuffer(buf,player));
        hold.changed(false);
    }
    /** Called by the native context bridge once per wrapper, not on every inventory lookup. */
    public static void bind(Player player,String identifier,IBackpackWrapper wrapper) {
        if(player.level().isClientSide())return;
        var id=parse(identifier);var view=BackpackSessions.session(player,id);if(view==null)return;
        var bound=BOUND.get(wrapper);if(bound!=null&&bound.get()==view.hold())return;
        var owner=new java.lang.ref.WeakReference<>(view.hold());BOUND.put(wrapper,owner);
        // Native storage still marks its SavedData dirty. These callbacks persist the cargo item's metadata.
        Runnable changed=()->{var hold=owner.get();if(hold!=null)hold.changed(false);};
        wrapper.setContentsChangeHandler(changed);
        wrapper.setInventorySlotChangeHandler(changed);
    }
    public static ItemStack placementCopy(ItemStack stack,Player player) {
        var state=net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageStackLifecycle.classifyEndpoint(stack);
        if(state==net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageEndpointStackState.ENDPOINT)
            return net.p3pp3rf1y.sophisticatedcore.linkedstorage.LinkedStorageService.createSecondaryEndpointCopy(((ServerPlayer)player).level(),stack).orElseThrow();
        return BackpackWrapper.fromStack(stack).cloneBackpack();
    }
    public static boolean canTake(ItemStack stack,Player player) {
        var wrapper=net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackLinkedStorageResolver.resolveOrCreate(player.level(),stack);
        return wrapper.getUpgradeHandler().getTypeWrappers(net.p3pp3rf1y.sophisticatedcore.upgrades.infinity.InfinityUpgradeItem.TYPE)
            .stream().noneMatch(upgrade->!upgrade.checkPermission(player));
    }
    public static void snapshot(BlockEntity be,ItemStack stack,HolderLookup.Provider lookup) {
        // Its native codec keeps the stack pending until onLoad. No live wrapper,
        // controller, world anchor or globally cached callback is needed for Carry On.
        var tag=new net.minecraft.nbt.CompoundTag();tag.put("backpackData",com.sange.tm_wagon.cargo.CargoNbt.save(stack,lookup));
        com.sange.tm_wagon.cargo.CargoNbt.load(be,tag,lookup);
    }
    public static ItemStack fromBlockEntity(BlockEntity be,ItemStack fallback,HolderLookup.Provider lookup) {
        var tag=be.saveWithoutMetadata(lookup);
        // loadAdditional defers initialization until the real chunk's onLoad; read its persisted stack.
        if(tag.contains("backpackData"))return com.sange.tm_wagon.cargo.CargoNbt.item(lookup,tag.getCompoundOrEmpty("backpackData"));
        var stack=((BackpackBlockEntity)be).getBackpackWrapper().getBackpack();
        return stack.isEmpty()?fallback:stack.copy();
    }
    private SophisticatedCargo(){}
}
