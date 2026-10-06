package com.sange.tm_wagon.client;

import com.sange.tm_wagon.assembly.WagonContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.GameType;

/** Verify the real Fabric creative inventory, including tab pagination and search. */
public final class CreativeInventoryClientSmoke {
    private static boolean opened;
    private static int ticks,loading;

    public static void tick() throws ReflectiveOperationException {
        var mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false;
        mc.mouseHandler.releaseMouse();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen&&mc.getOverlay()==null){
            mc.setScreen(new TitleScreen());return;
        }
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
            opened=true;mc.createWorldOpenFlows().openWorld(System.getProperty("tm_wagon.fabricSmokeWorld","repro"),()->{throw new IllegalStateException("Missing creative test world");});return;
        }
        if(mc.player==null||mc.level==null){
            if(++loading>1600)throw new IllegalStateException("Creative test world loading timeout");
            return;
        }
        if(ticks==0&&mc.screen!=null)return;
        ticks++;
        if(ticks==1)mc.getSingleplayerServer().execute(()->{
            mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().setGameMode(GameType.CREATIVE);
        });
        if(ticks==20){
            if(!mc.gameMode.hasInfiniteItems())throw new IllegalStateException("Creative game mode not synced");
            mc.setScreen(new CreativeModeInventoryScreen(mc.player,mc.level.enabledFeatures(),false));
        }
        if(ticks==28)Screenshot.grab(mc.gameDirectory,"fabric-creative-first-page.png",mc.getMainRenderTarget(),message->{});
        if(ticks==30){
            var tab=WagonContent.TAB.get();
            if(!tab.shouldDisplay()||tab.getDisplayItems().stream().noneMatch(s->s.is(WagonContent.FRAME_ITEM.get())))
                throw new IllegalStateException("Missing wagon creative tab contents");
            if(CreativeModeTabs.searchTab().getSearchTabDisplayItems().stream().noneMatch(s->s.is(WagonContent.FRAME_ITEM.get())))
                throw new IllegalStateException("Wagon items absent from creative search");
            var screen=(CreativeModeInventoryScreen)mc.screen;
            var currentPage=screen.getClass().getMethod("getCurrentPage");
            if((int)currentPage.invoke(screen)!=0)throw new IllegalStateException("Unexpected initial creative page");
            screen.getClass().getMethod("switchToNextPage").invoke(screen);
            if((int)currentPage.invoke(screen)!=1)throw new IllegalStateException("Fabric creative page switch failed");
            var select=CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab",CreativeModeTab.class);
            select.setAccessible(true);select.invoke(screen,tab);
            if(screen.getMenu().getSlot(0).getItem().isEmpty())throw new IllegalStateException("Wagon creative grid is empty");
        }
        if(ticks==40){
            Screenshot.grab(mc.gameDirectory,"fabric-wagon-creative-tab.png",mc.getMainRenderTarget(),message->{});
            com.mojang.logging.LogUtils.getLogger().info("CREATIVE_INVENTORY_PASS: wagon tab visible on page 2, {} variants and items available in search",WagonContent.TAB.get().getDisplayItems().size());
        }
        if(ticks==50)mc.stop();
    }
    private CreativeInventoryClientSmoke(){}
}
