package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.compat.StructureCollision;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class SableCompatGameTests {
    private static void run(GameTestHelper h,int scenario) {
        if(StructureCollision.available())SableCompatFixtures.run(h,scenario);
        h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_rotated_platform_support(GameTestHelper h) { run(h,0); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_structure_blocks_driving(GameTestHelper h) { run(h,1); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_moving_platform_carries_wagon(GameTestHelper h) { run(h,2); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_moving_wall_pushes_wagon(GameTestHelper h) { run(h,3); }    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_rotating_platform_carries_position_and_heading(GameTestHelper h) { run(h,4); }
}
