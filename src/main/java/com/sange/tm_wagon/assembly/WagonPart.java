package com.sange.tm_wagon.assembly;

public enum WagonPart {
    CARGO_BODY("cargo_body"), SINGLE_HORSE_SHAFTS("single_horse_shafts"),
    DOUBLE_HORSE_SHAFTS("double_horse_shafts"), SINGLE_SEAT("single_seat"),
    DOUBLE_SEAT("double_seat"), SMALL_WHEEL("small_wheel"), LARGE_WHEEL("large_wheel");
    public final String id;
    WagonPart(String id) { this.id = id; }
}
