package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class DismantlingHammerGameTests {
    public static ItemStack strike(GameTestHelper h,WagonEntity wagon) {
        var player=h.makeMockPlayer(GameType.SURVIVAL);var hammer=WagonContent.DISMANTLING_HAMMER.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND,hammer);
        Vec3 eye=wagon.pose().point(new Vec3(-2.8,2,0)),target=wagon.pose().point(new Vec3(0,2,0));
        player.setPos(eye.subtract(0,player.getEyeHeight(),0));Vec3 delta=target.subtract(eye);
        player.setYRot((float)-Math.toDegrees(Math.atan2(delta.x,delta.z)));player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        player.attack(wagon);h.assertTrue(wagon.isRemoved(),"Hammer did not dismantle wagon with a direct attack");return hammer;
    }
    private static WagonEntity wagon(GameTestHelper h) {
        var w=WagonContent.WAGON.get().create(h.getLevel());w.configure(WagonEntity.defaultParts(),Direction.NORTH);
        w.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(10,3,12))));h.getLevel().addFreshEntity(w);return w;
    }
    @GameTest(template="assembly_test")
    public static void ordinary_damage_is_ignored_and_hammer_commits_once(GameTestHelper h) {
        var w=wagon(h);var p=h.makeMockPlayer(GameType.SURVIVAL);
        for(var item:new Item[]{Items.AIR,Items.IRON_AXE,Items.DIAMOND_SWORD}) {
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item));p.attack(w);
            h.assertTrue(!w.isRemoved(),"Ordinary attack dismantled wagon");
        }
        h.assertTrue(!w.hurt(w.damageSources().generic(),10000)&&!w.hurt(w.damageSources().inFire(),10000)&&!w.isRemoved(),"Wagon accepted damage");
        var hammer=strike(h,w);h.assertTrue(hammer.getDamageValue()==1,"Dismantling should consume one durability");
        int before=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,w.getBoundingBox().inflate(2)).size();
        p.setItemInHand(InteractionHand.MAIN_HAND,hammer);p.attack(w);w.cargo().destroy(true,true);w.discard();
        h.assertTrue(hammer.getDamageValue()==1&&h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,w.getBoundingBox().inflate(2)).size()==before,"Repeated dismantle duplicated items or durability");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void precise_pick_respects_gaps_walls_floor_and_inside_eye(GameTestHelper h) {
        var w=wagon(h);var p=h.makeMockPlayer(GameType.SURVIVAL);
        for(float yaw:new float[]{180,225,270}) {
            w.applyPose(new com.sange.tm_wagon.physics.WagonPose(w.position(),yaw,.12F,.08F));
            Vec3 start=w.pose().point(new Vec3(-3,.6,0)),end=w.pose().point(new Vec3(3,.6,0));
            p.setPos(start.subtract(0,p.getEyeHeight(),0));
            h.assertTrue(w.getBoundingBox().contains(start)||w.getBoundingBox().clip(start,end).isPresent(),"Fixture misses broad bounds at "+yaw);
            h.assertTrue(ProjectileUtil.getEntityHitResult(p,start,end,new AABB(start,end).inflate(1),Entity::isPickable,36)==null,"Empty undercarriage gap blocked picking at "+yaw);
            start=w.pose().point(new Vec3(-3,2,0));end=w.pose().point(new Vec3(3,2,0));
            var wall=ProjectileUtil.getEntityHitResult(p,start,end,new AABB(start,end).inflate(1),Entity::isPickable,36);
            h.assertTrue(wall!=null&&wall.getEntity()==w,"Wall failed to block picking at "+yaw);
            start=w.pose().point(new Vec3(0,2,0));end=w.pose().point(new Vec3(3,2,0));
            var inside=ProjectileUtil.getEntityHitResult(p,start,end,new AABB(start,end).inflate(1),Entity::isPickable,9);
            h.assertTrue(inside!=null&&inside.getEntity()==w&&inside.getLocation().distanceToSqr(start)>.1,"Inside broad bounds picked eye instead of actual wall");
            start=w.pose().point(new Vec3(0,4,0));end=w.pose().point(new Vec3(0,-1,0));
            var floor=ProjectileUtil.getEntityHitResult(p,start,end,new AABB(start,end).inflate(1),Entity::isPickable,25);
            h.assertTrue(floor!=null&&floor.getEntity()==w&&w.pose().local(floor.getLocation()).y>1,"Wagon floor allowed ground picking");
            var clipped=ProjectileUtil.getEntityHitResult(p,start,end,new AABB(start,end).inflate(1),Entity::isPickable,.1);
            h.assertTrue(clipped==null,"Actual part ignored nearer block/reach limit");
        }
        w.applyPose(new com.sange.tm_wagon.physics.WagonPose(w.position(),180,0,0));
        Vec3 start=w.position().add(-3,2,0),end=w.position().add(3,2,0);
        var mob=net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());mob.setPos(w.position().add(-2,1.8,0));h.getLevel().addFreshEntity(mob);
        var near=ProjectileUtil.getEntityHitResult(p,start,end,new AABB(start,end).inflate(1),Entity::isPickable,36);
        h.assertTrue(near!=null&&near.getEntity()==mob,"Wagon hid a nearer ordinary entity");mob.setPos(w.position().add(0,1.8,0));
        var behind=ProjectileUtil.getEntityHitResult(p,start,end,new AABB(start,end).inflate(1),Entity::isPickable,36);
        h.assertTrue(behind!=null&&behind.getEntity()==w,"Entity behind wall could be attacked through wagon");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void hammer_recovers_accessories_and_container_contents_once(GameTestHelper h) {
        var w=wagon(h);var hold=w.cargo();var p=h.makeMockPlayer(GameType.SURVIVAL);p.setPos(w.position().add(-3,1,0));
        var stool=new com.sange.tm_wagon.material.WagonMaterial(com.sange.tm_wagon.material.WoodMaterial.BIRCH,DyeColor.WHITE);
        h.assertTrue(hold.place(0,new ItemStack(Items.CHEST),p)==null&&hold.place(1,new ItemStack(Items.SHULKER_BOX),p)==null,"Container fixtures failed");
        h.assertTrue(hold.place(4,stool.stack(WagonContent.STOOL.get()),p)==null&&hold.place(9,WagonContent.STRAW_MAT.get().getDefaultInstance(),p)==null,"Accessory fixtures failed");
        hold.entry(0).inventory.setItem(0,new ItemStack(Items.DIAMOND,23));hold.entry(1).inventory.setItem(0,new ItemStack(Items.EMERALD,31));
        var mob=net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());mob.setPos(p.position());h.getLevel().addFreshEntity(mob);
        h.assertTrue(hold.seats.sit(4,mob)==null,"Stool fixture could not seat mob");
        var cover=new com.sange.tm_wagon.material.WagonMaterial(com.sange.tm_wagon.material.WoodMaterial.OAK,DyeColor.BLUE);
        // Load an installed cover after seating to also exercise releasing a temporarily obstructed passenger.
        var saved=hold.save(h.getLevel().registryAccess(),false);var cloth=new net.minecraft.nbt.CompoundTag();cloth.putBoolean("Installed",true);cloth.put("Material",cover.save());saved.put("Cover",cloth);hold.load(saved,h.getLevel().registryAccess());
        strike(h,w);hold.destroy(true,true);
        var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,w.getBoundingBox().inflate(6)).stream().map(e->e.getItem()).toList();
        java.util.function.ToIntFunction<Item> count=item->drops.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();
        h.assertTrue(count.applyAsInt(Items.CHEST)==1&&count.applyAsInt(Items.DIAMOND)==23&&count.applyAsInt(Items.EMERALD)==0,"Chest contents duplicated or shulker contents spilled");
        h.assertTrue(count.applyAsInt(Items.SHULKER_BOX)==1&&drops.stream().filter(s->s.is(Items.SHULKER_BOX)).findFirst().orElseThrow()
            .get(net.minecraft.core.component.DataComponents.CONTAINER).stream().filter(s->s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum()==31,"Shulker lost stored contents");
        h.assertTrue(count.applyAsInt(WagonContent.STRAW_MAT.get())==1&&count.applyAsInt(WagonContent.STOOL.get())==1
            &&drops.stream().anyMatch(s->s.is(WagonContent.STOOL.get())&&com.sange.tm_wagon.material.WagonMaterial.of(s).equals(stool)),"Mat/stool lost or material changed");
        h.assertTrue(count.applyAsInt(WagonContent.CARGO_COVER.get())==1&&drops.stream().anyMatch(s->s.is(WagonContent.CARGO_COVER.get())&&com.sange.tm_wagon.material.WagonMaterial.of(s).equals(cover)),"Cover lost colour");
        h.assertTrue(mob.isAlive()&&!mob.isPassenger(),"Dismantling removed or retained passenger");h.succeed();
    }
    @GameTest(template="assembly_test")
    public static void hammer_has_iron_axe_properties_and_recipe(GameTestHelper h) {
        var stack=WagonContent.DISMANTLING_HAMMER.get().getDefaultInstance();var axe=Items.IRON_AXE.getDefaultInstance();
        h.assertTrue(stack.getMaxDamage()==axe.getMaxDamage()&&stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState())==axe.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()),"Hammer differs from iron axe durability/mining");
        var enchants=h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for(var id:java.util.List.of(Enchantments.EFFICIENCY,Enchantments.UNBREAKING,Enchantments.MENDING,Enchantments.SHARPNESS,Enchantments.FORTUNE,Enchantments.SILK_TOUCH))
            h.assertTrue(enchants.getOrThrow(id).canEnchant(stack),"Missing axe enchantment: "+id);
        var recipe=(CraftingRecipe)h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("tm_wagon","dismantling_hammer")).orElseThrow().value();
        for(Item log:new Item[]{Items.OAK_LOG,Items.STRIPPED_SPRUCE_LOG,Items.CRIMSON_STEM,Items.STRIPPED_WARPED_STEM}) {
            var input=CraftingInput.of(3,3,java.util.List.of(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.IRON_INGOT),new ItemStack(log),
                new ItemStack(Items.IRON_INGOT),new ItemStack(Items.IRON_INGOT),new ItemStack(log),ItemStack.EMPTY,new ItemStack(Items.STICK),ItemStack.EMPTY));
            h.assertTrue(recipe.matches(input,h.getLevel())&&recipe.assemble(input,h.getLevel().registryAccess()).is(stack.getItem()),"Hammer recipe rejected log: "+log);
        }
        h.succeed();
    }
}
