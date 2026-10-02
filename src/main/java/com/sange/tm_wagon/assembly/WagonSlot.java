package com.sange.tm_wagon.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Positions are relative to the assembly frame, with NORTH as front. */
public enum WagonSlot {
    BODY(0, 1, 0), SHAFTS(0, 1, -2), SEAT(0, 2, -2),
    FRONT_LEFT(-1, 0, -1), FRONT_RIGHT(1, 0, -1),
    REAR_LEFT(-1, 0, 1), REAR_RIGHT(1, 0, 1);
    private final BlockPos anchor;
    WagonSlot(int x, int y, int z) { anchor = new BlockPos(x, y, z); }

    public boolean accepts(WagonPart part) {
        return switch (this) {
            case BODY -> part.isCargoBody();
            case SHAFTS -> part == WagonPart.SINGLE_HORSE_SHAFTS || part == WagonPart.DOUBLE_HORSE_SHAFTS;
            case SEAT -> part == WagonPart.SINGLE_SEAT || part == WagonPart.DOUBLE_SEAT;
            case FRONT_LEFT, FRONT_RIGHT -> part == WagonPart.SMALL_WHEEL;
            case REAR_LEFT, REAR_RIGHT -> part == WagonPart.LARGE_WHEEL;
        };
    }
    public BlockPos position(BlockPos origin, Direction facing) { return position(origin,facing,WagonPart.CARGO_BODY); }
    public BlockPos position(BlockPos origin,Direction facing,WagonPart body) {
        int rear=(this==REAR_LEFT||this==REAR_RIGHT)&&body.rearExtension()>0?1:0;
        Vec3 p = rotate(new Vec3(anchor.getX(), anchor.getY(), anchor.getZ()+rear), facing);
        return origin.offset((int) p.x, (int) p.y, (int) p.z);
    }
    public Vec3 geometryOffset() { return geometryOffset(WagonPart.CARGO_BODY); }
    public Vec3 geometryOffset(WagonPart body) {
        double rear=20.0/16+body.rearExtension();
        return switch (this) {
            case FRONT_LEFT -> new Vec3(-21.0 / 16, 0, -20.0 / 16);
            case FRONT_RIGHT -> new Vec3(21.0 / 16, 0, -20.0 / 16);
            case REAR_LEFT -> new Vec3(-21.0 / 16, 0, rear);
            case REAR_RIGHT -> new Vec3(21.0 / 16, 0, rear);
            default -> Vec3.ZERO;
        };
    }
    public static Vec3 rotate(Vec3 p, Direction facing) {
        return switch (facing) {
            case EAST -> new Vec3(-p.z, p.y, p.x);
            case SOUTH -> new Vec3(-p.x, p.y, -p.z);
            case WEST -> new Vec3(p.z, p.y, -p.x);
            default -> p;
        };
    }
}
