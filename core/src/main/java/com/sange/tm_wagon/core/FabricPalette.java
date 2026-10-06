package com.sange.tm_wagon.core;

/** Pixel fabric tinting independent of Minecraft dye registries and rendering APIs. */
public final class FabricPalette {
    private static final float COLOUR_STRENGTH = .55F;

    public static int muted(int colour) {
        int red = (colour >> 16) & 255;
        int green = (colour >> 8) & 255;
        int blue = colour & 255;
        float grey = .2126F * red + .7152F * green + .0722F * blue;
        red = Math.round(grey + (red - grey) * COLOUR_STRENGTH);
        green = Math.round(grey + (green - grey) * COLOUR_STRENGTH);
        blue = Math.round(grey + (blue - grey) * COLOUR_STRENGTH);
        return (colour & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }

    private FabricPalette() {}
}
