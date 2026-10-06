package com.sange.tm_wagon.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.sange.tm_wagon.assembly.WagonContent;
import com.sange.tm_wagon.assembly.WagonPart;
import com.sange.tm_wagon.assembly.WagonSlot;
import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon_maid_combat")
@PrefixGameTestTemplate(false)
public class WagonMaidCombatGameTests {
    @GameTest(template="assembly_test",batch="maid_combat",timeoutTicks=180)
    public static void companion_bow_hits_hostile_without_dismounting(GameTestHelper h) { Scenarios.shoot(h,Items.BOW); }
    @GameTest(template="assembly_test",batch="maid_combat",timeoutTicks=180)
    public static void companion_crossbow_hits_hostile_without_dismounting(GameTestHelper h) { Scenarios.shoot(h,Items.CROSSBOW); }
    @GameTest(template="assembly_test",batch="maid_combat",timeoutTicks=220)
    public static void companion_switches_weapons_and_stops_when_empty_handed(GameTestHelper h) { Scenarios.switchWeapons(h); }
    @GameTest(template="assembly_test",batch="maid_combat",timeoutTicks=120)
    public static void companion_keeps_native_target_rules_and_requires_ammunition(GameTestHelper h) { Scenarios.ammunition(h); }
    @GameTest(template="assembly_test",batch="maid_combat",timeoutTicks=220)
    public static void companion_tacz_gun_hits_hostile_without_dismounting(GameTestHelper h) {
        if(!ModList.get().isLoaded("tacz")) { h.succeed();return; }
        Scenarios.gun(h);
    }

