package com.sange.tm_wagon.cargo;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Reuses vanilla screens/slots. Only the authoritative cargo container stores items. */
public final class CargoMenus {
    private record View(CargoHold hold,CargoEntry entry) {}
    private static final Map<Player,View> VIEWERS=new IdentityHashMap<>();
    public static void open(CargoHold hold,CargoEntry entry,Player player) {
        if(!(player instanceof ServerPlayer)||!hold.valid(entry,player))return;
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->{
            AbstractContainerMenu menu=switch(entry.kind) {
                case CHEST,BARREL->new ChestMenu(MenuType.GENERIC_9x3,id,inventory,entry.inventory,3) {
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case SHULKER->new ShulkerBoxMenu(id,inventory,entry.inventory) {
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case FURNACE->new FurnaceMenu(id,inventory,entry.inventory,entry.data()) {
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case SMOKER->new SmokerMenu(id,inventory,entry.inventory,entry.data()) {
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case BLAST_FURNACE->new BlastFurnaceMenu(id,inventory,entry.inventory,entry.data()) {
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case BREWING->new BrewingStandMenu(id,inventory,entry.inventory,CargoWorkBlocks.brewingData(entry)) {
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case LECTERN->new LecternMenu(id,entry.inventory,CargoWorkBlocks.lecternData(entry)) {
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case ENDER_CHEST->new ChestMenu(MenuType.GENERIC_9x3,id,inventory,p.getEnderChestInventory(),3) {
                    @Override public boolean stillValid(Player p) { return hold.valid(entry,p); }
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case CRAFTING->new CraftingMenu(id,inventory,access(hold,entry)) {
                    @Override public boolean stillValid(Player p) { return hold.valid(entry,p); }
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case CARTOGRAPHY->new CartographyTableMenu(id,inventory,access(hold,entry)) {
                    @Override public boolean stillValid(Player p) { return hold.valid(entry,p); }
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case STONECUTTER->new StonecutterMenu(id,inventory,access(hold,entry)) {
                    @Override public boolean stillValid(Player p) { return hold.valid(entry,p); }
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case ANVIL->new AnvilMenu(id,inventory,access(hold,entry)) {
                    @Override public boolean stillValid(Player p) { return hold.valid(entry,p); }
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case SMITHING->new SmithingMenu(id,inventory,access(hold,entry)) {
                    @Override public boolean stillValid(Player p) { return hold.valid(entry,p); }
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case LOOM->new LoomMenu(id,inventory,access(hold,entry)) {
                    @Override public boolean stillValid(Player p) { return hold.valid(entry,p); }
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case GRINDSTONE->new GrindstoneMenu(id,inventory,access(hold,entry)) {
                    @Override public boolean stillValid(Player p) { return hold.valid(entry,p); }
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                case ENCHANTING->new EnchantmentMenu(id,inventory,access(hold,entry)) {
                    @Override public boolean stillValid(Player p) { return hold.valid(entry,p); }
                    @Override public void removed(Player p) { super.removed(p);closed(p); }
                };
                default->null;
            };
            if(menu==null)return null;
            if(entry.cooking()) {
                var result=new FurnaceResultSlot(p,entry.inventory,2,116,35) {
                    @Override protected void checkTakeAchievements(ItemStack stack) { super.checkTakeAchievements(stack);entry.popExperience(p); }
                };
                result.index=2;menu.slots.set(2,result);
            }
            VIEWERS.put(p,new View(hold,entry));entry.setOpened(true);
            var sound=switch(entry.kind) {
                case SHULKER->net.minecraft.sounds.SoundEvents.SHULKER_BOX_OPEN;
                case BARREL->net.minecraft.sounds.SoundEvents.BARREL_OPEN;
                case ENDER_CHEST->net.minecraft.sounds.SoundEvents.ENDER_CHEST_OPEN;
                default->net.minecraft.sounds.SoundEvents.CHEST_OPEN;
            };
            if(entry.kind==CargoEntry.Kind.SHULKER||entry.kind==CargoEntry.Kind.BARREL||entry.kind==CargoEntry.Kind.CHEST||entry.kind==CargoEntry.Kind.ENDER_CHEST) {
                var pos=hold.position(entry);hold.owner().cargoLevel().playSound(null,pos.x,pos.y,pos.z,sound,net.minecraft.sounds.SoundSource.BLOCKS,.5F,1);
            }
            return menu;
        },entry.item.getHoverName()));
    }
    private static ContainerLevelAccess access(CargoHold hold,CargoEntry entry) {
        return new ContainerLevelAccess() {
            public <T> java.util.Optional<T> evaluate(java.util.function.BiFunction<Level,BlockPos,T> action) {
                // Anvil wear must never mutate real blocks. An isolated enchanting table has no shelves.
                if(entry.kind==CargoEntry.Kind.ANVIL||entry.kind==CargoEntry.Kind.ENCHANTING)
                    return java.util.Optional.ofNullable(action.apply(entry.interactionLevel(),CargoLevel.POS));
                return java.util.Optional.ofNullable(action.apply(hold.owner().cargoLevel(),BlockPos.containing(hold.position(entry))));
            }
        };
    }
    private static void closed(Player player) {
        var view=VIEWERS.remove(player);if(view==null)return;
        if(VIEWERS.values().stream().noneMatch(other->other.entry==view.entry))view.entry.setOpened(false);
    }
    /** Close only viewers whose own path was blocked by the new cover state. */
    public static void closeObstructed(CargoHold hold) {
        var players=new java.util.ArrayList<Player>();
        VIEWERS.forEach((player,view)->{if(view.hold==hold&&!hold.valid(view.entry,player))players.add(player);});
        for(var player:players) { player.closeContainer();closed(player); }
    }
    public static void close(CargoHold hold,CargoEntry entry) {
        var players=new java.util.ArrayList<Player>();
        VIEWERS.forEach((player,view)->{if(view.hold==hold&&(entry==null||view.entry==entry))players.add(player);});
        for(var player:players) { player.closeContainer();closed(player); }
    }
    private CargoMenus() {}
}
