package com.sange.tm_wagon.cargo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import tschipp.carryon.common.carry.CarryOnDataManager;
import tschipp.carryon.common.carry.PickupHandler;
import tschipp.carryon.common.config.ListHandler;

/** Optional bridge: run Carry On's own policy and pickup against a bounded cargo view.
 * The real world is never temporarily changed to stage the carried block. */
public final class CarryOnCargo {
    public static boolean pickup(CargoHold hold,CargoEntry entry,Player player) {
        if(!(player instanceof ServerPlayer server)||!(entry.item.getItem() instanceof BlockItem)
            ||!hold.valid(entry,player))return false;
        var data=CarryOnDataManager.getCarryData(player);
        if(data.isCarrying()||(data.isKeyPressed()&&data.getTick()==player.tickCount))return true;
        if(!data.isKeyPressed()||!player.getMainHandItem().isEmpty()||!player.getOffhandItem().isEmpty()
            ||!ListHandler.isPermitted(entry.state.getBlock()))return false;
        // 2.2.4 writes transient key/slot fields into NBT only in getNbt(); clone()
        // alone would lose the held key when rolling back an unsuccessful transfer.
        var backup=new tschipp.carryon.common.carry.CarryOnData(data.getNbt().copy());
        View view=null;
        try {
            CargoMenus.close(hold,entry);
            view=new View(hold,entry,server);
            var checkView=view;
            boolean picked=PickupHandler.tryPickUpBlock(server,view.pos,view,(state,pos)->{
                if(!hold.valid(entry,player)||!player.level().mayInteract(player,pos)){checkView.denied=true;return false;}
                var event=new BlockEvent.BreakEvent(player.level(),pos,state,player);
                NeoForge.EVENT_BUS.post(event);
                if(event.isCanceled()||!checkView.unchanged()){checkView.denied=true;return false;}
                return true;
            });
            if(!picked)return view.denied;
            if(!view.committed)throw new IllegalStateException("Carry On did not release the cargo source");
            return true;
        } catch(RuntimeException ex) {
            // Before commit the wagon still owns everything. After commit Carry On owns
            // the serialized block, even if a subsequent sound/effect hook throws.
            if(view==null||!view.committed)CarryOnDataManager.setCarryData(player,backup);
            if(!(ex instanceof Rejected))com.mojang.logging.LogUtils.getLogger().warn("Could not finish Carry On cargo pickup for {}",entry.state,ex);
            return true; // Never fall through to a second removal after a partial pickup.
        }
    }
    private static final class Rejected extends RuntimeException {}
    private static final class View extends CargoLevel {
        private final CargoHold hold;
        private final CargoEntry entry;
        private final ServerPlayer player;
        private final BlockPos pos;
        private final BlockState state;
        private final BlockEntity blockEntity;
        private final CompoundTag before;
        private boolean committed,denied;
        View(CargoHold hold,CargoEntry entry,ServerPlayer player) {
            super(hold,entry);this.hold=hold;this.entry=entry;this.player=player;
            pos=BlockPos.containing(hold.position(entry));
            state=entry.state.hasProperty(BarrelBlock.OPEN)?entry.state.setValue(BarrelBlock.OPEN,false):entry.state;
            blockEntity=snapshot(entry,pos,state,this);
            before=entry.save(registryAccess(),false);
        }
        boolean unchanged(){return hold.valid(entry,player)&&before.equals(entry.save(registryAccess(),false));}
        @Override public BlockState getBlockState(BlockPos at){return pos.equals(at)&&!committed?state:Blocks.AIR.defaultBlockState();}
        @Override public BlockEntity getBlockEntity(BlockPos at){return pos.equals(at)&&!committed?blockEntity:null;}
        @Override public void removeBlockEntity(BlockPos at){} // Detached snapshot, never remove a world BE.
        @Override public boolean removeBlock(BlockPos at,boolean moving) {
            if(!pos.equals(at)||committed||!unchanged()||!hold.releaseForCarry(entry,player))throw new Rejected();
            committed=true;hold.changed(true);return true;
        }
        @Override public boolean setBlock(BlockPos at,BlockState value,int flags,int recursion){return false;}
        @Override public void sendBlockUpdated(BlockPos at,BlockState old,BlockState value,int flags){}
        @Override public void blockEntityChanged(BlockPos at){}
    }
    private static BlockEntity snapshot(CargoEntry entry,BlockPos pos,BlockState state,View level) {
        if(!(state.getBlock() instanceof EntityBlock factory))return null;
        var be=factory.newBlockEntity(pos,state);
        if(be==null)throw new IllegalStateException("Block entity factory returned null");
        be.setLevel(level);
        var lookup=level.registryAccess();
        be.loadWithComponents(entry.item.getOrDefault(DataComponents.BLOCK_ENTITY_DATA,CustomData.EMPTY).copyTag(),lookup);
        be.applyComponentsFromItemStack(entry.item);
        var tag=be.saveWithFullMetadata(lookup);
        if(entry.inventory.getContainerSize()>0) {
            var contents=NonNullList.withSize(entry.inventory.getContainerSize(),ItemStack.EMPTY);
            for(int i=0;i<contents.size();i++)contents.set(i,entry.inventory.getItem(i).copy());
            // Container components must not override the authoritative cargo inventory.
            var components=tag.getCompound("components");components.remove("minecraft:container");
            ContainerHelper.saveAllItems(tag,contents,lookup);
            if(entry.kind==CargoEntry.Kind.LECTERN) {
                tag.remove("Items");tag.remove("Book");
                if(!contents.getFirst().isEmpty())tag.put("Book",contents.getFirst().save(lookup));
                tag.putInt("Page",entry.page);
            } else if(entry.kind==CargoEntry.Kind.POT) {
                tag.remove("Items");tag.remove("item");
                if(!contents.getFirst().isEmpty())tag.put("item",contents.getFirst().save(lookup));
            }
            if(entry.cooking()) {
                tag.putShort("BurnTime",(short)entry.burn);tag.putShort("CookTime",(short)entry.cook);
                tag.putShort("CookTimeTotal",(short)entry.totalCook);
                tag.put("RecipesUsed",entry.save(lookup,false).getCompound("Recipes").copy());
            } else if(entry.kind==CargoEntry.Kind.BREWING) {
                tag.putShort("BrewTime",(short)entry.brewTime);tag.putByte("Fuel",(byte)entry.brewFuel);
            }
        }
        be.loadWithComponents(tag,lookup);return be;
    }
    private CarryOnCargo() {}
}