    private static final class Scenarios {
        private static final class Fixture {
            final WagonEntity wagon;final EntityMaid maid;final Zombie target;
            boolean monitoring=true;
            Fixture(WagonEntity wagon,EntityMaid maid,Zombie target) { this.wagon=wagon;this.maid=maid;this.target=target; }
        }
        private static Fixture fixture(GameTestHelper h) {
            h.getLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,h.getLevel().getServer());
            h.getLevel().setDayTime(1000);
            for(int x=0;x<24;x++)for(int z=0;z<30;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            var wagon=WagonContent.WAGON.get().create(h.getLevel());var parts=WagonEntity.defaultParts();
            parts.put(WagonSlot.SEAT,WagonPart.DOUBLE_SEAT);wagon.configure(parts,Direction.NORTH);
            wagon.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(11,2,17))));h.getLevel().addFreshEntity(wagon);
            h.getLevel().setBlock(wagon.blockPosition(),WagonContent.FRAME.get().defaultBlockState(),3);
            var maid=EntityMaid.TYPE.create(h.getLevel());maid.setPos(wagon.position().add(2,0,-2));
            maid.setTame(true,true);maid.setHomeModeEnable(false);maid.setSchedule(MaidSchedule.ALL);
            maid.setTask(TaskManager.findTask(WagonMaidExtension.TASK).orElseThrow());h.getLevel().addFreshEntity(maid);
            h.assertTrue(wagon.boardCompanion(maid,1),"Combat fixture could not board companion seat");
            var target=EntityType.ZOMBIE.create(h.getLevel());target.setNoAi(true);target.setPersistenceRequired();
            target.setItemSlot(EquipmentSlot.HEAD,Items.DIAMOND_HELMET.getDefaultInstance());
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);
            target.setPos(wagon.position().add(.5,0,-8));h.getLevel().addFreshEntity(target);
            var fixture=new Fixture(wagon,maid,target);
            h.onEachTick(()->{
                if(fixture.monitoring&&WagonMaidExtension.TASK.equals(maid.getTask().getUid()))h.assertTrue(maid.getVehicle()==wagon,
                    "Companion left its seat during ranged combat: activity="+maid.getScheduleDetail()+" day="+h.getLevel().getDayTime()+" wagonRemoved="+wagon.isRemoved());
            });
            return fixture;
        }
        static void shoot(GameTestHelper h,Item item) {
            var f=fixture(h);f.maid.setItemInHand(InteractionHand.MAIN_HAND,item.getDefaultInstance());
            f.maid.getMaidInv().setStackInSlot(0,new ItemStack(Items.ARROW,16));
            h.succeedWhen(()->{
                h.assertTrue(f.target.getHealth()<100,"Mounted companion did not hit hostile with "+item);
                h.assertTrue(f.maid.getMaidInv().getStackInSlot(0).getCount()<16,"Mounted shooting did not consume arrows");
                h.assertTrue(f.maid.getMainHandItem().getDamageValue()>0,"Mounted shooting did not damage weapon");
                f.monitoring=false;
            });
        }
        static void switchWeapons(GameTestHelper h) {
            var f=fixture(h);f.maid.setItemInHand(InteractionHand.MAIN_HAND,Items.BOW.getDefaultInstance());
            f.maid.getMaidInv().setStackInSlot(0,new ItemStack(Items.ARROW,16));
            h.runAfterDelay(65,()->{
                h.assertTrue(f.target.getHealth()<100,"Bow did not shoot before weapon swap");
                f.target.setHealth(100);f.maid.setItemInHand(InteractionHand.MAIN_HAND,Items.CROSSBOW.getDefaultInstance());
            });
            h.runAfterDelay(130,()->{
                h.assertTrue(f.target.getHealth()<100,"Crossbow did not shoot after weapon swap");
                f.maid.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            });
            // Let arrows fired before the swap land before measuring empty-handed damage.
            h.runAfterDelay(155,()->f.target.setHealth(100));
            h.runAfterDelay(185,()->{
                h.assertTrue(f.target.getHealth()==100&&!f.maid.isUsingItem()&&!f.maid.isAiming(),
                    "Empty-handed companion kept shooting: health="+f.target.getHealth()+" using="+f.maid.isUsingItem()+" aiming="+f.maid.isAiming());
                f.maid.setTask(TaskManager.getIdleTask());
                h.assertTrue(!f.maid.isPassenger(),"Changing combat task did not release seat");h.succeed();
                f.monitoring=false;
            });
        }
        static void ammunition(GameTestHelper h) {
            var f=fixture(h);f.maid.setItemInHand(InteractionHand.MAIN_HAND,Items.BOW.getDefaultInstance());
            var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setPos(f.wagon.position().add(.5,0,-5));
            h.getLevel().addFreshEntity(cow);
            h.assertTrue(!f.maid.canAttack(cow)&&f.maid.canAttack(f.target),"Companion lost Little Maid's hostile targeting rules");
            h.runAfterDelay(90,()->{
                h.assertTrue(f.target.getHealth()==100&&!f.maid.isUsingItem(),"Bow shot without ammunition");
                h.assertTrue(cow.getHealth()==cow.getMaxHealth(),"Companion attacked a passive animal");
                f.monitoring=false;h.succeed();
            });
        }
        static void gun(GameTestHelper h) {
            var f=fixture(h);
            try {
                // Runtime-only fixture: production integration uses Little Maid's optional TACZ bridge.
                var type=Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
                var builder=type.getMethod("create").invoke(null);
                type.getMethod("setId",ResourceLocation.class).invoke(builder,ResourceLocation.parse("tacz:glock_17"));
                type.getMethod("setAmmoCount",int.class).invoke(builder,16);
                var stack=(ItemStack)type.getMethod("build",net.minecraft.core.HolderLookup.Provider.class).invoke(builder,h.getLevel().registryAccess());
                h.assertTrue(!stack.isEmpty(),"TACZ fixture could not build Glock 17");
                f.maid.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var gunApi=Class.forName("com.tacz.guns.api.item.IGun");
                var gun=gunApi.getMethod("getIGunOrNull",ItemStack.class).invoke(null,stack);
                var ammo=gunApi.getMethod("getCurrentAmmoCount",ItemStack.class);
                h.succeedWhen(()->{
                    h.assertTrue(f.target.getHealth()<100,"Mounted TACZ companion did not damage hostile");
                    try { h.assertTrue((int)ammo.invoke(gun,stack)<16,"Mounted TACZ shooting did not consume ammunition"); }
                    catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
                    f.monitoring=false;
                });
            } catch(ReflectiveOperationException e) { throw new IllegalStateException("TACZ combat fixture failed",e); }
        }
    }
}
