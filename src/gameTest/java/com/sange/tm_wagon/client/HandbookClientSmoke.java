package com.sange.tm_wagon.client;

import com.sange.tm_wagon.assembly.WagonContent;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.neoforged.fml.ModList;
import java.util.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import vazkii.patchouli.api.PatchouliAPI;

/** Dedicated disposable world verifies server item use, book resource loading and the real GUI. */
@EventBusSubscriber(modid="tm_wagon",value=Dist.CLIENT)
public final class HandbookClientSmoke {
    private static boolean opened,used;
    private static int ticks,visible;
    private static volatile Throwable failure;
    private record Spread(ResourceLocation entry,int page) {}
    private static final List<Spread> spreads=new ArrayList<>();
    private static int spreadIndex=-1,spreadTicks,checkedPages,checkedRecipes,overflow;
    private static final ResourceLocation BOOK=ResourceLocation.fromNamespaceAndPath("tm_wagon","coachmans_manual");
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("tm_wagon.handbookSmokeTest"))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;mc.mouseHandler.releaseMouse();
        if(++ticks>1800)throw new IllegalStateException("Handbook smoke timed out: "+mc.screen);
        if(failure!=null)throw new IllegalStateException("Handbook smoke failed",failure);
        if(!opened&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            opened=true;mc.createWorldOpenFlows().openWorld("repro",()->{throw new IllegalStateException("Cannot open handbook fixture");});
        }
        if(mc.level==null||mc.player==null)return;
        if(!used&&mc.getSingleplayerServer()!=null&&mc.screen==null) {
            used=true;
            var book=ResourceLocation.fromNamespaceAndPath("tm_wagon","coachmans_manual");
            // getBookStack always makes Patchouli's generic item, even for custom-book definitions.
            // getSubtitle throws for an unregistered book and therefore verifies discovery through the API.
            PatchouliAPI.get().getSubtitle(book);
            mc.getSingleplayerServer().execute(()->{
                try {
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    var manual=WagonContent.MANUAL.orElseThrow().get();
                    if(p.getInventory().countItem(manual)<1)throw new IllegalStateException("Login handbook missing");
                    for(int i=0;i<9;i++)if(p.getInventory().getItem(i).is(manual)) {p.getInventory().selected=i;break;}
                    manual.use(p.level(),p,InteractionHand.MAIN_HAND);
                } catch(Throwable t) { failure=t; }
            });
        }
        if(ResourceLocation.fromNamespaceAndPath("tm_wagon","coachmans_manual").equals(PatchouliAPI.get().getOpenBookGui())) {
            if(++visible==20)net.minecraft.client.Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->{});
            if(visible==35) {
                try { prepare();nextSpread(); }catch(Exception e){throw new IllegalStateException("Cannot load handbook contents",e);}
            }
            if(spreadIndex>=0&&++spreadTicks==8) {
                try {
                    inspectPage(field(mc.screen,"leftPage"));inspectPage(field(mc.screen,"rightPage"));
                    var current=spreads.get(spreadIndex);
                    if(current.entry().getPath().equals("start/assembly")||current.entry().getPath().equals("start/supplies")
                        ||current.entry().getPath().equals("workshop/seats")||current.entry().getPath().startsWith("equipment/"))
                        net.minecraft.client.Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->{});
                    var page=field(mc.screen,"rightPage");
                    boolean preview=page!=null&&current.entry().getPath().startsWith("equipment/")&&page.getClass().getSimpleName().equals("PageImage");
                    if(preview) {
                        if(((ResourceLocation[])field(page,"images")).length!=2)throw new IllegalStateException("Preview must contain two states");
                        boolean clicked=false;
                        for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button
                            &&child.getClass().getSimpleName().equals("GuiButtonBookArrowSmall")&&!(boolean)field(child,"left")) {
                            button.onPress();clicked=true;break;
                        }
                        if(!clicked)throw new IllegalStateException("Preview's next-image button missing");
                    }else advance();
                }catch(Exception e){throw new IllegalStateException("Handbook spread failed: "+spreads.get(spreadIndex),e);}
            }else if(spreadIndex>=0&&spreadTicks==12) {
                try {
                    if((int)field(field(mc.screen,"rightPage"),"index")!=1)throw new IllegalStateException("Preview did not advance");
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),c->{});
                    advance();
                }catch(Exception e){throw new IllegalStateException("Preview switch failed",e);}
            }
        }
    }
    private static Object field(Object object,String name)throws ReflectiveOperationException {
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try {
            var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(object.getClass()+"."+name);
    }
    private static Object invoke(Object object,String name)throws ReflectiveOperationException {
        return object.getClass().getMethod(name).invoke(object);
    }
    private static void prepare()throws ReflectiveOperationException {
        var registry=Class.forName("vazkii.patchouli.common.book.BookRegistry").getField("INSTANCE").get(null);
        var book=((Map<?,?>)field(registry,"books")).get(BOOK);
        var contents=invoke(book,"getContents");
        if((boolean)invoke(contents,"isErrored"))throw new IllegalStateException("Book contents errored",(Exception)invoke(contents,"getException"));
        if(((Map<?,?>)field(contents,"categories")).size()!=6)throw new IllegalStateException("Missing handbook categories");
        var entries=(Map<?,?>)field(contents,"entries");
        int expected=21;
        for(String mod:List.of("carryon","touhou_little_maid","sable"))if(ModList.get().isLoaded(mod))expected++;
        if(entries.size()!=expected)throw new IllegalStateException("Expected "+expected+" entries, got "+entries.size());
        for(var id:entries.keySet().stream().map(ResourceLocation.class::cast).sorted().toList()) {
            if(Boolean.getBoolean("tm_wagon.handbookPreviewOnly")&&!id.getPath().startsWith("equipment/"))continue;
            var pages=(List<?>)invoke(entries.get(id),"getPages");
            for(int page=0;page<pages.size();page+=2)spreads.add(new Spread(id,page));
        }
    }
    private static void advance() {
        if(spreadIndex+1==spreads.size()) {
            if(overflow!=0)throw new IllegalStateException("Overflowing handbook text: "+overflow);
            LogUtils.getLogger().info("HANDBOOK_SMOKE: PASS - {} entries, {} rendered pages, {} recipe pages, {} overflowing text blocks",spreads.stream().map(Spread::entry).distinct().count(),checkedPages,checkedRecipes,overflow);
            Minecraft.getInstance().stop();
        }else nextSpread();
    }
    private static void nextSpread() {
        spreadIndex++;spreadTicks=0;
        var next=spreads.get(spreadIndex);
        PatchouliAPI.get().openBookEntry(BOOK,next.entry(),next.page());
    }
    private static void inspectPage(Object page)throws ReflectiveOperationException {
        if(page==null)return;
        checkedPages++;
        String name=page.getClass().getSimpleName();
        if(name.equals("PageTemplate")) {
            var template=field(page,"template");
            for(var component:(List<?>)field(template,"components")) {
                if(component.getClass().getSimpleName().equals("ComponentCustom")) {
                    var callbacks=field(component,"callbacks");
                    var display=(CraftingRecipe)field(callbacks,"display");
                    if(display==null||display.getResultItem(Minecraft.getInstance().level.registryAccess()).isEmpty())
                        throw new IllegalStateException("Missing handbook recipe output");
                    checkedRecipes++;
                }else if(component.getClass().getSimpleName().equals("ComponentText"))inspectText(field(component,"textRenderer"));
            }
        }else {
            try {inspectText(field(page,"textRender"));}catch(NoSuchFieldException ignored){}
        }
    }
    private static void inspectText(Object renderer)throws ReflectiveOperationException {
        if(renderer==null)return;
        var layouter=field(renderer,"layouter");
        int end=(int)field(layouter,"y")+(int)field(layouter,"lineHeight");
        float scale=(float)field(renderer,"scale");
        if(end>156||scale<.99f) {
            overflow++;
            LogUtils.getLogger().warn("HANDBOOK_LAYOUT: {} page {} end={} scale={}",spreads.get(spreadIndex).entry(),spreads.get(spreadIndex).page(),end,scale);
        }
    }
}
