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
        if(entry.kind==CargoEntry.Kind.STRAW_MAT||entry.kind==CargoEntry.Kind.STOOL)return false;
        if(!(player instanceof ServerPlayer server)||!(entry.item.getItem() instanceof BlockItem||com.sange.tm_wagon.compat.BackpackCompat.matches(entry.item))
            ||!hold.valid(entry,player))return false;
        var data=CarryOnDataManager.getCarryData(player);
        if(data.isCarrying()||(data.isKeyPressed()&&data.getTick()==player.tickCount))return true;
        if(!data.isKeyPressed()||!player.getMainHandItem().isEmpty()||!player.getOffhandItem().isEmpty()
            ||!ListHandler.isPermitted(entry.state.getBlock()))return false;
        if(!com.sange.tm_wagon.compat.BackpackCompat.canTake(entry,player))return true;
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
    /** Import a carried block through exactly the same cargo policy as item placement. */
    public static boolean place(CargoHold hold,int slot,Player player) {
        if(!(player instanceof ServerPlayer server))return false;
        var data=CarryOnDataManager.getCarryData(player);
        if(!data.isCarrying())return false;
        if(data.isCarrying(tschipp.carryon.common.carry.CarryOnData.CarryType.ENTITY))return seatEntity(hold,slot,server,data);
        if(!data.isCarrying(tschipp.carryon.common.carry.CarryOnData.CarryType.BLOCK)) {
            CargoHold.message(player,"message.tm_wagon.cargo_unsupported");return true;
        }
        if(data.getTick()==player.tickCount)return true;
        boolean[] committed={false};
        var script=data.getActiveScript();
        try {
            var state=data.getBlock();var stack=com.sange.tm_wagon.compat.BackpackCompat.blockItem(state.getBlock());
            String restriction=CargoHold.placementRestriction(stack);
            if(restriction!=null){CargoHold.message(player,restriction);return true;}
            var lookup=player.level().registryAccess();
            var be=data.getBlockEntity(BlockPos.containing(hold.owner().cargoPose().point(hold.centreAt(slot))),lookup);
            if(state.hasBlockEntity()&&be==null){CargoHold.message(player,"message.tm_wagon.cargo_unsupported");return true;}
            if(be!=null){
                if(com.sange.tm_wagon.compat.BackpackCompat.matches(stack))stack=com.sange.tm_wagon.compat.BackpackCompat.fromBlockEntity(be,stack,lookup);
                else {be.setLevel(player.level());be.saveToItem(stack,lookup);}
            }
            // Orient in wagon-local coordinates, retaining fill levels and other carried state.
            var placed=CargoPlacement.state(hold,slot,stack,player);
            for(var property:placed.getProperties())if(state.hasProperty(property)
                &&(property instanceof net.minecraft.world.level.block.state.properties.DirectionProperty
                ||property.getValueClass()==net.minecraft.core.Direction.Axis.class||ListHandler.isPropertyException(property)))
                state=copyProperty(state,placed,property);
            if(state.hasProperty(BarrelBlock.OPEN))state=state.setValue(BarrelBlock.OPEN,false);
            var finalState=state;
            String error=hold.placeCarried(slot,stack,player,finalState,()->{
                data.clear();data.setTick(player.tickCount);committed[0]=true;
            });
            if(error!=null){CargoHold.message(player,error);return true;}
        } catch(RuntimeException ex) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not finish placing carried cargo",ex);
            if(!committed[0]){CargoHold.message(player,"message.tm_wagon.cargo_unsupported");return true;}
        }
        // Even if a cosmetic update fails after commit, clear/sync the carried source once.
        CarryOnDataManager.setCarryData(player,data);
        if(!player.isCreative()||tschipp.carryon.Constants.COMMON_CONFIG.settings.slownessInCreative)
            player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        server.swing(net.minecraft.world.InteractionHand.MAIN_HAND,true);
        // Execute the normal placement script only after ownership has transferred.
        script.ifPresent(value->{
            String command=value.scriptEffects().commandPlace();
            if(!command.isEmpty())server.getServer().getCommands().performPrefixedCommand(
                server.getServer().createCommandSourceStack(),"/execute as "+server.getGameProfile().getName()+" run "+command);
        });
        return true;
    }
    /** Spawn -> seat -> clear serialized source, all on one server thread. Failed insertions retain Carry On data. */
    private static boolean seatEntity(CargoHold hold,int slot,ServerPlayer player,tschipp.carryon.common.carry.CarryOnData data) {
        if(data.getTick()==player.tickCount)return true;
        var entry=hold.entry(slot);
        if(entry==null||entry.kind!=CargoEntry.Kind.STOOL||!hold.valid(entry,player)) {
            CargoHold.message(player,"message.tm_wagon.cargo_unsupported");return true;
        }
        // getEntity clears invalid data in Carry On; inspect a detached copy instead.
        var copy=new tschipp.carryon.common.carry.CarryOnData(data.getNbt().copy());
        var entity=copy.getEntity(player.level());
        if(!(entity instanceof net.minecraft.world.entity.LivingEntity rider)||!CargoSeats.eligible(rider)) {
            CargoHold.message(player,"message.tm_wagon.stool_cannot_sit");return true;
        }
        rider.setPos(hold.owner().cargoPose().point(hold.centreAt(slot)));
        if(!hold.seats.available(slot,rider)||!player.level().mayInteract(player,BlockPos.containing(rider.position()))) {
            CargoHold.message(player,"message.tm_wagon.stool_cannot_sit");return true;
        }
        if(rider instanceof net.minecraft.world.entity.Mob mob) {
            var check=new net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck(mob,player.serverLevel(),net.minecraft.world.entity.MobSpawnType.EVENT,null);
            NeoForge.EVENT_BUS.post(check);
            if(check.getResult()==net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result.FAIL)return true;
        }
        for(var level:player.server.getAllLevels())if(level.getEntity(rider.getUUID())!=null)return true;
        if(!player.serverLevel().addFreshEntity(rider))return true;
        boolean committed=false;var script=data.getActiveScript();
        try {
            String error=hold.seats.sit(slot,rider);
            if(error!=null) { CargoHold.message(player,error);return true; }
            // The maid may immediately dismount when another task is selected;
            // that is still a successful transfer to the world, not a rollback.
            data.clear();data.setTick(player.tickCount);committed=true;
        } finally { if(!committed) { rider.stopRiding();rider.discard(); } }
        CarryOnDataManager.setCarryData(player,data);
        if(!player.isCreative()||tschipp.carryon.Constants.COMMON_CONFIG.settings.slownessInCreative)
            player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND,true);
        script.ifPresent(value->{String command=value.scriptEffects().commandPlace();if(!command.isEmpty())
            player.server.getCommands().performPrefixedCommand(player.server.createCommandSourceStack(),
                "/execute as "+player.getGameProfile().getName()+" run "+command);});
        return true;
    }
    private static <T extends Comparable<T>> BlockState copyProperty(BlockState target,BlockState source,net.minecraft.world.level.block.state.properties.Property<T> property) {
        return target.setValue(property,source.getValue(property));
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
        if(com.sange.tm_wagon.compat.BackpackCompat.matches(entry.item)) {
            if(!com.sange.tm_wagon.compat.BackpackCompat.sophisticated(entry.item))be.setLevel(level);
            com.sange.tm_wagon.compat.BackpackCompat.snapshot(be,entry.item,level.registryAccess());
            be.setLevel(level); // Carry On serializes using the BE's registry access.
            return be;
        }
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
