package com.sange.tm_wagon.assembly;

public enum WagonPart {
    CARGO_BODY("cargo_body"), LONG_CARGO_BODY("long_cargo_body"), SINGLE_HORSE_SHAFTS("single_horse_shafts"),
    DOUBLE_HORSE_SHAFTS("double_horse_shafts"), SINGLE_SEAT("single_seat"),
    DOUBLE_SEAT("double_seat"), SMALL_WHEEL("small_wheel"), LARGE_WHEEL("large_wheel"),
    SINGLE_WOODEN_SEAT("single_wooden_seat"), DOUBLE_WOODEN_SEAT("double_wooden_seat"),
    WIDE_CARGO_BODY("wide_cargo_body"), TRIPLE_SEAT("triple_seat"), TRIPLE_WOODEN_SEAT("triple_wooden_seat");
    public final String id;
    WagonPart(String id) { this.id = id; }
    public boolean isCargoBody() { return this==CARGO_BODY||this==LONG_CARGO_BODY||this==WIDE_CARGO_BODY; }
    public boolean isWoodenSeat() { return this==SINGLE_WOODEN_SEAT||this==DOUBLE_WOODEN_SEAT||this==TRIPLE_WOODEN_SEAT; }
    public int seatCapacity() { return switch(this) {
        case SINGLE_SEAT,SINGLE_WOODEN_SEAT->1;
        case DOUBLE_SEAT,DOUBLE_WOODEN_SEAT->2;
        case TRIPLE_SEAT,TRIPLE_WOODEN_SEAT->3;
        default->0;
    }; }
    public int columns() { return this==WIDE_CARGO_BODY?4:2; }
    public int rows() { return this==WIDE_CARGO_BODY?8:this==LONG_CARGO_BODY?6:5; }
    public int cargoCapacity() { return columns()*rows(); }
    public double columnSpacing() { return this==WIDE_CARGO_BODY?.86:1; }
    public double firstRowZ() { return this==WIDE_CARGO_BODY?-(rows()-1)*.35:-.96; }
    public double widthScale() { return this==WIDE_CARGO_BODY?1.75:1; }
    public double frontOffset() { return this==WIDE_CARGO_BODY?firstRowZ()+1.05:0; }
    public double rearExtension() { return this==WIDE_CARGO_BODY?-firstRowZ()-1.8:this==LONG_CARGO_BODY?.7:0; }
    public double frontWheelZ() { return -1.25+frontOffset(); }
    public double rearWheelZ() { return 1.25+rearExtension(); }
    public double wheelHalfTrack() { return 21.0/16*widthScale(); }
    public String modelPrefix() { return this==WIDE_CARGO_BODY?"wide_":this==LONG_CARGO_BODY?"long_":""; }
}
