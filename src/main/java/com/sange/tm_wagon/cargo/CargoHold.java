package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.CargoConfig;
import com.sange.tm_wagon.assembly.WagonGeometry;
import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.assembly.WagonContent;
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

/** Dimensioned fixed positions; only occupied slots are ticked. Inventories are not included in render updates. */
public final class CargoHold {
    public static final int CAPACITY=10,MAX_CAPACITY=32,GATE_TICKS=16;
    public static final double SCALE=.68,FLOOR=1.5;
    /** A rejected action, deliberately silent instead of a user-facing translation key. */
    static final String ACCESS_BLOCKED="tm_wagon:access_blocked";
    public static final TagKey<Item> DISALLOWED=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("tm_wagon","disallowed_cargo"));
    private final CargoOwner owner;
    public final CargoSeats seats=new CargoSeats(this);
    private final CargoCover cover=new CargoCover(this);
    public CargoCover cover() { return cover; }
    private final CargoCanopy canopy=new CargoCanopy(this);
    public CargoCanopy canopy() { return canopy; }
    private final WagonCabinet cabinet=new WagonCabinet(this);
    public WagonCabinet cabinet() { return cabinet; }
    private final CargoEntry[] entries=new CargoEntry[MAX_CAPACITY];
    private boolean gateTarget,gateCollision;
    private long gateStart=Long.MIN_VALUE;
    private float gateFrom;
    private boolean loading;
    public CargoHold(CargoOwner owner) { this.owner=owner; }
    public CargoOwner owner() { return owner; }
    public int capacity() { return owner.cargoBody().cargoCapacity(); }
    /** At most 32 references, no inventory/NBT inspection. Mats reserve three cells. */
    public int occupiedSlots() {
        int count=0;
        for(int i=0;i<capacity();i++)if(entries[i]!=null)count+=entries[i].kind==CargoEntry.Kind.STRAW_MAT?3:1;
        return count;
    }
    public int columns() { return owner.cargoBody().columns(); }
    public int rows() { return owner.cargoBody().rows(); }
    public double rearExtension() { return owner.cargoBody().rearExtension(); }
    public CargoEntry entry(int slot) { int anchor=anchorSlot(slot);return anchor<0?null:entries[anchor]; }
    /** Reserved cells reference one authoritative entry; save, drops and ticking visit anchors only. */
    public int anchorSlot(int slot) {
        if(slot<0||slot>=capacity())return -1;
        if(entries[slot]!=null)return slot;
        for(int anchor=slot+columns();anchor<capacity()&&anchor<=slot+2*columns();anchor+=columns())
            if(entries[anchor]!=null&&entries[anchor].kind==CargoEntry.Kind.STRAW_MAT)return anchor;
        return -1;
    }
    public static AABB matBox(int anchor) { return matBox(anchor,WagonPart.CARGO_BODY); }
    public static AABB matBox(int anchor,WagonPart body) {
        Vec3 p=centre(anchor,body);return new AABB(p.x-.43,FLOOR+.002,p.z-1.4-.34,p.x+.43,FLOOR+.125,p.z+.34);
    }
    public static AABB stoolBox(int slot) { return stoolBox(slot,WagonPart.CARGO_BODY); }
    public static AABB stoolBox(int slot,WagonPart body) {
        var box=slotBox(slot,body);return new AABB(box.minX,box.minY,box.minZ,box.maxX,FLOOR+.5,box.maxZ);
    }
    public AABB entryBox(int anchor) { return switch(entries[anchor].kind) {
        case STRAW_MAT->matBounds(anchor);case STOOL->stoolBounds(anchor);default->slotBounds(anchor);
    }; }
    public Vec3 centreAt(int slot) { return centre(slot,owner.cargoBody()); }
    public AABB slotBounds(int slot) { return slotBox(slot,owner.cargoBody()); }
    public AABB matBounds(int slot) { return matBox(slot,owner.cargoBody()); }
    public AABB stoolBounds(int slot) { return stoolBox(slot,owner.cargoBody()); }
    public static Vec3 centre(int slot) { return centre(slot,WagonPart.CARGO_BODY); }
    public static Vec3 centre(int slot,WagonPart body) {
        return new Vec3((slot%body.columns()-(body.columns()-1)/2.0)*body.columnSpacing(),FLOOR,body.firstRowZ()+slot/body.columns()*.70);
    }
    public static AABB slotBox(int slot) { return slotBox(slot,WagonPart.CARGO_BODY); }
    public static AABB slotBox(int slot,WagonPart body) {
        Vec3 p=centre(slot,body);return new AABB(p.x-SCALE/2,p.y,p.z-SCALE/2,p.x+SCALE/2,p.y+SCALE,p.z+SCALE/2);
    }
    public boolean empty() { if(cover.installed()||canopy.installed()||cabinet.installed())return false;for(var e:entries)if(e!=null)return false;return true; }
    public int slot(CargoEntry entry) { for(int i=0;i<MAX_CAPACITY;i++)if(entries[i]==entry)return i;return -1; }
    public Vec3 position(CargoEntry entry) { int slot=slot(entry);return owner.cargoPose().point(slot<0?new Vec3(0,FLOOR,0):centreAt(slot)); }
    public boolean valid(CargoEntry entry,Player player) {
        return owner.cargoLive()&&!owner.cargoBusy()&&slot(entry)>=0&&entry.hold==this&&player.isAlive()
            &&player.level()==owner.cargoLevel()&&player.distanceToSqr(position(entry))<=64&&!coverObstructed(slot(entry),player);
    }
    private boolean coverObstructed(int slot,Player player) {
        if(!cover.installed()&&!canopy.installed())return false;
        if(player==null)return true;
        Vec3 eye=owner.cargoPose().local(player.getEyePosition());
        int anchor=anchorSlot(slot);AABB box=anchor<0?slotBounds(slot):entryBox(anchor);
        Vec3 target=new Vec3(net.minecraft.util.Mth.clamp(eye.x,box.minX,box.maxX),
            net.minecraft.util.Mth.clamp(eye.y,box.minY,box.maxY),net.minecraft.util.Mth.clamp(eye.z,box.minZ,box.maxZ));
        if(!cover.obstructs(eye,target))return false;
        // A visible lower face still permits access when cloth obscures only the upper edge.
        return cover.obstructs(eye,new Vec3(target.x,box.minY+.001,target.z))
            &&cover.obstructs(eye,new Vec3(target.x,(box.minY+box.maxY)/2,target.z));
    }
    public void changed(boolean visible) { if(!loading&&owner.cargoLevel()!=null&&!owner.cargoLevel().isClientSide)owner.cargoChanged(visible); }
    public List<AABB> boxes() { var result=new ArrayList<AABB>();for(int i=0;i<MAX_CAPACITY;i++)if(entries[i]!=null)result.add(entryBox(i));return result; }
    public AABB tailBox() { return tailBox(owner.cargoBody()); }
    private AABB tailBox(WagonPart body) {
        AABB box=WagonGeometry.partBoxes(body).get(4);double hinge=4.70+body.rearExtension()*2;
        return gateCollision?new AABB(box.minX,3.0625-box.maxY,hinge-box.maxZ,box.maxX,3.0625-box.minY,hinge-box.minZ):box;
    }
    public List<AABB> bodyBoxes() { return bodyBoxes(owner.cargoBody()); }
    public List<AABB> structuralBodyBoxes(WagonPart body) {
        var boxes=new ArrayList<>(WagonGeometry.partBoxes(body));boxes.set(4,tailBox(body));return boxes;
    }
    public List<AABB> bodyBoxes(WagonPart body) {
        var boxes=structuralBodyBoxes(body);boxes.addAll(boxes());boxes.addAll(cover.boxes(body));boxes.addAll(canopy.boxes(body));return boxes;
    }
    public float gateProgress(float partial) {
        if(gateStart==Long.MIN_VALUE||owner.cargoLevel()==null)return gateTarget?1:0;
        float t=(float)Math.clamp((owner.cargoLevel().getGameTime()+partial-gateStart)/GATE_TICKS,0,1);
        t=t*t*(3-2*t);return gateFrom+((gateTarget?1:0)-gateFrom)*t;
    }
    public boolean gateMoving() { return gateStart!=Long.MIN_VALUE; }
    public boolean gateOpen() { return gateCollision; }
    public String toggleGate() {
        if(owner.cargoBusy())return "message.tm_wagon.assembly_busy";
        if(gateMoving())return ACCESS_BLOCKED;
        gateFrom=gateProgress(0);gateTarget=!gateTarget;gateStart=owner.cargoLevel().getGameTime();
        Vec3 p=owner.cargoPose().point(new Vec3(0,1.53,2.35+rearExtension()));
        owner.cargoLevel().playSound(null,p.x,p.y,p.z,gateTarget?net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_OPEN:net.minecraft.sounds.SoundEvents.WOODEN_TRAPDOOR_CLOSE,net.minecraft.sounds.SoundSource.BLOCKS,.65F,1);
        changed(true);return null;
    }
    public void tick() {
        if(owner.cargoLevel()==null||owner.cargoLevel().isClientSide||!owner.cargoLive())return;
        if(gateStart!=Long.MIN_VALUE&&owner.cargoLevel().getGameTime()-gateStart>=GATE_TICKS) {
            boolean old=gateCollision;gateCollision=gateTarget;
            String error=old!=gateTarget&&!freeLocalVolume(tailBox())?"message.tm_wagon.cargo_blocked":owner.cargoGeometryChanged();
            if(error==null) { gateStart=Long.MIN_VALUE;changed(true); }
            else { gateCollision=old;gateFrom=gateTarget?1:0;gateTarget=old;gateStart=owner.cargoLevel().getGameTime();changed(true); }
        }
        for(var entry:entries)if(entry!=null)entry.tick();
        seats.tick();
    }
    private int selected(Vec3 point) {
        // Proxy shapes round out to 1/32 block; match those hits without choosing a farther neighbour.
        int nearest=-1;double distance=Double.POSITIVE_INFINITY;
        for(int i=0;i<capacity();i++)if(entries[i]!=null) {
            AABB box=entryBox(i);if(!box.inflate(1.0/32+.001).contains(point))continue;
            Vec3 surface=new Vec3(net.minecraft.util.Mth.clamp(point.x,box.minX,box.maxX),
                net.minecraft.util.Mth.clamp(point.y,box.minY,box.maxY),net.minecraft.util.Mth.clamp(point.z,box.minZ,box.maxZ));
            double candidate=point.distanceToSqr(surface);
            if(candidate<distance) { nearest=i;distance=candidate; }
        }
        if(nearest>=0)return nearest;
        if(point.y<FLOOR-.14||point.y>FLOOR+.025||Math.abs(point.x)>owner.cargoBody().widthScale()||point.z< -1.34375+owner.cargoBody().frontOffset()||point.z>2.21875+rearExtension())return -1;
        var body=owner.cargoBody();
        int row=Math.clamp((int)Math.round((point.z-body.firstRowZ())/.70),0,rows()-1);
        int column=Math.clamp((int)Math.round(point.x/body.columnSpacing()+(columns()-1)/2.0),0,columns()-1);
        int selected=row*columns()+column;
        int anchor=anchorSlot(selected);return anchor<0?selected:anchor;
    }
    /** Called with the first actual cart-surface hit, so a wall cannot be clicked through. */
    public InteractionResult interact(Player player,InteractionHand hand,Vec3 local) {
        if(!owner.cargoLive())return InteractionResult.PASS;
        var maidResult=com.sange.tm_wagon.compat.MaidCompat.interact(this,player,hand,local);
        if(maidResult!=InteractionResult.PASS)return maidResult;
        var cabinetResult=cabinet.interact(player,hand,local);if(cabinetResult!=InteractionResult.PASS)return cabinetResult;
        var canopyResult=canopy.interact(player,hand,local);if(canopyResult!=InteractionResult.PASS)return canopyResult;
        var coverResult=cover.interact(player,hand,local);if(coverResult!=InteractionResult.PASS)return coverResult;
        int slot=selected(local);var stack=player.getItemInHand(hand);
        if(slot>=0&&cover.obstructs(owner.cargoPose().local(player.getEyePosition()),local)) {
            return InteractionResult.sidedSuccess(owner.cargoLevel().isClientSide);
        }
        if(slot<0) {
            if(!tailBox().inflate(.025).contains(local))return InteractionResult.PASS;
            if(!owner.cargoLevel().isClientSide)message(player,toggleGate());
            return InteractionResult.sidedSuccess(owner.cargoLevel().isClientSide);
        }
        if(owner.cargoBusy()) { if(!owner.cargoLevel().isClientSide)message(player,"message.tm_wagon.assembly_busy");return InteractionResult.FAIL; }
        if(owner.cargoLevel().isClientSide)return InteractionResult.SUCCESS;
        if(hand==InteractionHand.MAIN_HAND&&com.sange.tm_wagon.compat.CarryOnCompat.place(this,slot,player))return InteractionResult.CONSUME;
        if(hand==InteractionHand.MAIN_HAND&&com.sange.tm_wagon.compat.CarryOnCompat.pickup(this,entries[slot],player))return InteractionResult.CONSUME;
        if(entries[slot]!=null&&entries[slot].kind==CargoEntry.Kind.STOOL&&stack.is(net.minecraft.world.item.Items.LEAD)
            &&seats.seatLeashed(slot,player))return InteractionResult.CONSUME;
        if(player.isSecondaryUseActive()) {
            if(entries[slot]!=null)message(player,take(slot,player));
            else if(com.sange.tm_wagon.compat.BackpackCompat.matches(stack))message(player,place(slot,stack,player));
            return InteractionResult.CONSUME;
        }
        if(entries[slot]!=null) {
            if(com.sange.tm_wagon.compat.BackpackCompat.matches(entries[slot].item))com.sange.tm_wagon.compat.BackpackCompat.open(this,entries[slot],player);
            else if(entries[slot].kind==CargoEntry.Kind.STRAW_MAT)message(player,StrawMatSleep.sleep(this,entries[slot],player,local));
            else if(entries[slot].kind==CargoEntry.Kind.STOOL)message(player,seats.sit(slot,player));
            else if(entries[slot].kind!=CargoEntry.Kind.ORDINARY)CargoWorkBlocks.interact(this,entries[slot],player,hand,local);
            else if(stack.getItem() instanceof BlockItem)message(player,"message.tm_wagon.cargo_occupied");
            return InteractionResult.CONSUME;
        }
        if(com.sange.tm_wagon.compat.BackpackCompat.matches(stack)) {
            // Both audited mods hard-code their placement gesture to player sneaking.
            // Ordinary use retains the held backpack's own opening/configuration behavior.
            stack.getItem().use(owner.cargoLevel(),player,hand);return InteractionResult.CONSUME;
        }
        if(!(stack.getItem() instanceof BlockItem||stack.getItem() instanceof StrawMatItem||stack.getItem() instanceof WagonStoolItem))return InteractionResult.CONSUME;
        message(player,place(slot,stack,player));return InteractionResult.CONSUME;
    }
    static void message(Player player,String error) { if(error!=null&&!ACCESS_BLOCKED.equals(error))player.displayClientMessage(Component.translatable(error),true); }
    public static boolean allowed(ItemStack item) {
        return placementRestriction(item)==null;
    }
    static String placementRestriction(ItemStack item) {
        if(!item.isEmpty()&&(item.getItem() instanceof StrawMatItem||item.getItem() instanceof WagonStoolItem))return null;
        if(item.isEmpty()||item.is(DISALLOWED))return "message.tm_wagon.cargo_unsupported";
        Block block=item.getItem() instanceof BlockItem blockItem?blockItem.getBlock():com.sange.tm_wagon.compat.BackpackCompat.block(item);
        if(block==null)return "message.tm_wagon.cargo_unsupported";
        if(block instanceof BedBlock||block instanceof DoorBlock||block instanceof DoublePlantBlock)return "message.tm_wagon.cargo_unsupported";
        return CargoConfig.allows(block)?null:"message.tm_wagon.cargo_filtered";
    }
    public String place(int slot,ItemStack stack,Player player) {
        return place(slot,stack,player,null,null);
    }
    /** The carried source is cleared only after cargo geometry has committed. */
    String placeCarried(int slot,ItemStack stack,Player player,BlockState state,Runnable commitSource) {
        return place(slot,stack,player,state,commitSource);
    }
    private String place(int slot,ItemStack stack,Player player,BlockState carriedState,Runnable commitSource) {
        if(!owner.cargoLive()||owner.cargoBusy()||slot<0||slot>=capacity())return "message.tm_wagon.assembly_busy";
        if(coverObstructed(slot,player))return ACCESS_BLOCKED;
        String restriction=placementRestriction(stack);if(restriction!=null)return restriction;
        boolean mat=stack.getItem() instanceof StrawMatItem,stool=stack.getItem() instanceof WagonStoolItem;
        if(mat) {
            if(slot<2*columns()||entry(slot)!=null||entry(slot-columns())!=null||entry(slot-2*columns())!=null)return "message.tm_wagon.mat_space";
        } else if(entry(slot)!=null)return "message.tm_wagon.cargo_occupied";
        Vec3 point=owner.cargoPose().point(centreAt(slot));
        if(player==null||player.level()!=owner.cargoLevel()||player.distanceToSqr(point)>64
            ||!owner.cargoLevel().mayInteract(player,BlockPos.containing(point)))return "message.tm_wagon.protected";
        var state=carriedState!=null?carriedState:mat?Blocks.HAY_BLOCK.defaultBlockState():stool?Blocks.OAK_PLANKS.defaultBlockState():CargoPlacement.state(this,slot,stack,player);
        var properties=stack.get(DataComponents.BLOCK_STATE);if(properties!=null)state=properties.apply(state);
        if(state.hasProperty(ChestBlock.TYPE))state=state.setValue(ChestBlock.TYPE,net.minecraft.world.level.block.state.properties.ChestType.SINGLE);
        if(state.getBlock() instanceof ShulkerBoxBlock)state=state.setValue(ShulkerBoxBlock.FACING,net.minecraft.core.Direction.UP);
        if(mat)for(int cell=slot;cell>=slot-2*columns();cell-=columns())
            if(!owner.cargoLevel().mayInteract(player,BlockPos.containing(owner.cargoPose().point(centreAt(cell)))))return "message.tm_wagon.protected";
        var storageKind=CargoEntry.kind(state);
        if((storageKind==CargoEntry.Kind.CHEST||storageKind==CargoEntry.Kind.BARREL)&&CargoContainers.protectedContents(stack))
            return "message.tm_wagon.cargo_container_protected";
        if(!freeLocalVolume(mat?matBounds(slot):stool?stoolBounds(slot):slotBounds(slot)))return "message.tm_wagon.cargo_blocked";
        CargoEntry entry=CargoEntry.fromItem(this,commitSource==null?com.sange.tm_wagon.compat.BackpackCompat.placementCopy(stack,player):stack,state);entries[slot]=entry;
        try {
            String error=owner.cargoGeometryChanged();
            if(error!=null) { entries[slot]=null;return error; }
        } catch(RuntimeException failure){entries[slot]=null;throw failure;}
        if(commitSource!=null)commitSource.run();
        else if(!player.getAbilities().instabuild)stack.shrink(1);
        changed(true);cargoSound(state,point,player,true);return null;
    }
    /** Vanilla anvil wear consumes the workstation, not a block at its current world position. */
    void consume(CargoEntry entry) {
        int slot=slot(entry);if(slot<0)return;entries[slot]=null;owner.cargoGeometryChanged();changed(true);
    }
    /** Bounded to one cargo/gate volume; skip this host's proxy blocks and use actual cart geometry. */
    boolean freeLocalVolume(AABB local) {
        var volume=com.sange.tm_wagon.physics.OrientedBox.at(local.deflate(.001),owner.cargoPose());
        AABB box=volume.bounds();var level=owner.cargoLevel();
        if(!com.sange.tm_wagon.compat.StructureCollision.clear(level,volume))return false;
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))) {
            if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||level.isOutsideBuildHeight(pos))return false;
            if(com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity.find(level,pos)==owner)continue;
            for(AABB block:level.getBlockState(pos).getCollisionShape(level,pos).toAabbs())
                if(volume.intersects(block.move(pos)))return false;
        }
        for(var entity:level.getEntities(null,box)) {
            if(entity==owner||entity.isRemoved())continue;
            if(entity instanceof WagonEntity wagon) {
                for(var collider:wagon.colliders())if(volume.intersects(collider))return false;
            }else if((entity instanceof LivingEntity||entity.canBeCollidedWith())&&volume.intersects(entity.getBoundingBox()))return false;
        }
        return true;
    }

    public String take(int slot,Player player) {
        var entry=entry(slot);if(entry!=null&&coverObstructed(slot(entry),player))return ACCESS_BLOCKED;
        if(entry==null||!valid(entry,player))return "message.tm_wagon.assembly_busy";
        if(!com.sange.tm_wagon.compat.BackpackCompat.canTake(entry,player))return ACCESS_BLOCKED;
        slot=slot(entry);StrawMatSleep.wake(this,entry);seats.release(entry);
        CargoMenus.close(this,entry);Vec3 position=position(entry);BlockState state=entry.state;entries[slot]=null;
        String error=owner.cargoGeometryChanged();if(error!=null) { entries[slot]=entry;return error; }
        ItemStack returned=entry.returnedItem();
        // Clear ownership before any items enter a player inventory or spawn in the world.
        dropContents(entry,position,player);
        player.getInventory().placeItemBackInInventory(returned);
        changed(true);cargoSound(state,position,player,false);return null;
    }
    /** Transfer ownership without returning an item or dropping the inventory. */
    boolean releaseForCarry(CargoEntry entry,Player player) {
        if(!valid(entry,player))return false;
        CargoMenus.close(this,entry);
        int slot=slot(entry);if(slot<0)return false;
        entries[slot]=null;
        try {
            String error=owner.cargoGeometryChanged();
            if(error!=null){entries[slot]=entry;return false;}
        } catch(RuntimeException error){entries[slot]=entry;throw error;}
        return true;
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
    void drop(Vec3 position,ItemStack stack) {
        var entity=new net.minecraft.world.entity.item.ItemEntity(owner.cargoLevel(),position.x,position.y+.1,position.z,stack);
        entity.setDefaultPickUpDelay();owner.cargoLevel().addFreshEntity(entity);
    }
    public void destroy(boolean drops) {
        destroy(drops,false);
    }
    /** Tool dismantling also recovers the intact optional components; inventory ownership is still revoked once. */
    public void destroy(boolean drops,boolean dismantled) {
        ItemStack cabinetItem=dismantled&&cabinet.installed()?cabinet.material().stack(WagonContent.CABINET.get()):ItemStack.EMPTY;
        cabinet.destroy(drops);
        if(drops&&!cabinetItem.isEmpty())drop(owner.cargoPose().position(),cabinetItem);
        boolean remnants=!dismantled&&owner instanceof com.sange.tm_wagon.entity.WagonEntity;
        cover.destroy(drops&&!remnants);canopy.destroy(drops&&!remnants);
        StrawMatSleep.wake(this,null);
        closeMenus();
        for(int i=0;i<MAX_CAPACITY;i++) {
            var entry=entries[i];if(entry==null)continue;Vec3 pos=position(entry);entries[i]=null;
            if(drops) { if(!remnants||entry.kind!=CargoEntry.Kind.STOOL&&entry.kind!=CargoEntry.Kind.STRAW_MAT)drop(pos,entry.returnedItem());dropContents(entry,pos,null); }
            else entry.inventory.clearContent();
        }
    }
    /** Forced removal of selected cargo anchors; the controller rebuilds the layout once. */
    public void destroySlots(java.util.Set<Integer> anchors,boolean drops) {
        for(int slot:anchors) {
            if(slot<0||slot>=capacity())continue;
            var entry=entries[slot];if(entry==null)continue;
            StrawMatSleep.wake(this,entry);seats.release(entry);CargoMenus.close(this,entry);
            Vec3 pos=position(entry);entries[slot]=null;
            if(drops) { drop(pos,entry.returnedItem());dropContents(entry,pos,null); }
            else entry.inventory.clearContent();
        }
    }
    public void closeMenus() { cabinet.closeMenus();StrawMatSleep.wake(this,null);seats.release(null);CargoMenus.close(this,null); }
    public void closeMenusForTransfer() { cabinet.closeMenus();seats.release(null);CargoMenus.close(this,null); }
    /** Called only after target creation/layout has committed, on the server thread. */
    public void transferTo(CargoHold target) {
        if(target==this||!target.empty())throw new IllegalStateException("Cargo target already owns entries");
        for(int i=target.capacity();i<MAX_CAPACITY;i++)if(entries[i]!=null)throw new IllegalStateException("Cargo target is too small");
        closeMenusForTransfer();
        for(int i=0;i<MAX_CAPACITY;i++) { target.entries[i]=entries[i];entries[i]=null;if(target.entries[i]!=null)target.entries[i].hold=target; }
        cabinet.transferTo(target.cabinet);
        cover.transferTo(target.cover);canopy.transferTo(target.canopy);
        target.gateTarget=gateTarget;target.gateCollision=gateCollision;target.gateStart=gateStart;target.gateFrom=gateFrom;
        gateTarget=gateCollision=false;gateStart=Long.MIN_VALUE;gateFrom=0;changed(true);target.changed(true);
        StrawMatSleep.transfer(this,target);
    }
    public CompoundTag save(HolderLookup.Provider lookup,boolean visual) {
        var tag=new CompoundTag();var list=new ListTag();
        tag.put("Cabinet",cabinet.save(lookup,visual));
        for(int i=0;i<MAX_CAPACITY;i++)if(entries[i]!=null) { var saved=entries[i].save(lookup,visual);saved.putInt("Slot",i);list.add(saved); }
        tag.put("Entries",list);tag.put("Cover",cover.save());tag.put("Canopy",canopy.save());tag.putBoolean("GateTarget",gateTarget);tag.putBoolean("GateCollision",gateCollision);tag.putLong("GateStart",gateStart);tag.putFloat("GateFrom",gateFrom);return tag;
    }
    public void load(CompoundTag tag,HolderLookup.Provider lookup) {
        if(owner.cargoLevel()!=null&&!owner.cargoLevel().isClientSide)closeMenus();
        loading=true;java.util.Arrays.fill(entries,null);
        for(var value:tag.getList("Entries",Tag.TAG_COMPOUND)) {
            var entry=(CompoundTag)value;int slot=entry.getInt("Slot");if(slot<0||slot>=capacity()||entry(slot)!=null)continue;
            var loaded=CargoEntry.load(this,entry,lookup);if(loaded==null)continue;
            if(loaded.kind==CargoEntry.Kind.STRAW_MAT&&(slot<2*columns()||entry(slot-columns())!=null||entry(slot-2*columns())!=null))continue;
            entries[slot]=loaded;
        }
        cover.load(tag.getCompound("Cover"));canopy.load(tag.getCompound("Canopy"));
        cabinet.load(tag.getCompound("Cabinet"),lookup);
        gateTarget=tag.getBoolean("GateTarget");gateCollision=tag.getBoolean("GateCollision");gateStart=tag.contains("GateStart")?tag.getLong("GateStart"):Long.MIN_VALUE;gateFrom=Math.clamp(tag.getFloat("GateFrom"),0,1);loading=false;
    }
    public static AABB worldBox(AABB box,WagonPose pose) {
        AABB result=null;
        for(double x:new double[]{box.minX,box.maxX})for(double y:new double[]{box.minY,box.maxY})for(double z:new double[]{box.minZ,box.maxZ}) {
            Vec3 point=pose.point(new Vec3(x,y,z));AABB corner=new AABB(point,point);result=result==null?corner:result.minmax(corner);
        }return result;
    }
}
