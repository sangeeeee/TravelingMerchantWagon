package com.sange.tm_wagon.client;

import net.minecraft.world.item.DyeColor;

/** Muted fabric colours shared by items, cushions, covers and curtains. */
public final class FabricColours {
    private static final float COLOUR_STRENGTH = .55F;
    private static final int[] TINTS = new int[16];

    static {
        for (var dye : DyeColor.values()) {
            int colour = dye.getTextureDiffuseColor();
            int red = (colour >> 16) & 255;
            int green = (colour >> 8) & 255;
            int blue = colour & 255;
            // Blend toward equal-luminance grey rather than whitening the fabric.
            float grey = .2126F * red + .7152F * green + .0722F * blue;
            red = Math.round(grey + (red - grey) * COLOUR_STRENGTH);
            green = Math.round(grey + (green - grey) * COLOUR_STRENGTH);
            blue = Math.round(grey + (blue - grey) * COLOUR_STRENGTH);
            TINTS[dye.getId()] = (colour & 0xFF000000) | (red << 16) | (green << 8) | blue;
        }
    }

    public static int tint(DyeColor dye) { return TINTS[dye.getId()]; }

    private FabricColours() {}
}
