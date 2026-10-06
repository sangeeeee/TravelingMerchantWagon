package com.sange.tm_wagon.cargo;

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
    private com.sange.tm_wagon.material.WagonMaterial material=com.sange.tm_wagon.material.WagonMaterial.DEFAULT;
    public com.sange.tm_wagon.material.WagonMaterial material() { return material; }
    private final CargoHold hold;
    private boolean installed;
    private int openRows;
    public CargoCover(CargoHold hold) { this.hold=hold; }
    public boolean installed() { return installed; }
    public int openRows() { return openRows; }
    public int rows() { return hold.rows(); }
    public static double front(WagonPart body) { return FRONT+body.frontOffset(); }
    public static double halfWidth(WagonPart body) { return HALF_WIDTH*body.widthScale(); }
    public boolean covered(int slot) { return installed&&slot>=0&&slot<hold.capacity()&&slot/hold.columns()>=openRows; }
    public double back(WagonPart body) { return 2.34375+body.rearExtension(); }
    public double boundary(int row,WagonPart body) {
        return row<=0?front(body):row>=body.rows()?back(body):body.firstRowZ()-.35+row*.7;
    }
    public double radius() { return (.055+.047*Math.sqrt(openRows))/Math.sqrt(2); }
    public double rollZ(WagonPart body) {
        return openRows>=body.rows()?back(body)-radius():boundary(openRows,body);
    }
    /** Only the flat cloth supports weight; rolled fabric and hanging edges are decorative. */
    public List<AABB> boxes(WagonPart body) {
        if(!installed||openRows>=body.rows())return List.of();
        return List.of(new AABB(-halfWidth(body),Y,boundary(openRows,body),halfWidth(body),TOP,back(body)));
    }
    public AABB rollBox(WagonPart body) {
        double r=radius(),z=rollZ(body);
        return new AABB(-halfWidth(body),TOP,z-r,halfWidth(body),TOP+2*r,z+r);
    }
    /** Outline/click volumes deliberately do not participate in movement collisions. */
    public List<AABB> selectionBoxes(WagonPart body) {
        if(!installed)return List.of();
        var result=new ArrayList<AABB>(boxes(body));
        if(openRows<body.rows()) {
            double front=boundary(openRows,body),back=back(body);
            result.add(new AABB(-halfWidth(body),Y-.125,front,-halfWidth(body)+.016,TOP,back));
            result.add(new AABB(halfWidth(body)-.016,Y-.125,front,halfWidth(body),TOP,back));
            result.add(new AABB(-halfWidth(body),Y-.125,back-.016,halfWidth(body),TOP,back));
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
    public boolean hit(Vec3 local) {
        // Block outlines round outward by up to 1/32 on every axis, including roll edges.
        return selectionBoxes(hold.owner().cargoBody()).stream().anyMatch(box->box.inflate(1.0/32+.001).contains(local));
    }
    /** A handful of local-space intersections, only during interaction/menu validation; no world scan. */
    public boolean obstructs(Vec3 eye,Vec3 target) {
        if(!installed&&!hold.canopy().installed())return false;
        for(AABB box:hold.canopy().selectionBoxes(hold.owner().cargoBody()))if(occludes(box,eye,target))return true;
        for(AABB box:selectionBoxes(hold.owner().cargoBody()))if(occludes(box,eye,target))return true;
        // The lowered tailgate must expose cargo; side walls still prevent interaction through wood.
        var hull=WagonGeometry.partBoxes(hold.owner().cargoBody());
        for(int i=0;i<4;i++)if(occludes(hull.get(i),eye,target))return true;
        return occludes(hold.tailBox(),eye,target);
    }
    private static boolean occludes(AABB box,Vec3 eye,Vec3 target) {
        if(box.contains(eye))return true;
        var hit=box.clip(eye,target);
        // Touching the floor/target at the ray endpoint is not an intervening obstacle.
        return hit.isPresent()&&eye.distanceToSqr(hit.get())<eye.distanceToSqr(target)-1e-8;
    }
    boolean side(Vec3 local) {
        if(local.y<CargoHold.FLOOR+.01||local.y>2.3125-.01)return false;
        var body=WagonGeometry.partBoxes(hold.owner().cargoBody());
        for(int i=1;i<=4;i++)if((i==4?hold.tailBox():body.get(i)).inflate(.018).contains(local))return true;
        return false;
    }
    String permission(Player player,Vec3 local) {
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
        if(hold.canopy().installed())return "message.tm_wagon.roof_conflict";
        if(installed)return "message.tm_wagon.cover_installed";
        if(!(stack.getItem() instanceof WagonCoverItem)||stack.isEmpty()||!side(local))return "message.tm_wagon.cover_side";
        installed=true;openRows=0;
        if(!clearAdded(List.of())) { installed=false;return "message.tm_wagon.cover_blocked"; }
        error=hold.owner().cargoGeometryChanged();
        if(error!=null) { installed=false;return error; }
        material=new com.sange.tm_wagon.material.WagonMaterial(com.sange.tm_wagon.material.WoodMaterial.OAK,com.sange.tm_wagon.material.WagonMaterial.of(stack).colour());
        if(!player.getAbilities().instabuild)stack.shrink(1);
        conceal();hold.changed(true);sound(true);return null;
    }
    public String remove(Player player,Vec3 local) {
        String error=permission(player,local);if(error!=null)return error;
        if(!installed||!side(local))return "message.tm_wagon.cover_side";
        int previous=openRows;installed=false;openRows=0;
        error=hold.owner().cargoGeometryChanged();
        if(error!=null) { installed=true;openRows=previous;return error; }
        // Clear ownership before giving back the single reusable cover.
        player.getInventory().placeItemBackInInventory(material.stack(WagonContent.CARGO_COVER.get()));
        hold.changed(true);sound(false);return null;
    }
    public String step(Player player,Vec3 local,int direction) {
        String error=permission(player,local);if(error!=null)return error;
        if(!installed||!hit(local))return "message.tm_wagon.cover_side";
        int target=net.minecraft.util.Mth.clamp(openRows+(direction<0?-1:1),0,rows());
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
        for(AABB box:added.toAabbs())if(!hold.freeLocalVolume(box))return false;
        return true;
    }
    private void conceal() {
        CargoMenus.closeObstructed(hold);
    }
    private void sound(boolean spreading) {
        var point=hold.owner().cargoPose().point(new Vec3(0,TOP,rollZ(hold.owner().cargoBody())));
        hold.owner().cargoLevel().playSound(null,point.x,point.y,point.z,
            spreading?SoundType.WOOL.getPlaceSound():SoundType.WOOL.getBreakSound(),SoundSource.BLOCKS,.75F,1);
    }
    public void destroy(boolean drops) {
        if(!installed)return;installed=false;openRows=0;
        if(drops)hold.drop(hold.owner().cargoPose().point(new Vec3(0,TOP,0)),material.stack(WagonContent.CARGO_COVER.get()));
    }
    void transferTo(CargoCover target) {
        target.material=material;target.installed=installed;target.openRows=net.minecraft.util.Mth.clamp(openRows,0,target.rows());installed=false;openRows=0;
    }
    public CompoundTag save() {
        var tag=new CompoundTag();tag.put("Material",material.save());tag.putBoolean("Installed",installed);tag.putInt("OpenRows",openRows);return tag;
    }
    public void load(CompoundTag tag) {
        material=com.sange.tm_wagon.material.WagonMaterial.load(tag.getCompound("Material"));
        installed=tag.getBoolean("Installed");openRows=installed?net.minecraft.util.Mth.clamp(tag.getInt("OpenRows"),0,rows()):0;
    }
}
