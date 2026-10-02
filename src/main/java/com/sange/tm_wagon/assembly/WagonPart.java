package com.sange.tm_wagon.assembly;

public enum WagonPart {
    CARGO_BODY("cargo_body"), LONG_CARGO_BODY("long_cargo_body"), SINGLE_HORSE_SHAFTS("single_horse_shafts"),
    DOUBLE_HORSE_SHAFTS("double_horse_shafts"), SINGLE_SEAT("single_seat"),
    DOUBLE_SEAT("double_seat"), SMALL_WHEEL("small_wheel"), LARGE_WHEEL("large_wheel");
    public final String id;
    WagonPart(String id) { this.id = id; }
    public boolean isCargoBody() { return this==CARGO_BODY||this==LONG_CARGO_BODY; }
    public int cargoCapacity() { return this==LONG_CARGO_BODY?12:10; }
    public double rearExtension() { return this==LONG_CARGO_BODY?.7:0; }
    public String modelPrefix() { return this==LONG_CARGO_BODY?"long_":""; }
}
