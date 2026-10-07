package com.sange.tm_wagon.handbook;

import com.sange.tm_wagon.ServerConfig;
import com.sange.tm_wagon.assembly.WagonContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Check every login for an actual prior gift, including players in pre-existing worlds. */
@EventBusSubscriber(modid="tm_wagon")
public final class HandbookGifts {
    static final String RECEIVED="tm_wagon:handbook_received";
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)giveOnFirstJoin(player);
    }
    public static void giveOnFirstJoin(ServerPlayer player) {
        if(WagonContent.MANUAL.isEmpty()||!ServerConfig.GIVE_MANUAL_ON_FIRST_JOIN.get())return;
        var data=player.getPersistentData();
        var persisted=data.getCompound(Player.PERSISTED_NBT_TAG).orElseGet(net.minecraft.nbt.CompoundTag::new);
        if(persisted.getBoolean(RECEIVED).orElse(false))return;
        // Commit immediately before inventory insertion/drop on the same server thread.
        // Missing Patchouli or a disabled gift never consumes a player's entitlement.
        persisted.putBoolean(RECEIVED,true);
        data.put(Player.PERSISTED_NBT_TAG,persisted);
        var book=new ItemStack(WagonContent.MANUAL.orElseThrow().get());
        player.getInventory().placeItemBackInInventory(book,false);
    }
    private HandbookGifts() {}
}
