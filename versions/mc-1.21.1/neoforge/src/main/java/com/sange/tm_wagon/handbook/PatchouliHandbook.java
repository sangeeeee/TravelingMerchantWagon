package com.sange.tm_wagon.handbook;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import vazkii.patchouli.api.PatchouliAPI;

/** Only loaded after confirming Patchouli is installed; its API is never bundled. */
final class PatchouliHandbook {
    static void open(ServerPlayer player) {
        PatchouliAPI.get().openBookGUI(player,ResourceLocation.fromNamespaceAndPath("tm_wagon","coachmans_manual"));
    }
    private PatchouliHandbook() {}
}
