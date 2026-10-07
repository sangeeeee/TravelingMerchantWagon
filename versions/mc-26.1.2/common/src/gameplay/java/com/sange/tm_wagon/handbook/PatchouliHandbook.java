package com.sange.tm_wagon.handbook;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import vazkii.patchouli.api.PatchouliAPI;

/** Only loaded after confirming Patchouli is installed; its API is never bundled. */
final class PatchouliHandbook {
    static void open(ServerPlayer player) {
        HandbookRecipes.send(player);
        PatchouliAPI.get().openBookGUI(player,Identifier.fromNamespaceAndPath("tm_wagon","coachmans_manual"));
    }
    private PatchouliHandbook() {}
}
