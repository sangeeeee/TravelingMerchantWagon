package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.CargoConfig;
import com.sange.tm_wagon.assembly.WagonGeometry;
import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.entity.WagonEntity;
import com.sange.tm_wagon.physics.WagonPose;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Ten fixed positions; only occupied slots are ticked. Inventories are not included in render updates. */
public final class CargoHold {
    public static final int CAPACITY=10,GATE_TICKS=16;
    public static final double SCALE=.68,FLOOR=1.5;
    public static final TagKey<Item> DISALLOWED=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("tm_wagon","disallowed_cargo"));
    private final CargoOwner owner;
    private final CargoEntry[] entries=new CargoEntry[CAPACITY];
    private boolean gateTarget,gateCollision;
    private long gateStart=Long.MIN_VALUE;
    private float gateFrom;
    private boolean loading;
    public CargoHold(CargoOwner owner) { this.owner=owner; }
    public CargoOwner owner() { return owner; }
    public CargoEntry entry(int slot) { int anchor=anchorSlot(slot);return anchor<0?null:entries[anchor]; }
    /** Reserved cells reference one authoritative entry; save, drops and ticking visit anchors only. */
    public int anchorSlot(int slot) {
        if(slot<0||slot>=CAPACITY)return -1;
        if(entries[slot]!=null)return slot;
        for(int anchor=slot+2;anchor<CAPACITY&&anchor<=slot+4;anchor+=2)
            if(entries[anchor]!=null&&entries[anchor].kind==CargoEntry.Kind.STRAW_MAT)return anchor;
        return -1;
    }
    public static AABB matBox(int anchor) {
        Vec3 p=centre(anchor);return new AABB(p.x-.43,FLOOR+.002,p.z-1.4-.34,p.x+.43,FLOOR+.125,p.z+.34);
    }
    public AABB entryBox(int anchor) { return entries[anchor].kind==CargoEntry.Kind.STRAW_MAT?matBox(anchor):slotBox(anchor); }
    public static Vec3 centre(int slot) { return new Vec3(slot%2==0?-.5:.5,FLOOR,-.96+slot/2*.70); }
    public static AABB slotBox(int slot) {
        Vec3 p=centre(slot);return new AABB(p.x-SCALE/2,p.y,p.z-SCALE/2,p.x+SCALE/2,p.y+SCALE,p.z+SCALE/2);
    }
    public boolean empty() { for(var e:entries)if(e!=null)return false;return true; }
    public int slot(CargoEntry entry) { for(int i=0;i<CAPACITY;i++)if(entries[i]==entry)return i;return -1; }
    public Vec3 position(CargoEntry entry) { int slot=slot(entry);return owner.cargoPose().point(slot<0?new Vec3(0,FLOOR,0):centre(slot)); }
    public boolean valid(CargoEntry entry,Player player) {
        return owner.cargoLive()&&!owner.cargoBusy()&&slot(entry)>=0&&entry.hold==this&&player.isAlive()
            &&player.level()==owner.cargoLevel()&&player.distanceToSqr(position(entry))<=64;
    }
    public void changed(boolean visible) { if(!loading&&owner.cargoLevel()!=null&&!owner.cargoLevel().isClientSide)owner.cargoChanged(visible); }
    public List<AABB> boxes() { var result=new ArrayList<AABB>();for(int i=0;i<CAPACITY;i++)if(entries[i]!=null)result.add(entryBox(i));return result; }
    public AABB tailBox() {
        AABB box=WagonGeometry.partBoxes(WagonPart.CARGO_BODY).get(4);
        return gateCollision?new AABB(box.minX,3.0625-box.maxY,4.70-box.maxZ,box.maxX,3.0625-box.minY,4.70-box.minZ):box;
    }
    public List<AABB> bodyBoxes() {
        var boxes=new ArrayList<>(WagonGeometry.partBoxes(WagonPart.CARGO_BODY));boxes.set(4,tailBox());boxes.addAll(boxes());return boxes;
    }
    public float gateProgress(float partial) {
        if(gateStart==Long.MIN_VALUE||owner.cargoLevel()==null)return gateTarget?1:0;
        float t=(float)Math.clamp((owner.cargoLevel().getGameTime()+partial-gateStart)/GATE_TICKS,0,1);
        t=t*t*(3-2*t);return gateFrom+((gateTarget?1:0)-gateFrom)*t;
    }
    public boolean gateMoving() { return gateStart!=Long.MIN_VALUE; }
    public boolean gateOpen() { return gateCollision; }
    public String toggleGate() {
        if(owner.cargoBusy()||gateMoving())return "message.tm_wagon.assembly_busy";
        gateFrom=gateProgress(0);gateTarget=!gateTarget;gateStart=owner.cargoLevel().getGameTime();
        Vec3 p=owner.cargoPose().point(new Vec3(0,1.53,2.35));
        owner.cargoLevel().playSound(null,p.x,p.y,p.z,gateTarget?net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_OPEN:net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,.65F,1);
        changed(true);return null;
    }
    public void tick() {
        if(owner.cargoLevel()==null||owner.cargoLevel().isClientSide||!owner.cargoLive())return;
        if(gateStart!=Long.MIN_VALUE&&owner.cargoLevel().getGameTime()-gateStart>=GATE_TICKS) {
            boolean old=gateCollision;gateCollision=gateTarget;
            String error=old!=gateTarget&&!freeVolume(worldBox(tailBox(),owner.cargoPose()))?"message.tm_wagon.cargo_blocked":owner.cargoGeometryChanged();
            if(error==null) { gateStart=Long.MIN_VALUE;changed(true); }
            else { gateCollision=old;gateFrom=gateTarget?1:0;gateTarget=old;gateStart=owner.cargoLevel().getGameTime();changed(true); }
        }
        for(var entry:entries)if(entry!=null)entry.tick();
    }
    private int selected(Vec3 point) {
        for(int i=0;i<CAPACITY;i++)if(entries[i]!=null&&entryBox(i).inflate(.015).contains(point))return i;
        if(point.y<FLOOR-.14||point.y>FLOOR+.025||Math.abs(point.x)>1||point.z< -1.34375||point.z>2.21875)return -1;
        int row=Math.clamp((int)Math.round((point.z+.96)/.70),0,4);int selected=row*2+(point.x<0?0:1);
        int anchor=anchorSlot(selected);return anchor<0?selected:anchor;
    }
    /** Called with the first actual cart-surface hit, so a wall cannot be clicked through. */
    public InteractionResult interact(Player player,InteractionHand hand,Vec3 local) {
        if(!owner.cargoLive())return InteractionResult.PASS;
        int slot=selected(local);var stack=player.getItemInHand(hand);
        if(slot<0) {
            if(!tailBox().inflate(.025).contains(local))return InteractionResult.PASS;
            if(!owner.cargoLevel().isClientSide)message(player,toggleGate());
            return InteractionResult.sidedSuccess(owner.cargoLevel().isClientSide);
        }
        if(owner.cargoBusy()) { if(!owner.cargoLevel().isClientSide)message(player,"message.tm_wagon.assembly_busy");return InteractionResult.FAIL; }
        if(owner.cargoLevel().isClientSide)return InteractionResult.SUCCESS;
        if(player.isSecondaryUseActive()) {
            if(entries[slot]!=null)message(player,take(slot,player));
            return InteractionResult.CONSUME;
        }
        if(entries[slot]!=null) {
            if(entries[slot].kind==CargoEntry.Kind.STRAW_MAT)message(player,StrawMatSleep.sleep(this,entries[slot],player));
            else if(entries[slot].kind!=CargoEntry.Kind.ORDINARY)CargoWorkBlocks.interact(this,entries[slot],player,hand,local);
            else if(stack.getItem() instanceof BlockItem)message(player,"message.tm_wagon.cargo_occupied");
            return InteractionResult.CONSUME;
        }
        if(!(stack.getItem() instanceof BlockItem||stack.getItem() instanceof StrawMatItem))return InteractionResult.CONSUME;
        message(player,place(slot,stack,player));return InteractionResult.CONSUME;
    }
    private static void message(Player player,String error) { if(error!=null)player.displayClientMessage(Component.translatable(error),true); }
    public static boolean allowed(ItemStack item) {
        return placementRestriction(item)==null;
    }
    private static String placementRestriction(ItemStack item) {
        if(!item.isEmpty()&&item.getItem() instanceof StrawMatItem)return null;
        if(item.isEmpty()||!(item.getItem() instanceof BlockItem blockItem)||item.is(DISALLOWED))return "message.tm_wagon.cargo_unsupported";
        Block block=blockItem.getBlock();
        if(block instanceof BedBlock||block instanceof DoorBlock||block instanceof DoublePlantBlock)return "message.tm_wagon.cargo_unsupported";
        return CargoConfig.allows(block)?null:"message.tm_wagon.cargo_filtered";
    }
    public String place(int slot,ItemStack stack,Player player) {
        if(!owner.cargoLive()||owner.cargoBusy()||slot<0||slot>=CAPACITY)return "message.tm_wagon.assembly_busy";
        String restriction=placementRestriction(stack);if(restriction!=null)return restriction;
        boolean mat=stack.getItem() instanceof StrawMatItem;
        if(mat) {
            if(slot<4||entry(slot)!=null||entry(slot-2)!=null||entry(slot-4)!=null)return "message.tm_wagon.mat_space";
        } else if(entry(slot)!=null)return "message.tm_wagon.cargo_occupied";
        Vec3 point=owner.cargoPose().point(centre(slot));
        if(player==null||player.level()!=owner.cargoLevel()||player.distanceToSqr(point)>64
            ||!owner.cargoLevel().mayInteract(player,BlockPos.containing(point)))return "message.tm_wagon.protected";
        var state=mat?Blocks.HAY_BLOCK.defaultBlockState():CargoPlacement.state(this,slot,stack,player);
        var properties=stack.get(DataComponents.BLOCK_STATE);if(properties!=null)state=properties.apply(state);
        if(state.hasProperty(ChestBlock.TYPE))state=state.setValue(ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.SINGLE);
        if(state.getBlock() instanceof ShulkerBoxBlock)state=state.setValue(ShulkerBoxBlock.FACING,net.minecraft.core.Direction.UP);
        if(mat)for(int cell=slot;cell>=slot-4;cell-=2)
            if(!owner.cargoLevel().mayInteract(player,BlockPos.containing(owner.cargoPose().point(centre(cell)))))return "message.tm_wagon.protected";
        AABB world=worldBox(mat?matBox(slot):slotBox(slot),owner.cargoPose());
        if(!freeVolume(world))return "message.tm_wagon.cargo_blocked";
        CargoEntry entry=CargoEntry.fromItem(this,stack,state);entries[slot]=entry;
        String error=owner.cargoGeometryChanged();
        if(error!=null) { entries[slot]=null;return error; }
        if(!player.getAbilities().instabuild)stack.shrink(1);changed(true);cargoSound(state,point,player,true);return null;
    }
    /** Vanilla anvil wear consumes the workstation, not a block at its current world position. */
    void consume(CargoEntry entry) {
        int slot=slot(entry);if(slot<0)return;entries[slot]=null;owner.cargoGeometryChanged();changed(true);
    }
    /** Bounded to one cargo/gate volume; skip this host's proxy blocks and use actual cart geometry. */
    private boolean freeVolume(AABB volume) {
        AABB box=volume.deflate(.001);var level=owner.cargoLevel();
        var shape=net.minecraft.world.phys.shapes.Shapes.create(box);
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))) {
            if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||level.isOutsideBuildHeight(pos))return false;
            if(com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity.find(level,pos)==owner)continue;
            if(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape,level.getBlockState(pos).getCollisionShape(level,pos).move(pos.getX(),pos.getY(),pos.getZ()),net.minecraft.world.phys.shapes.BooleanOp.AND))return false;
        }
        for(var entity:level.getEntities(null,box)) {
            if(entity==owner||entity.isRemoved())continue;
            if(entity instanceof WagonEntity wagon) { if(wagon.intersects(box))return false; }
            else if(entity instanceof LivingEntity||entity.canBeCollidedWith())return false;
        }
        return true;
    }
    public String take(int slot,Player player) {
        var entry=entry(slot);if(entry==null||!valid(entry,player))return "message.tm_wagon.assembly_busy";
        slot=slot(entry);StrawMatSleep.wake(this,entry);
        CargoMenus.close(this,entry);Vec3 position=position(entry);BlockState state=entry.state;entries[slot]=null;
        String error=owner.cargoGeometryChanged();if(error!=null) { entries[slot]=entry;return error; }
        ItemStack returned=entry.returnedItem();
        // Clear ownership before any items enter a player inventory or spawn in the world.
        dropContents(entry,position,player);
        player.getInventory().placeItemBackInInventory(returned);
        changed(true);cargoSound(state,position,player,false);return null;
    }
    /** Broadcast once after committing the transaction, including to the interacting player. */
    private void cargoSound(BlockState state,Vec3 position,Player player,boolean placing) {
        var level=owner.cargoLevel();if(level.isClientSide)return;
        SoundType type;
        try { type=state.getSoundType(level,BlockPos.containing(position),player); }
        catch(RuntimeException unsupportedContext) { type=SoundType.WOOD; }
        if(type==null)type=SoundType.WOOD;
        var sound=placing?type.getPlaceSound():type.getBreakSound();
        if(sound==null) { type=SoundType.WOOD;sound=placing?type.getPlaceSound():type.getBreakSound(); }
        level.playSound(null,position.x,position.y,position.z,sound,SoundSource.BLOCKS,
            placing?(type.getVolume()+1)/2:type.getVolume(),placing?type.getPitch()*.8F:type.getPitch());
    }
    private void dropContents(CargoEntry entry,Vec3 position,Player player) {
        if(entry.kind!=CargoEntry.Kind.SHULKER)for(int i=0;i<entry.inventory.getContainerSize();i++) {
            ItemStack stack=entry.inventory.removeItemNoUpdate(i);if(!stack.isEmpty())drop(position,stack);
        }
        entry.inventory.clearContent();entry.popExperience(player);
    }
    private void drop(Vec3 position,ItemStack stack) {
        var entity=new net.minecraft.world.entity.item.ItemEntity(owner.cargoLevel(),position.x,position.y+.1,position.z,stack);
        entity.setDefaultPickUpDelay();owner.cargoLevel().addFreshEntity(entity);
    }
    public void destroy(boolean drops) {
        StrawMatSleep.wake(this,null);
        closeMenus();
        for(int i=0;i<CAPACITY;i++) {
            var entry=entries[i];if(entry==null)continue;Vec3 pos=position(entry);entries[i]=null;
            if(drops) { drop(pos,entry.returnedItem());dropContents(entry,pos,null); }
            else entry.inventory.clearContent();
        }
    }
    public void closeMenus() { StrawMatSleep.wake(this,null);CargoMenus.close(this,null); }
    /** Called only after target creation/layout has committed, on the server thread. */
    public void transferTo(CargoHold target) {
        if(target==this||!target.empty())throw new IllegalStateException("Cargo target already owns entries");
        closeMenus();
        for(int i=0;i<CAPACITY;i++) { target.entries[i]=entries[i];entries[i]=null;if(target.entries[i]!=null)target.entries[i].hold=target; }
        target.gateTarget=gateTarget;target.gateCollision=gateCollision;target.gateStart=gateStart;target.gateFrom=gateFrom;
        gateTarget=gateCollision=false;gateStart=Long.MIN_VALUE;gateFrom=0;changed(true);target.changed(true);
    }
    public CompoundTag save(HolderLookup.Provider lookup,boolean visual) {
        var tag=new CompoundTag();var list=new ListTag();
        for(int i=0;i<CAPACITY;i++)if(entries[i]!=null) { var saved=entries[i].save(lookup,visual);saved.putInt("Slot",i);list.add(saved); }
        tag.put("Entries",list);tag.putBoolean("GateTarget",gateTarget);tag.putBoolean("GateCollision",gateCollision);tag.putLong("GateStart",gateStart);tag.putFloat("GateFrom",gateFrom);return tag;
    }
    public void load(CompoundTag tag,HolderLookup.Provider lookup) {
        if(owner.cargoLevel()!=null&&!owner.cargoLevel().isClientSide)StrawMatSleep.wake(this,null);
        loading=true;java.util.Arrays.fill(entries,null);
        for(var value:tag.getList("Entries",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)value;int slot=entry.getInt("Slot");if(slot<0||slot>=CAPACITY||entry(slot)!=null)continue;
            var loaded=CargoEntry.load(this,entry,lookup);if(loaded==null)continue;
            if(loaded.kind==CargoEntry.Kind.STRAW_MAT&&(slot<4||entry(slot-2)!=null||entry(slot-4)!=null))continue;
            entries[slot]=loaded;
        }
        gateTarget=tag.getBoolean("GateTarget");gateCollision=tag.getBoolean("GateCollision");gateStart=tag.contains("GateStart")?tag.getLong("GateStart"):Long.MIN_VALUE;gateFrom=Math.clamp(tag.getFloat("GateFrom"),0,1);loading=false;
    }
    public static AABB worldBox(AABB box,WagonPose pose) {
        AABB result=null;
        for(double x:new double[]{box.minX,box.maxX})for(double y:new double[]{box.minY,box.maxY})for(double z:new double[]{box.minZ,box.maxZ}) {
            Vec3 point=pose.point(new Vec3(x,y,z));AABB corner=new AABB(point,point);result=result==null?corner:result.minmax(corner);
        }return result;
    }
}
