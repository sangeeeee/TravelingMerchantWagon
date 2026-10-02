package com.sange.tm_wagon.assembly;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WagonGeometry {
    private record Key(WagonPart part, WagonSlot slot, Direction facing,WagonPart body) {}
    private static final Map<String, List<AABB>> BOXES = load();
    private static final int COLLISION_SIGNATURE = BOXES.hashCode();
    public static int collisionSignature() { return COLLISION_SIGNATURE; }
    private static final Map<Key, Map<BlockPos, List<AABB>>> CACHE = new HashMap<>();
    private record EntityKey(Map<WagonSlot,WagonPart> parts,Direction facing) {}
    private static final Map<EntityKey,VoxelShape> ENTITY_CACHE = new HashMap<>();
    public static synchronized VoxelShape entityShape(Map<WagonSlot,WagonPart> parts,Direction facing) {
        return ENTITY_CACHE.computeIfAbsent(new EntityKey(Map.copyOf(parts),facing),key -> {
            var boxes=new ArrayList<AABB>();
            parts.forEach((slot,part) -> cells(part,slot,facing,parts.getOrDefault(WagonSlot.BODY,WagonPart.CARGO_BODY)).forEach((cell,volumes) ->
                volumes.forEach(box -> boxes.add(box.move(cell.getX()-.5,cell.getY(),cell.getZ()-.5)))));
            return shape(boxes);
        });
    }

    private static Map<String, List<AABB>> load() {
        var result = new HashMap<String, List<AABB>>();
        try (var stream = WagonGeometry.class.getResourceAsStream("/data/tm_wagon/wagon_geometry.json")) {
            if (stream == null) throw new IllegalStateException("Missing wagon collision data");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String id : json.keySet()) {
                List<AABB> boxes = new ArrayList<>();
                for (var element : json.getAsJsonArray(id)) {
                    var b = element.getAsJsonArray();
                    boxes.add(new AABB(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(),
                        b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble()));
                }
                result.put(id, List.copyOf(boxes));
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load wagon geometry", e); }
        return result;
    }

    public static List<AABB> partBoxes(WagonPart part) { return BOXES.get(part.id); }
    public static Map<BlockPos,List<AABB>> customCells(List<AABB> boxes,Direction facing) { return clip(boxes,facing); }

    private record FrameKey(Direction facing, int height) {}
    private static final Map<FrameKey,Map<BlockPos,List<AABB>>> FRAME_CACHE = new HashMap<>();

    /** Cell-local boxes; each box stays within [0,1] on every axis. */
    public static Map<BlockPos,List<AABB>> cells(WagonPart part,WagonSlot slot,Direction facing) {
        return cells(part,slot,facing,WagonPart.CARGO_BODY);
    }
    public static synchronized Map<BlockPos,List<AABB>> cells(WagonPart part,WagonSlot slot,Direction facing,WagonPart body) {
        return CACHE.computeIfAbsent(new Key(part,slot,facing,body), key -> {
            List<AABB> boxes = new ArrayList<>();
            Vec3 offset = slot.geometryOffset(body);
            for (AABB box : BOXES.get(part.id)) {
                if (slot == WagonSlot.FRONT_RIGHT || slot == WagonSlot.REAR_RIGHT) box = new AABB(-box.maxX,box.minY,box.minZ,-box.minX,box.maxY,box.maxZ);
                boxes.add(box.move(offset));
            }
            Map<BlockPos,List<AABB>> result = clip(boxes,facing);
            result.computeIfAbsent(slot.position(BlockPos.ZERO,facing,body),ignored -> List.of());
            return Map.copyOf(result);
        });
    }
    public static Map<BlockPos,List<AABB>> frameCells(Direction facing) { return frameCells(facing,0); }
    public static synchronized Map<BlockPos,List<AABB>> frameCells(Direction facing, double progress) {
        int height = (int)Math.round(FrameMotion.collisionTop(progress)*32);
        return FRAME_CACHE.computeIfAbsent(new FrameKey(facing,height),ignored -> Map.copyOf(clip(frameBoxes(height/32.0,height/32.0),facing)));
    }
    public static Map<BlockPos,List<AABB>> frameSweep(Direction facing,double from,double to) {
        double a=FrameMotion.collisionTop(from),b=FrameMotion.collisionTop(to);
        return clip(frameBoxes(Math.min(a,b),Math.max(a,b)),facing);
    }
    private static List<AABB> frameBoxes(double lowTop,double highTop) {
        var footprint=BOXES.get("wagon_assembly_frame").get(1);
        return List.of(new AABB(-.5,0,-.5,.5,highTop-.125,.5),
            new AABB(footprint.minX,lowTop-.125,footprint.minZ,footprint.maxX,highTop,footprint.maxZ));
    }
    private static Map<BlockPos,List<AABB>> clip(List<AABB> boxes,Direction facing) {
        Map<BlockPos,List<AABB>> cells = new LinkedHashMap<>();
        for (AABB box : boxes) {
            Vec3 a = WagonSlot.rotate(new Vec3(box.minX,box.minY,box.minZ),facing);
            Vec3 b = WagonSlot.rotate(new Vec3(box.maxX,box.maxY,box.maxZ),facing);
            AABB world = new AABB(a,b).move(.5,0,.5);
            for (int x=(int)Math.floor(world.minX);x<Math.ceil(world.maxX-1e-7);x++)
                for (int y=(int)Math.floor(world.minY);y<Math.ceil(world.maxY-1e-7);y++)
                    for (int z=(int)Math.floor(world.minZ);z<Math.ceil(world.maxZ-1e-7);z++) {
                        AABB clipped=world.intersect(new AABB(x,y,z,x+1,y+1,z+1));
                        if (clipped.getXsize()<1e-7||clipped.getYsize()<1e-7||clipped.getZsize()<1e-7) continue;
                        AABB local=clipped.move(-x,-y,-z);
                        AABB snapped=new AABB(Math.floor(local.minX*32)/32,Math.floor(local.minY*32)/32,Math.floor(local.minZ*32)/32,
                            Math.ceil(local.maxX*32)/32,Math.ceil(local.maxY*32)/32,Math.ceil(local.maxZ*32)/32);
                        cells.computeIfAbsent(new BlockPos(x,y,z),ignored -> new ArrayList<>()).add(snapped);
                    }
        }
        cells.replaceAll((pos,parts) -> List.copyOf(parts));
        return cells;
    }

    public static VoxelShape shape(List<AABB> boxes) {
        VoxelShape shape = Shapes.empty();
        for (AABB box : boxes) shape = Shapes.joinUnoptimized(shape, Shapes.create(box), net.minecraft.world.phys.shapes.BooleanOp.OR);
        return shape.optimize();
    }
    private WagonGeometry() {}
}
