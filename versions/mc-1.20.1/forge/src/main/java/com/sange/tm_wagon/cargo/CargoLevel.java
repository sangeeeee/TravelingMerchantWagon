package com.sange.tm_wagon.cargo;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;

/** A bounded one-block view for vanilla cargo interactions, never a world/chunk to tick.
 * Mutations affect only this entry; adjacent blocks are air and neighbor/redstone updates are disabled. */
class CargoLevel extends Level {
    static final BlockPos POS=new BlockPos(0,64,0);
    private final CargoHold hold;
    private final CargoEntry entry;
    CargoLevel(CargoHold hold,CargoEntry entry) {
        super((WritableLevelData)hold.owner().cargoLevel().getLevelData(),hold.owner().cargoLevel().dimension(),
            hold.owner().cargoLevel().registryAccess(),hold.owner().cargoLevel().dimensionTypeRegistration(),
            hold.owner().cargoLevel().getProfilerSupplier(),false,false,0,0);
        this.hold=hold;this.entry=entry;
    }
    private Level world() { return hold.owner().cargoLevel(); }
    private Vec3 position() { return entry==null?hold.owner().cargoPose().position():hold.position(entry); }
    @Override public BlockState getBlockState(BlockPos pos) { return entry!=null&&POS.equals(pos)?entry.state:Blocks.AIR.defaultBlockState(); }
    @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    @Override public boolean setBlock(BlockPos pos,BlockState state,int flags,int recursion) {
        if(entry==null||!POS.equals(pos)||hold.slot(entry)<0)return false;
        if(state.isAir())hold.consume(entry);
        else { entry.state=state;hold.changed(true); }
        return true;
    }
    @Override public void sendBlockUpdated(BlockPos pos,BlockState oldState,BlockState state,int flags) { hold.changed(true); }
    @Override public void blockEntityChanged(BlockPos pos) { hold.changed(false); }
    @Override public void updateNeighborsAt(BlockPos pos,Block block) {}
    @Override public void updateNeighbourForOutputSignal(BlockPos pos,Block block) {}
    @Override public LevelTickAccess<Block> getBlockTicks() { return BlackholeTickAccess.emptyLevelList(); }
    @Override public LevelTickAccess<Fluid> getFluidTicks() { return BlackholeTickAccess.emptyLevelList(); }
    @Override public ChunkSource getChunkSource() { return world().getChunkSource(); }
    @Override public String gatherChunkSourceStats() { return "wagon cargo"; }
    @Override public Entity getEntity(int id) { return world().getEntity(id); }
    @Override protected LevelEntityGetter<Entity> getEntities() { throw new UnsupportedOperationException("Cargo has no entity storage"); }
    @Override public List<? extends Player> players() { return world().players(); }
    @Override public RecipeManager getRecipeManager() { return world().getRecipeManager(); }
    @Override public float getShade(net.minecraft.core.Direction direction,boolean shade) { return world().getShade(direction,shade); }
    @Override public Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int x,int y,int z) { return world().getUncachedNoiseBiome(x,y,z); }
    @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return world().enabledFeatures(); }
    @Override public MapItemSavedData getMapData(String id) { return world().getMapData(id); }
    @Override public void setMapData(String id,MapItemSavedData data) { world().setMapData(id,data); }
    @Override public int getFreeMapId() { return world().getFreeMapId(); }
    @Override public Scoreboard getScoreboard() { return world().getScoreboard(); }
    @Override public void destroyBlockProgress(int breaker,BlockPos pos,int progress) {}
    @Override public void playSeededSound(Player player,double x,double y,double z,Holder<SoundEvent> sound,SoundSource source,float volume,float pitch,long seed) {
        Vec3 p=position();world().playSeededSound(player,p.x,p.y,p.z,sound,source,volume,pitch,seed);
    }
    @Override public void playSeededSound(Player player,Entity entity,Holder<SoundEvent> sound,SoundSource source,float volume,float pitch,long seed) {
        Vec3 p=position();world().playSeededSound(player,p.x,p.y,p.z,sound,source,volume,pitch,seed);
    }
    @Override public void levelEvent(Player player,int id,BlockPos pos,int data) { world().levelEvent(player,id,BlockPos.containing(position()),data); }
    @Override public void gameEvent(GameEvent event,Vec3 pos,GameEvent.Context context) { world().gameEvent(event,position(),context); }
    @Override public boolean addFreshEntity(Entity entity) {
        // Vanilla direct-interaction drops are created in this view; rebuild them in the real world.
        if(entity instanceof net.minecraft.world.entity.item.ItemEntity item) {
            Vec3 p=position();var drop=new net.minecraft.world.entity.item.ItemEntity(world(),p.x,p.y+.1,p.z,item.getItem());
            drop.setDefaultPickUpDelay();return world().addFreshEntity(drop);
        }
        return false;
    }
}
