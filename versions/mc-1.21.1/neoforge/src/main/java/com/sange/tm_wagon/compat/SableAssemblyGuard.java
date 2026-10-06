package com.sange.tm_wagon.compat;

import com.sange.tm_wagon.assembly.*;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Loaded only with Sable. Remove ownership before Sable can copy inventory NBT. */
public final class SableAssemblyGuard {
    private static final class Removal {
        boolean frame,cover,canopy;
        final EnumSet<WagonSlot> slots=EnumSet.noneOf(WagonSlot.class);
        final java.util.Set<Integer> cargo=new java.util.HashSet<>();
        java.util.Set<BlockPos> bodyCells,coverCells,canopyCells;
        final java.util.Map<BlockPos,java.util.Set<Integer>> cargoCells=new java.util.HashMap<>();
        void bodyCell(AssemblyFrameBlockEntity frame,BlockPos pos) {
            if(bodyCells==null) {
                var hold=frame.cargo();var body=frame.cargoBody();var facing=frame.facing();
                bodyCells=WagonGeometry.customCells(hold.structuralBodyBoxes(body),facing).keySet();
                var cloth=new ArrayList<>(hold.cover().boxes(body));
                coverCells=new java.util.HashSet<>(WagonGeometry.customCells(cloth,facing).keySet());
                coverCells.addAll(hold.cover().selectionCells(body,facing).keySet());
                canopyCells=WagonGeometry.customCells(hold.canopy().selectionBoxes(body),facing).keySet();
                for(int i=0;i<hold.capacity();i++) {
                    var entry=hold.entry(i);if(entry==null||hold.slot(entry)!=i)continue;
                    for(var cell:WagonGeometry.customCells(List.of(hold.entryBox(i)),facing).keySet())
                        cargoCells.computeIfAbsent(cell,key->new java.util.HashSet<>()).add(i);
                }
            }
            BlockPos local=pos.subtract(frame.getBlockPos());
            if(bodyCells.contains(local))slots.add(WagonSlot.BODY);
            cover|=coverCells.contains(local);canopy|=canopyCells.contains(local);
            cargo.addAll(cargoCells.getOrDefault(local,java.util.Set.of()));
        }
    }
    private static boolean forbidden(ServerLevel level,BlockPos pos) {
        Block block=level.getBlockState(pos).getBlock();
        return block instanceof AssemblyFrameBlock||block instanceof AssemblyPartBlock;
    }
    private static void collect(ServerLevel level,BlockPos pos,LinkedHashMap<AssemblyFrameBlockEntity,Removal> requests) {
        var entity=level.getBlockEntity(pos);
        if(entity instanceof AssemblyFrameBlockEntity frame) {
            requests.computeIfAbsent(frame,key->new Removal()).frame=true;
        } else if(entity instanceof AssemblyCellBlockEntity cell) {
            // A partial selection can cross the controller chunk boundary. This is
            // an explicit assembly operation, so load that one authoritative owner.
            level.getChunkAt(cell.owner());
            if(level.getBlockEntity(cell.owner()) instanceof AssemblyFrameBlockEntity frame) {
                var part=frame.layout().get(cell.source());
                if(part==null)return;
                var removal=requests.computeIfAbsent(frame,key->new Removal());
                removal.frame|=part.framePart();
                for(var slot:part.slots()) {
                    if(slot==WagonSlot.BODY)removal.bodyCell(frame,cell.source());else removal.slots.add(slot);
                }
            }
        }
    }
    private static void remove(ServerLevel level,LinkedHashMap<AssemblyFrameBlockEntity,Removal> requests) {
        requests.forEach((frame,request)->{
            if(frame.isRemoved()||frame.changing())return;
            // Unlike damage to an entity wagon, forced block dismantling returns
            // the optional cabinet too. Its inventory is emptied by normal removal.
            ItemStack cabinet=(request.frame||request.slots.contains(WagonSlot.BODY))&&frame.cargo().cabinet().installed()
                ?frame.cargo().cabinet().material().stack(WagonContent.CABINET.get()):ItemStack.EMPTY;
            if(request.frame)frame.dismantle(true,true);
            else {
                if(!request.slots.contains(WagonSlot.BODY)) {
                    boolean drops=level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
                    if(request.cover)frame.cargo().cover().destroy(drops);
                    if(request.canopy)frame.cargo().canopy().destroy(drops);
                    frame.cargo().destroySlots(request.cargo,drops);
                }
                frame.remove(request.slots,true);
            }
            if(!cabinet.isEmpty()&&level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS))
                Block.popResource(level,frame.getBlockPos().above(),cabinet);
        });
    }
    public static List<BlockPos> beforeMove(ServerLevel source,AssemblyTransform transform,Iterable<BlockPos> positions) {
        var kept=new ArrayList<BlockPos>();
        var requests=new LinkedHashMap<AssemblyFrameBlockEntity,Removal>();
        for(BlockPos mutable:positions) {
            BlockPos pos=mutable.immutable();
            if(forbidden(source,pos)&&(Sable.HELPER.getContaining(transform.getLevel(),transform.apply(pos))!=null
                ||Sable.HELPER.getContaining(source,pos)!=null))collect(source,pos,requests);
            else kept.add(pos);
        }
        remove(source,requests);
        // Never copy a former wagon cell, including shared cells still occupied by
        // an unaffected module. Ordinary selected structure blocks remain movable.
        return kept;
    }
    public static void rejectPlaced(ServerLevel level,BlockPos pos) {
        if(!forbidden(level,pos)||Sable.HELPER.getContaining(level,pos)==null)return;
        var requests=new LinkedHashMap<AssemblyFrameBlockEntity,Removal>();
        collect(level,pos,requests);remove(level,requests);
        // A detached proxy has no independent inventory or item entitlement. Its
        // controller, if present, is the sole authority for component drops.
        if(forbidden(level,pos))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
    }
    private SableAssemblyGuard() {}
}
