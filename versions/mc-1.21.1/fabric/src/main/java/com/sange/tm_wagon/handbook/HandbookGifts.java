package com.sange.tm_wagon.handbook;

import com.sange.tm_wagon.ServerConfig;
import com.sange.tm_wagon.assembly.WagonContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Check every login for an actual prior gift, including players in pre-existing worlds. */
public final class HandbookGifts {
    static final String RECEIVED="tm_wagon:handbook_received";
    
    public static void giveOnFirstJoin(ServerPlayer player) {
        if(WagonContent.MANUAL.isEmpty()||!ServerConfig.GIVE_MANUAL_ON_FIRST_JOIN.get())return;
        var data=com.sange.tm_wagon.platform.EntityData.of(player);
        var persisted=data.getCompound("PlayerPersisted");
        if(persisted.getBoolean(RECEIVED))return;
        // Commit immediately before inventory insertion/drop on the same server thread.
        // Missing Patchouli or a disabled gift never consumes a player's entitlement.
        persisted.putBoolean(RECEIVED,true);
        data.put("PlayerPersisted",persisted);
        var book=new ItemStack(WagonContent.MANUAL.orElseThrow().get());
        player.getInventory().placeItemBackInInventory(book);
    }
    private HandbookGifts() {}
}
