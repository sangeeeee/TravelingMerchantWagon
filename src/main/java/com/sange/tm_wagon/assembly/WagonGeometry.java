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
    private record Key(WagonPart part, WagonSlot slot, Direction facing) {}
    private static final Map<WagonPart, List<AABB>> BOXES = load();
    private static final Map<Key, Map<BlockPos, List<AABB>>> CACHE = new HashMap<>();

    private static Map<WagonPart, List<AABB>> load() {
        var result = new java.util.EnumMap<WagonPart, List<AABB>>(WagonPart.class);
        try (var stream = WagonGeometry.class.getResourceAsStream("/data/tm_wagon/wagon_geometry.json")) {
            if (stream == null) throw new IllegalStateException("Missing wagon collision data");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (WagonPart part : WagonPart.values()) {
                List<AABB> boxes = new ArrayList<>();
                for (var element : json.getAsJsonArray(part.id)) {
                    var b = element.getAsJsonArray();
                    boxes.add(new AABB(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(),
                        b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble()));
                }
                result.put(part, List.copyOf(boxes));
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load wagon geometry", e); }
        return result;
    }

    /** Cell-local boxes; each box stays within [0,1] on every axis. */
    public static synchronized Map<BlockPos, List<AABB>> cells(WagonPart part, WagonSlot slot, Direction facing) {
        return CACHE.computeIfAbsent(new Key(part, slot, facing), key -> {
            Map<BlockPos, List<AABB>> cells = new LinkedHashMap<>();
            Vec3 offset = slot.geometryOffset();
            for (AABB box : BOXES.get(part)) {
                if (slot == WagonSlot.FRONT_RIGHT || slot == WagonSlot.REAR_RIGHT) box = new AABB(-box.maxX,box.minY,box.minZ,-box.minX,box.maxY,box.maxZ);
                Vec3 a = WagonSlot.rotate(new Vec3(box.minX, box.minY, box.minZ).add(offset), facing);
                Vec3 b = WagonSlot.rotate(new Vec3(box.maxX, box.maxY, box.maxZ).add(offset), facing);
                AABB world = new AABB(a, b).move(.5, 0, .5);
                for (int x = (int) Math.floor(world.minX); x < Math.ceil(world.maxX - 1e-7); x++)
                    for (int y = (int) Math.floor(world.minY); y < Math.ceil(world.maxY - 1e-7); y++)
                        for (int z = (int) Math.floor(world.minZ); z < Math.ceil(world.maxZ - 1e-7); z++) {
                            AABB clipped = world.intersect(new AABB(x, y, z, x+1, y+1, z+1));
                            if (clipped.getXsize() < 1e-7 || clipped.getYsize() < 1e-7 || clipped.getZsize() < 1e-7) continue;
                            // Half a model unit is enough for collision, and a
                            // power-of-two grid prevents huge irregular voxel
                            // mergers from the wheel's rotated cuboids.
                            AABB local = clipped.move(-x,-y,-z);
                            AABB snapped = new AABB(Math.floor(local.minX*32)/32,Math.floor(local.minY*32)/32,Math.floor(local.minZ*32)/32,
                                Math.ceil(local.maxX*32)/32,Math.ceil(local.maxY*32)/32,Math.ceil(local.maxZ*32)/32);
                            cells.computeIfAbsent(new BlockPos(x, y, z), ignored -> new ArrayList<>()).add(snapped);
                        }
            }
            cells.computeIfAbsent(slot.position(BlockPos.ZERO, facing), ignored -> new ArrayList<>());
            cells.replaceAll((pos, boxes) -> List.copyOf(boxes));
            return Map.copyOf(cells);
        });
    }

    public static VoxelShape shape(List<AABB> boxes) {
        VoxelShape shape = Shapes.empty();
        for (AABB box : boxes) shape = Shapes.joinUnoptimized(shape, Shapes.create(box), net.minecraft.world.phys.shapes.BooleanOp.OR);
        return shape.optimize();
    }
    private WagonGeometry() {}
}
