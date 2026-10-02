package com.sange.tm_wagon.cargo;

import com.google.gson.JsonParser;
import com.sange.tm_wagon.assembly.AssemblyFrameBlockEntity;
import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.assembly.WagonGeometry;
import com.sange.tm_wagon.assembly.WagonPart;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One optional roof; cached thin silhouettes and two independent curtains, no ticking entities. */
public final class CargoCanopy {
    public static final double BASE=2.28125,FRONT=CargoCover.FRONT,THICK=1.0/64,REAR=2.203125,FRONT_CURTAIN_INSET=.0234375,REAR_CURTAIN_INSET=5.0/1024;
    public static final double TOP=3.96875+.5*Math.tan(Math.PI/8);
    private static final Map<String,List<AABB>> GEOMETRY=loadGeometry();
    private record Key(WagonPart body,boolean installed,boolean front,boolean rear) {}
    private static final Map<Key,List<AABB>> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<WagonPart,List<AABB>> RIM_CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Key,List<AABB>> OUTLINE_CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    private record SelectionKey(WagonPart body,net.minecraft.core.Direction facing,boolean installed) {}
    private SelectionKey selectionKey;
    private Map<net.minecraft.core.BlockPos,List<AABB>> selectionCells=Map.of();
    private final CargoHold hold;
    private boolean installed,frontClosed,rearClosed;
    public static int geometrySignature() { return 31*GEOMETRY.hashCode()+2; }
    public CargoCanopy(CargoHold hold) { this.hold=hold; }
    public boolean installed() { return installed; }
    public boolean closed(boolean front) { return front?frontClosed:rearClosed; }
    // The rear skirt and curtain fit between the last cargo row and the
    // closed gate's inner plank surface (2.20625 blocks for the standard body).
    public double back(WagonPart body) { return REAR+body.rearExtension(); }
    public double curtainZ(WagonPart body,boolean front) { return front?FRONT+FRONT_CURTAIN_INSET:back(body)-REAR_CURTAIN_INSET-THICK; }
    public List<AABB> curtainBoxes(WagonPart body,boolean front) {
        return GEOMETRY.get(closed(front)?"curtain_closed":"curtain_open").stream().map(b->b.move(0,0,curtainZ(body,front))).toList();
    }
    public List<AABB> boxes(WagonPart body) {
        return CACHE.computeIfAbsent(new Key(body,installed,frontClosed,rearClosed),key->{
            if(!key.installed)return List.of();
            double back=back(body);var result=new ArrayList<AABB>();
            for(AABB b:GEOMETRY.get("shell"))result.add(new AABB(b.minX,b.minY,FRONT,b.maxX,b.maxY,back));
            result.addAll(curtainBoxes(body,true));result.addAll(curtainBoxes(body,false));return List.copyOf(result);
        });
    }
    /** Decorative arch trim is visible/clickable, never a movement collider. */
    public List<AABB> rimBoxes(WagonPart body) {
        if(!installed)return List.of();
        return RIM_CACHE.computeIfAbsent(body,key->{
            var result=new ArrayList<AABB>();
            for(AABB b:GEOMETRY.get("end")) { result.add(b.move(0,0,FRONT+THICK/4));result.add(b.move(0,0,back(body)-THICK-THICK/4)); }
            return List.copyOf(result);
        });
    }
    public List<AABB> selectionBoxes(WagonPart body) {
        return OUTLINE_CACHE.computeIfAbsent(new Key(body,installed,frontClosed,rearClosed),key->{
            var result=new ArrayList<>(boxes(body));result.addAll(rimBoxes(body));return List.copyOf(result);
        });
    }
    public Map<net.minecraft.core.BlockPos,List<AABB>> selectionCells(WagonPart body,net.minecraft.core.Direction facing) {
        var key=new SelectionKey(body,facing,installed);
        if(!key.equals(selectionKey)) { selectionCells=WagonGeometry.customCells(rimBoxes(body),facing);selectionKey=key; }
        return selectionCells;
    }
    private Boolean curtainHit(Vec3 local) {
        if(!installed)return null;
        for(boolean front:new boolean[]{true,false}) {
            for(AABB b:curtainBoxes(hold.owner().cargoBody(),front))if(b.inflate(.035).contains(local))return front;
            if(!closed(front)) {
                // Folded curtains are hidden behind the end trim; that exact
                // projected patch of fabric remains a usable handle from outside.
                double end=front?FRONT+THICK/4:back(hold.owner().cargoBody())-THICK-THICK/4;
                for(AABB b:GEOMETRY.get("curtain_handles"))
                    if(b.move(0,0,end).inflate(.035).contains(local))return front;
            }
        }
        return null;
    }
    public boolean hit(Vec3 local) { return installed&&selectionBoxes(hold.owner().cargoBody()).stream().anyMatch(b->b.inflate(.035).contains(local)); }
    public InteractionResult interact(Player player,InteractionHand hand,Vec3 local) {
        boolean wall=hold.cover().side(local);var stack=player.getItemInHand(hand);Boolean curtain=curtainHit(local);
        boolean removing=installed&&wall&&curtain==null&&!hit(local)&&player.isSecondaryUseActive();
        boolean installing=!installed&&wall&&stack.getItem() instanceof WagonCanopyItem;
        if(!removing&&!installing&&curtain==null&&!hit(local))return InteractionResult.PASS;
        if(hold.owner().cargoLevel().isClientSide)return InteractionResult.SUCCESS;
        String error=removing?remove(player,local):installing?install(stack,player,local):curtain!=null?toggle(player,local,curtain):null;
        CargoHold.message(player,error);return InteractionResult.CONSUME;
    }
    public String install(ItemStack stack,Player player,Vec3 local) {
        String error=hold.cover().permission(player,local);if(error!=null)return error;
        if(!(hold.owner() instanceof AssemblyFrameBlockEntity))return "message.tm_wagon.canopy_block_only";
        if(installed||hold.cover().installed())return "message.tm_wagon.roof_conflict";
        if(!(stack.getItem() instanceof WagonCanopyItem)||stack.isEmpty()||!hold.cover().side(local))return "message.tm_wagon.canopy_side";
        installed=true;frontClosed=rearClosed=false;
        if(!clearAdded(List.of())) { installed=false;return "message.tm_wagon.canopy_blocked"; }
        error=hold.owner().cargoGeometryChanged();if(error!=null) { installed=false;return error; }
        if(!player.getAbilities().instabuild)stack.shrink(1);
        CargoMenus.closeObstructed(hold);hold.changed(true);sound(local,true);return null;
    }
    public String remove(Player player,Vec3 local) {
        String error=hold.cover().permission(player,local);if(error!=null)return error;
        if(!(hold.owner() instanceof AssemblyFrameBlockEntity))return "message.tm_wagon.canopy_block_only";
        if(!installed||!hold.cover().side(local))return "message.tm_wagon.canopy_side";
        installed=false;error=hold.owner().cargoGeometryChanged();if(error!=null) { installed=true;return error; }
        frontClosed=rearClosed=false;
        player.getInventory().placeItemBackInInventory(new ItemStack(WagonContent.CANOPY.get()));hold.changed(true);sound(local,false);return null;
    }
    public String toggle(Player player,Vec3 local,boolean front) {
        String error=hold.cover().permission(player,local);if(error!=null)return error;
        if(!installed||!Boolean.valueOf(front).equals(curtainHit(local)))return "message.tm_wagon.canopy_side";
        var before=boxes(hold.owner().cargoBody());boolean previous=closed(front);setClosed(front,!previous);
        if(!clearAdded(before)) { setClosed(front,previous);return "message.tm_wagon.canopy_blocked"; }
        error=hold.owner().cargoGeometryChanged();if(error!=null) { setClosed(front,previous);return error; }
        CargoMenus.closeObstructed(hold);hold.changed(true);sound(local,!previous);return null;
    }
    private void setClosed(boolean front,boolean closed) { if(front)frontClosed=closed;else rearClosed=closed; }
    private boolean clearAdded(List<AABB> before) {
        var added=net.minecraft.world.phys.shapes.Shapes.join(WagonGeometry.shape(boxes(hold.owner().cargoBody())),WagonGeometry.shape(before),net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);
        for(AABB b:added.toAabbs())if(!hold.freeVolume(CargoHold.worldBox(b,hold.owner().cargoPose())))return false;
        return true;
    }
    private void sound(Vec3 local,boolean closing) {
        var p=hold.owner().cargoPose().point(local);
        hold.owner().cargoLevel().playSound(null,p.x,p.y,p.z,closing?SoundType.WOOL.getPlaceSound():SoundType.WOOL.getBreakSound(),SoundSource.BLOCKS,.75F,1);
    }
    public void destroy(boolean drops) {
        if(!installed)return;installed=frontClosed=rearClosed=false;
        if(drops)hold.drop(hold.owner().cargoPose().point(new Vec3(0,BASE,0)),new ItemStack(WagonContent.CANOPY.get()));
    }
    void transferTo(CargoCanopy target) {
        target.installed=installed;target.frontClosed=frontClosed;target.rearClosed=rearClosed;installed=frontClosed=rearClosed=false;
    }
    public CompoundTag save() {
        var tag=new CompoundTag();tag.putBoolean("Installed",installed);tag.putBoolean("FrontClosed",frontClosed);tag.putBoolean("RearClosed",rearClosed);return tag;
    }
    public void load(CompoundTag tag) {
        // Corrupt/foreign data never creates two mutually exclusive accessories.
        installed=tag.getBoolean("Installed")&&!hold.cover().installed();frontClosed=installed&&tag.getBoolean("FrontClosed");rearClosed=installed&&tag.getBoolean("RearClosed");
    }
    private static Map<String,List<AABB>> loadGeometry() {
        try(var stream=CargoCanopy.class.getResourceAsStream("/data/tm_wagon/canopy_geometry.json")) {
            if(stream==null)throw new IllegalStateException("Missing canopy collision data");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();var result=new HashMap<String,List<AABB>>();
            for(String key:json.keySet()) {
                var boxes=new ArrayList<AABB>();for(var value:json.getAsJsonArray(key)) {
                    var b=value.getAsJsonArray();boxes.add(new AABB(b.get(0).getAsDouble(),b.get(1).getAsDouble(),b.get(2).getAsDouble(),b.get(3).getAsDouble(),b.get(4).getAsDouble(),b.get(5).getAsDouble()));
                }result.put(key,List.copyOf(boxes));
            }return Map.copyOf(result);
        }catch(java.io.IOException error) { throw new IllegalStateException("Cannot load canopy geometry",error); }
    }
}
