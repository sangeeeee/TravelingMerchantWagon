package com.sange.tm_wagon.client;

import net.minecraft.world.item.DyeColor;
import com.sange.tm_wagon.core.FabricPalette;

/** Muted fabric colours shared by items, cushions, covers and curtains. */
public final class FabricColours {
    private static final int[] TINTS = new int[16];

    static {
        for (var dye : DyeColor.values()) {
            TINTS[dye.getId()] = FabricPalette.muted(dye.getTextureDiffuseColor());
        }
    }

    public static int tint(DyeColor dye) { return TINTS[dye.getId()]; }

    private FabricColours() {}
}
