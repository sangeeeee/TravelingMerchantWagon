package com.sange.tm_wagon.cargo;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Direct work-block actions and two bounded timers. No neighboring block ticks or automation. */
public final class CargoWorkBlocks {
    public static void interact(CargoHold hold,CargoEntry e,Player player,InteractionHand hand,Vec3 local) {
        if(!hold.valid(e,player))return;ItemStack stack=player.getItemInHand(hand);
        switch(e.kind) {
            case CAULDRON -> {
                var level=e.interactionLevel();var hit=new BlockHitResult(Vec3.atCenterOf(CargoLevel.POS),net.minecraft.core.Direction.UP,CargoLevel.POS,false);
                e.state.useItemOn(stack,level,player,hand,hit);
            }
            case COMPOSTER -> {
                var level=e.interactionLevel();var hit=new BlockHitResult(Vec3.atCenterOf(CargoLevel.POS),net.minecraft.core.Direction.UP,CargoLevel.POS,false);
                var result=e.state.useItemOn(stack,level,player,hand,hit);
                if(!result.consumesAction())e.state.useWithoutItem(level,player,hit);
                if(e.state.getValue(ComposterBlock.LEVEL)==7&&e.compostReady==Long.MIN_VALUE)e.compostReady=hold.owner().cargoLevel().getGameTime()+20;
            }
            case LECTERN -> {
                if(e.inventory.getItem(0).isEmpty()&&(stack.is(Items.WRITTEN_BOOK)||stack.is(Items.WRITABLE_BOOK))) {
                    var book=stack.copyWithCount(1);
                    if(player instanceof ServerPlayer p&&book.is(Items.WRITTEN_BOOK))net.minecraft.world.item.component.WrittenBookContent.resolveForItem(book,net.minecraft.network.chat.ResolutionContext.create(p.createCommandSourceStack().withPosition(hold.position(e))),p.level().registryAccess());
                    e.inventory.setItem(0,book);stack.consume(1,player);e.page=0;
                }
                if(!e.inventory.getItem(0).isEmpty())CargoMenus.open(hold,e,player);
            }
            case BOOKSHELF -> {
                // Convert the clicked miniature face back to vanilla 0..1 coordinates.
                Vec3 p=hold.centreAt(hold.slot(e));double x=(local.x-p.x)/CargoHold.SCALE+.5,y=(local.y-p.y)/CargoHold.SCALE,z=(local.z-p.z)/CargoHold.SCALE+.5;
                var facing=e.state.getValue(HorizontalDirectionalBlock.FACING);
                boolean front=switch(facing) { case NORTH->z<.04;case SOUTH->z>.96;case WEST->x<.04;case EAST->x>.96;default->false; };
                if(!front)return;
                double faceX=switch(facing) { case NORTH->1-x;case SOUTH->x;case WEST->z;case EAST->1-z;default->x; };
                int index=(y>=.5?0:3)+(faceX<.375?0:faceX<.6875?1:2);
                if(!e.inventory.getItem(index).isEmpty()) {
                    player.getInventory().placeItemBackInInventory(e.inventory.removeItemNoUpdate(index),false);e.inventory.setChanged();sound(hold,e,SoundEvents.CHISELED_BOOKSHELF_PICKUP);
                } else if(stack.is(ItemTags.BOOKSHELF_BOOKS)) {
                    e.inventory.setItem(index,stack.copyWithCount(1));stack.consume(1,player);sound(hold,e,SoundEvents.CHISELED_BOOKSHELF_INSERT);
                }
            }
            case SHELF -> {
                if(hand!=InteractionHand.MAIN_HAND)return;
                Vec3 p=hold.centreAt(hold.slot(e));double x=(local.x-p.x)/CargoHold.SCALE+.5,y=(local.y-p.y)/CargoHold.SCALE,z=(local.z-p.z)/CargoHold.SCALE+.5;
                var facing=e.state.getValue(ShelfBlock.FACING);
                boolean front=switch(facing) { case NORTH->z<.04;case SOUTH->z>.96;case WEST->x<.04;case EAST->x>.96;default->false; };
                if(!front)return;
                var target=((ShelfBlock)e.state.getBlock()).getHitSlot(new BlockHitResult(new Vec3(x,y,z),facing,net.minecraft.core.BlockPos.ZERO,false),facing);
                if(target.isEmpty())return;
                int index=target.getAsInt();var stored=e.inventory.getItem(index);
                if(stack.isEmpty()&&stored.isEmpty())return;
                // Commit an entire-stack swap once; the shelf and hand never share mutable stacks.
                var removed=stored.copy();e.inventory.setItem(index,stack.copy());
                player.setItemInHand(hand,player.hasInfiniteMaterials()&&removed.isEmpty()?stack.copy():removed);
                player.getInventory().setChanged();
                sound(hold,e,removed.isEmpty()?SoundEvents.SHELF_PLACE_ITEM:stack.isEmpty()?SoundEvents.SHELF_TAKE_ITEM:SoundEvents.SHELF_SINGLE_SWAP);
            }
            case POT -> {
                var stored=e.inventory.getItem(0);
                if(!stack.isEmpty()&&(stored.isEmpty()||ItemStack.isSameItemSameComponents(stored,stack))&&stored.getCount()<stack.getMaxStackSize()) {
                    if(stored.isEmpty())e.inventory.setItem(0,stack.copyWithCount(1));else { stored.grow(1);e.inventory.setChanged(); }
                    stack.consume(1,player);sound(hold,e,SoundEvents.DECORATED_POT_INSERT);
                } else sound(hold,e,SoundEvents.DECORATED_POT_INSERT_FAIL);
            }
            default -> CargoMenus.open(hold,e,player);
        }
    }
    private static void sound(CargoHold hold,CargoEntry e,SoundEvent sound) {
        Vec3 p=hold.position(e);hold.owner().cargoLevel().playSound(null,p.x,p.y,p.z,sound,SoundSource.BLOCKS,1,1);
    }
    static void updateVisualState(CargoEntry e) {
        var old=e.state;
        if(e.kind==CargoEntry.Kind.BREWING)for(int i=0;i<3;i++)e.state=e.state.setValue(BrewingStandBlock.HAS_BOTTLE[i],!e.inventory.getItem(i).isEmpty());
        if(e.kind==CargoEntry.Kind.LECTERN)e.state=e.state.setValue(LecternBlock.HAS_BOOK,!e.inventory.getItem(0).isEmpty());
        if(e.kind==CargoEntry.Kind.BOOKSHELF)for(int i=0;i<6;i++)e.state=e.state.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(i),!e.inventory.getItem(i).isEmpty());
        if(old!=e.state)e.hold.changed(true);
    }
    static void tick(CargoEntry e) {
        if(e.kind==CargoEntry.Kind.COMPOSTER) {
            if(e.state.getValue(ComposterBlock.LEVEL)==7) {
                long now=e.hold.owner().cargoLevel().getGameTime();
                if(e.compostReady==Long.MIN_VALUE)e.compostReady=now+20;
                if(now>=e.compostReady) { e.state=e.state.setValue(ComposterBlock.LEVEL,8);e.compostReady=Long.MIN_VALUE;sound(e.hold,e,SoundEvents.COMPOSTER_READY);e.hold.changed(true); }
            }
            return;
        }
        if(e.kind!=CargoEntry.Kind.BREWING)return;
        var inventory=e.inventory;var level=(net.minecraft.server.level.ServerLevel)e.hold.owner().cargoLevel();var ingredient=inventory.getItem(3);
        var fuel=inventory.getItem(4);
        if(e.brewFuel==0&&fuel.is(ItemTags.BREWING_FUEL)) {
            e.brewFuel=20;fuel.shrink(1);inventory.setChanged();
        }
        var brewing=level.potionBrewing();
        boolean canBrew=false;
        for(int i=0;i<3&&!ingredient.isEmpty();i++)if(brewing.hasMix(inventory.getItem(i),ingredient)){canBrew=true;break;}
        if(e.brewTime>0) {
            if(!canBrew||!ingredient.is(e.brewingIngredient))e.brewTime=0;
            else if(--e.brewTime==0) {
                var items=NonNullList.withSize(5,ItemStack.EMPTY);for(int i=0;i<5;i++)items.set(i,inventory.getItem(i));
                 {
                    for(int i=0;i<3;i++)items.set(i,brewing.mix(items.get(3),items.get(i)));
                    
                    var input=items.get(3);var remainder=(input.getCraftingRemainder()==null?ItemStack.EMPTY:input.getCraftingRemainder().create());input.shrink(1);
                    if(!remainder.isEmpty()) {
                        if(input.isEmpty())items.set(3,remainder);
                        else { Vec3 pos=e.hold.position(e);var drop=new net.minecraft.world.entity.item.ItemEntity(e.hold.owner().cargoLevel(),pos.x,pos.y+.1,pos.z,remainder);drop.setDefaultPickUpDelay();e.hold.owner().cargoLevel().addFreshEntity(drop); }
                    }
                    for(int i=0;i<5;i++)inventory.setItem(i,items.get(i));sound(e.hold,e,SoundEvents.BREWING_STAND_BREW);
                }
            }
            e.hold.changed(false);
        } else if(canBrew&&e.brewFuel>0) { e.brewFuel--;e.brewTime=400;e.brewingIngredient=ingredient.getItem();e.hold.changed(false); }
    }
    static ContainerData brewingData(CargoEntry e) {
        return new ContainerData() {
            public int get(int i) { return switch(i) { case 0->e.brewTime;case 1->e.brewFuel;default->0; }; }
            public void set(int i,int v) { switch(i) { case 0->e.brewTime=v;case 1->e.brewFuel=v; } }
            public int getCount() { return 2; }
        };
    }
    static ContainerData lecternData(CargoEntry e) {
        return new ContainerData() {
            public int get(int i) { return e.page; }
            public void set(int i,int v) {
                var book=e.inventory.getItem(0);var written=book.get(DataComponents.WRITTEN_BOOK_CONTENT);var writable=book.get(DataComponents.WRITABLE_BOOK_CONTENT);
                int count=written!=null?written.pages().size():writable!=null?writable.pages().size():0;
                e.page=Math.clamp(v,0,Math.max(0,count-1));e.hold.changed(false);
            }
            public int getCount() { return 1; }
        };
    }
    private CargoWorkBlocks() {}
}
