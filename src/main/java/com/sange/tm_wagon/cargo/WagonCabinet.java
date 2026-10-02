package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.*;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One inventory, two independently animated drawers. Contents never enter render packets. */
@net.neoforged.fml.common.EventBusSubscriber(modid="tm_wagon")
public final class WagonCabinet {
    public static final int DRAWER_TICKS=5;
    public static final double BOTTOM=24.04/16,TOP=29.96/16,FRONT=-34.15/16,BACK=-23.52/16;
    private final CargoHold hold;
    private Store store;
    private boolean installed;
    private int rows;
    private final Drawer[] drawers={new Drawer(),new Drawer()};
    private final IdentityHashMap<Player,Integer> viewers=new IdentityHashMap<>();
    private static final class Drawer {
        boolean open;
        long start=Long.MIN_VALUE;
        float from;
    }
    /** Menus keep this identity; replacing/removing a cabinet invalidates all old access. */
    private static final class Store extends SimpleContainer {
        WagonCabinet cabinet;
        Store(WagonCabinet cabinet,int size) { super(size);this.cabinet=cabinet; }
        @Override public void setChanged() { super.setChanged();cabinet.hold.changed(false); }
        @Override public boolean stillValid(Player p) { return cabinet.store==this&&cabinet.valid(p); }
    }
    private static final class CabinetMenu extends ChestMenu {
        private final WagonCabinet cabinet;
        private final Store inventory;
        CabinetMenu(int id,net.minecraft.world.entity.player.Inventory playerInventory,WagonCabinet cabinet,Store inventory) {
            super(cabinet.rows==6?MenuType.GENERIC_9x6:MenuType.GENERIC_9x3,id,playerInventory,inventory,cabinet.rows);
            this.cabinet=cabinet;this.inventory=inventory;
        }
        @Override public boolean stillValid(Player p) { return cabinet.store==inventory&&cabinet.valid(p); }
        @Override public void removed(Player p) { super.removed(p);cabinet.closed(p); }
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void loggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        // Close before PlayerList saves the departing player. No idle wagon scans are needed.
        if(event.getEntity().containerMenu instanceof CabinetMenu)event.getEntity().closeContainer();
    }
    public WagonCabinet(CargoHold hold) { this.hold=hold; }
    public boolean installed() { return installed; }
    public int rows() { return rows; }
    public SimpleContainer inventory() { return store; }
    public static AABB box(WagonPart seat) {
        double half=(seat!=null&&seat.seatCapacity()==2?15.5:8.5)/16;
        return new AABB(-half,BOTTOM,FRONT,half,TOP,BACK);
    }
    public AABB box() { return box(rows==6?WagonPart.DOUBLE_SEAT:WagonPart.SINGLE_SEAT); }
    public List<AABB> boxes() { return installed?List.of(box()):List.of(); }
    // The existing solid lower-seat collider fully encloses this rectangular cabinet.
    // Reuse it instead of adding overlapping movement boxes for inaccessible space.
    private int side(Vec3 p) {
        var seat=hold.owner().cargoSeat();if(seat==null)return -1;
        AABB b=box(seat);
        if(p.y<BOTTOM-.025||p.y>TOP+.025||p.z<FRONT-.035||p.z>BACK+.035)return -1;
        return Math.abs(Math.abs(p.x)-b.maxX)<=.04?(p.x<0?0:1):-1;
    }
    public InteractionResult interact(Player p,InteractionHand hand,Vec3 local) {
        int side=side(local);boolean hit=installed&&box().inflate(.035).contains(local);
        boolean removing=hit&&p.isSecondaryUseActive();
        boolean installing=!installed&&side>=0&&p.getItemInHand(hand).getItem() instanceof WagonCabinetItem;
        if(!removing&&!installing&&(!installed||side<0))return InteractionResult.PASS;
        if(hold.owner().cargoLevel().isClientSide)return InteractionResult.SUCCESS;
        String error=removing?remove(p,local):installing?install(p.getItemInHand(hand),p,local):open(p,local);
        CargoHold.message(p,error);return InteractionResult.CONSUME;
    }
    public String install(ItemStack stack,Player p,Vec3 local) {
        String error=hold.cover().permission(p,local);if(error!=null)return error;
        if(!(hold.owner() instanceof AssemblyFrameBlockEntity))return "message.tm_wagon.cabinet_block_only";
        if(installed||side(local)<0||stack.isEmpty()||!(stack.getItem() instanceof WagonCabinetItem))return "message.tm_wagon.cabinet_side";
        rows=hold.owner().cargoSeat().seatCapacity()==2?6:3;installed=true;store=new Store(this,rows*9);
        if(!p.getAbilities().instabuild)stack.shrink(1);
        hold.changed(true);sound(local,SoundEvents.WOOD_PLACE);return null;
    }
    public String remove(Player p,Vec3 local) {
        String error=hold.cover().permission(p,local);if(error!=null)return error;
        if(!installed||!box().inflate(.035).contains(local))return "message.tm_wagon.cabinet_side";
        destroy(true);
        p.getInventory().placeItemBackInInventory(new ItemStack(WagonContent.CABINET.get()));
        hold.changed(true);sound(local,SoundEvents.WOOD_BREAK);return null;
    }
    public boolean valid(Player p) {
        var owner=hold.owner();Vec3 position=owner.cargoPose().point(box().getCenter());
        return installed&&store!=null&&owner.cargoLive()&&!owner.cargoBusy()&&p.isAlive()&&!p.isSpectator()
            &&p.level()==owner.cargoLevel()&&p.distanceToSqr(position)<=64;
    }
    public String open(Player p,Vec3 local) {
        String error=hold.cover().permission(p,local);if(error!=null)return error;
        int side=side(local);if(side<0||!valid(p)||!(p instanceof ServerPlayer))return "message.tm_wagon.cabinet_side";
        Store inventory=store;
        p.openMenu(new SimpleMenuProvider((id,playerInventory,player)->{
            var menu=new CabinetMenu(id,playerInventory,this,inventory);
            viewers.put(player,side);setOpen(side,true);sound(local,SoundEvents.BARREL_OPEN);return menu;
        },Component.translatable("item.tm_wagon.wagon_cabinet")));
        return null;
    }
    private void closed(Player p) {
        Integer side=viewers.remove(p);if(side==null)return;
        if(!viewers.containsValue(side))setOpen(side,false);
        var b=box();sound(new Vec3(side==0?b.minX:b.maxX,(BOTTOM+TOP)/2,(FRONT+BACK)/2),SoundEvents.BARREL_CLOSE);
    }
    public float progress(int side,float tick) {
        Drawer d=drawers[side];if(d.start==Long.MIN_VALUE||hold.owner().cargoLevel()==null)return d.open?1:0;
        float t=(float)Math.clamp((hold.owner().cargoLevel().getGameTime()+tick-d.start)/DRAWER_TICKS,0,1);
        t=t*t*(3-2*t);return d.from+((d.open?1:0)-d.from)*t;
    }
    public boolean opened(int side) { return drawers[side].open; }
    private void setOpen(int side,boolean open) {
        Drawer d=drawers[side];if(d.open==open)return;
        d.from=progress(side,0);d.start=hold.owner().cargoLevel().getGameTime();d.open=open;hold.changed(true);
    }
    public void closeMenus() {
        for(Player p:List.copyOf(viewers.keySet())) { p.closeContainer();closed(p); }
    }
    public void destroy(boolean drops) {
        if(!installed)return;closeMenus();Store old=store;Vec3 p=hold.owner().cargoPose().point(box().getCenter());
        // Revoke ownership before spawning items. A second teardown cannot drop them twice.
        installed=false;store=null;rows=0;resetDrawers();
        if(old!=null)for(int i=0;i<old.getContainerSize();i++) {
            ItemStack stack=old.removeItemNoUpdate(i);if(drops&&!stack.isEmpty())hold.drop(p,stack);
        }
    }
    void transferTo(WagonCabinet target) {
        if(target.installed||target.store!=null)throw new IllegalStateException("Cabinet target already owns inventory");
        target.installed=installed;target.rows=rows;target.store=store;
        if(store!=null)store.cabinet=target;
        installed=false;store=null;rows=0;resetDrawers();target.resetDrawers();
    }
    private void resetDrawers() { for(Drawer d:drawers) { d.open=false;d.from=0;d.start=Long.MIN_VALUE; } }
    public CompoundTag save(HolderLookup.Provider lookup,boolean visual) {
        var tag=new CompoundTag();tag.putBoolean("Installed",installed);tag.putInt("Rows",rows);
        if(visual)for(int i=0;i<2;i++) {
            var d=new CompoundTag();d.putBoolean("Open",drawers[i].open);d.putLong("Start",drawers[i].start);d.putFloat("From",drawers[i].from);tag.put("Drawer"+i,d);
        }
        else if(installed&&store!=null) {
            var items=NonNullList.withSize(store.getContainerSize(),ItemStack.EMPTY);
            for(int i=0;i<items.size();i++)items.set(i,store.getItem(i));
            ContainerHelper.saveAllItems(tag,items,lookup);
        }
        return tag;
    }
    void load(CompoundTag tag,HolderLookup.Provider lookup) {
        installed=tag.getBoolean("Installed");rows=tag.getInt("Rows")==6?6:3;store=null;resetDrawers();
        if(!installed) { rows=0;return; }
        boolean client=hold.owner().cargoLevel()!=null&&hold.owner().cargoLevel().isClientSide;
        if(!client) {
            store=new Store(this,rows*9);var items=NonNullList.withSize(rows*9,ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag,items,lookup);
            for(int i=0;i<items.size();i++)store.setItem(i,items.get(i));
        }
        // Opening is transient server state; reload starts closed and carries no stale viewers.
        if(client||hold.owner().cargoLevel()==null)for(int i=0;i<2;i++)if(tag.contains("Drawer"+i)) {
            var d=tag.getCompound("Drawer"+i);drawers[i].open=d.getBoolean("Open");drawers[i].start=d.getLong("Start");drawers[i].from=Math.clamp(d.getFloat("From"),0,1);
        }
    }
    private void sound(Vec3 local,net.minecraft.sounds.SoundEvent event) {
        var level=hold.owner().cargoLevel();if(level==null||level.isClientSide)return;
        var p=hold.owner().cargoPose().point(local);level.playSound(null,p.x,p.y,p.z,event,SoundSource.BLOCKS,.65F,1);
    }
}
