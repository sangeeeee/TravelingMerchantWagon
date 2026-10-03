package com.sange.tm_wagon.assembly;

import com.sange.tm_wagon.compat.StructureCollision;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tm_wagon")
@PrefixGameTestTemplate(false)
public class SableAssemblyGuardGameTests {
    private static void run(GameTestHelper h,int scenario) {
        if(StructureCollision.available())SableAssemblyGuardFixtures.run(h,scenario);else h.succeed();
    }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_multiple_cells_of_one_component_drop_once(GameTestHelper h) { run(h,0); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_several_components_drop_once_and_keep_body(GameTestHelper h) { run(h,1); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_body_physicalization_drops_dependencies_and_cargo(GameTestHelper h) { run(h,2); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_frame_physicalization_drops_whole_assembly_once(GameTestHelper h) { run(h,3); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_frame_item_on_structure_consumes_and_returns_once(GameTestHelper h) { run(h,4); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_imported_frame_is_cleaned_after_nbt_load(GameTestHelper h) { run(h,5); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_physicalizing_only_wagon_cells_handles_empty_selection(GameTestHelper h) { run(h,6); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_physicalization_honours_disabled_block_drops(GameTestHelper h) { run(h,7); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_raw_import_of_multiple_proxy_cells_drops_one_component(GameTestHelper h) { run(h,8); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_roof_only_selection_keeps_body_and_components(GameTestHelper h) { run(h,9); }
    @GameTest(template="assembly_test",timeoutTicks=100)
    public static void sable_cargo_only_selection_drops_chest_contents_once(GameTestHelper h) { run(h,10); }
}
