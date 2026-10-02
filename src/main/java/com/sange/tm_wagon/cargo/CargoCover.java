package com.sange.tm_wagon.cargo;

import com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity;
import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.assembly.WagonGeometry;
import com.sange.tm_wagon.assembly.WagonPart;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One optional accessory and a bounded row counter; no cargo slots or extra entities. */
public final class CargoCover {
    public static final double HALF_WIDTH=1.1875,Y=2.359375,TOP=2.375,FRONT=-1.34375;
    private final CargoHold hold;
    private boolean installed;
    private int openRows;
    public CargoCover(CargoHold hold) { this.hold=hold; }
    public boolean installed() { return installed; }
    public int openRows() { return openRows; }
    public int rows() { return hold.capacity()/2; }
    public boolean covered(int slot) { return installed&&slot>=0&&slot<hold.capacity()&&slot/2>=openRows; }
    public double back(WagonPart body) { return 2.34375+body.rearExtension(); }
    public double boundary(int row,WagonPart body) {
        return row<=0?FRONT:row>=body.cargoCapacity()/2?back(body):-1.31+row*.7;
    }
    public double radius() { return (.055+.047*Math.sqrt(openRows))/Math.sqrt(2); }
    public double rollZ(WagonPart body) {
        return openRows>=body.cargoCapacity()/2?back(body)-radius():boundary(openRows,body);
    }
    /** Only the flat cloth supports weight; rolled fabric and hanging edges are decorative. */
    public List<AABB> boxes(WagonPart body) {
        if(!installed||openRows>=body.cargoCapacity()/2)return List.of();
        return List.of(new AABB(-HALF_WIDTH,Y,boundary(openRows,body),HALF_WIDTH,TOP,back(body)));
    }
    public AABB rollBox(WagonPart body) {
        double r=radius(),z=rollZ(body);
        return new AABB(-HALF_WIDTH,TOP,z-r,HALF_WIDTH,TOP+2*r,z+r);
    }
    /** Outline/click volumes deliberately do not participate in movement collisions. */
    public List<AABB> selectionBoxes(WagonPart body) {
        if(!installed)return List.of();
        var result=new ArrayList<AABB>(boxes(body));
        if(openRows<body.cargoCapacity()/2) {
            double front=boundary(openRows,body),back=back(body);
            result.add(new AABB(-HALF_WIDTH,Y-.125,front,-HALF_WIDTH+.016,TOP,back));
            result.add(new AABB(HALF_WIDTH-.016,Y-.125,front,HALF_WIDTH,TOP,back));
            result.add(new AABB(-HALF_WIDTH,Y-.125,back-.016,HALF_WIDTH,TOP,back));
        }
        if(openRows>0)result.add(rollBox(body));
        return result;
    }
    private record SelectionKey(WagonPart body,net.minecraft.core.Direction facing,boolean installed,int rows) {}
    private SelectionKey selectionKey;
    private java.util.Map<BlockPos,List<AABB>> selectionCells=java.util.Map.of();
    public java.util.Map<BlockPos,List<AABB>> selectionCells(WagonPart body,net.minecraft.core.Direction facing) {
        var key=new SelectionKey(body,facing,installed,openRows);
        if(!key.equals(selectionKey)) {
            selectionCells=WagonGeometry.customCells(selectionBoxes(body),facing);selectionKey=key;
        }
        return selectionCells;
    }
    public boolean hit(Vec3 local) { return selectionBoxes(hold.owner().cargoBody()).stream().anyMatch(box->box.inflate(.018,.025,.018).contains(local)); }
    private boolean side(Vec3 local) {
        if(local.y<CargoHold.FLOOR+.01||local.y>2.3125-.01)return false;
        var body=WagonGeometry.partBoxes(hold.owner().cargoBody());
        for(int i=1;i<=4;i++)if(body.get(i).inflate(.018).contains(local))return true;
        return false;
    }
    private String permission(Player player,Vec3 local) {
        var owner=hold.owner();Vec3 point=owner.cargoPose().point(local);
        if(!owner.cargoLive()||owner.cargoBusy())return "message.tm_wagon.assembly_busy";
        if(player==null||player.level()!=owner.cargoLevel()||!player.isAlive()||player.isSpectator()
            ||player.distanceToSqr(point)>64||!owner.cargoLevel().mayInteract(player,BlockPos.containing(point)))return "message.tm_wagon.protected";
        return null;
    }
    /** Visible cloth, including hanging edges, always adjusts rows; exposed wooden walls dismantle. */
    public InteractionResult interact(Player player,InteractionHand hand,Vec3 local) {
        boolean wall=side(local),surface=installed&&hit(local);
        var stack=player.getItemInHand(hand);
        boolean removing=installed&&wall&&!surface&&player.isSecondaryUseActive();
        boolean installing=wall&&!installed&&stack.getItem() instanceof WagonCoverItem;
        if(!removing&&!installing&&!surface)return InteractionResult.PASS;
        if(hold.owner().cargoLevel().isClientSide)return InteractionResult.SUCCESS;
        String error=removing?remove(player,local):installing?install(stack,player,local):step(player,local,player.isSecondaryUseActive()?-1:1);
        CargoHold.message(player,error);return InteractionResult.CONSUME;
    }
    public String install(ItemStack stack,Player player,Vec3 local) {
        String error=permission(player,local);if(error!=null)return error;
        if(!(hold.owner() instanceof AssemblyFrameBlockEntity))return "message.tm_wagon.cover_block_only";
        if(installed)return "message.tm_wagon.cover_installed";
        if(!(stack.getItem() instanceof WagonCoverItem)||stack.isEmpty()||!side(local))return "message.tm_wagon.cover_side";
        installed=true;openRows=0;
        if(!clearAdded(List.of())) { installed=false;return "message.tm_wagon.cover_blocked"; }
        error=hold.owner().cargoGeometryChanged();
        if(error!=null) { installed=false;return error; }
        if(!player.getAbilities().instabuild)stack.shrink(1);
        conceal();hold.changed(true);sound(true);return null;
    }
    public String remove(Player player,Vec3 local) {
        String error=permission(player,local);if(error!=null)return error;
        if(!(hold.owner() instanceof AssemblyFrameBlockEntity))return "message.tm_wagon.cover_block_only";
        if(!installed||!side(local))return "message.tm_wagon.cover_side";
        int previous=openRows;installed=false;openRows=0;
        error=hold.owner().cargoGeometryChanged();
        if(error!=null) { installed=true;openRows=previous;return error; }
        // Clear ownership before giving back the single reusable cover.
        player.getInventory().placeItemBackInInventory(new ItemStack(WagonContent.CARGO_COVER.get()));
        hold.changed(true);sound(false);return null;
    }
    public String step(Player player,Vec3 local,int direction) {
        String error=permission(player,local);if(error!=null)return error;
        if(!installed||!hit(local))return "message.tm_wagon.cover_side";
        int target=Math.clamp(openRows+(direction<0?-1:1),0,rows());
        if(target==openRows)return null;
        var before=boxes(hold.owner().cargoBody());int previous=openRows;openRows=target;
        if(!clearAdded(before)) { openRows=previous;return "message.tm_wagon.cover_blocked"; }
        error=hold.owner().cargoGeometryChanged();
        if(error!=null) { openRows=previous;return error; }
        conceal();hold.changed(true);sound(direction<0);return null;
    }
    private boolean clearAdded(List<AABB> before) {
        var added=net.minecraft.world.phys.shapes.Shapes.join(WagonGeometry.shape(boxes(hold.owner().cargoBody())),
            WagonGeometry.shape(before),net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
        for(AABB box:added.toAabbs())if(!hold.freeVolume(CargoHold.worldBox(box,hold.owner().cargoPose())))return false;
        return true;
    }
    private void conceal() {
        for(int slot=0;slot<hold.capacity();slot++)if(hold.anchorSlot(slot)==slot&&covered(slot)) {
            var entry=hold.entry(slot);if(entry==null)continue;
            CargoMenus.close(hold,entry);StrawMatSleep.wake(hold,entry);
        }
    }
    private void sound(boolean spreading) {
        var point=hold.owner().cargoPose().point(new Vec3(0,TOP,rollZ(hold.owner().cargoBody())));
        hold.owner().cargoLevel().playSound(null,point.x,point.y,point.z,
            spreading?SoundType.WOOL.getPlaceSound():SoundType.WOOL.getBreakSound(),SoundSource.BLOCKS,.75F,1);
    }
    public void destroy(boolean drops) {
        if(!installed)return;installed=false;openRows=0;
        if(drops)hold.drop(hold.owner().cargoPose().point(new Vec3(0,TOP,0)),new ItemStack(WagonContent.CARGO_COVER.get()));
    }
    void transferTo(CargoCover target) {
        target.installed=installed;target.openRows=Math.clamp(openRows,0,target.rows());installed=false;openRows=0;
    }
    public CompoundTag save() {
        var tag=new CompoundTag();tag.putBoolean("Installed",installed);tag.putInt("OpenRows",openRows);return tag;
    }
    public void load(CompoundTag tag) {
        installed=tag.getBoolean("Installed");openRows=installed?Math.clamp(tag.getInt("OpenRows"),0,rows()):0;
    }
}
