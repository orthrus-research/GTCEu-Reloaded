package gregtech.api.pattern;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.util.GTUtility;
import gregtech.api.util.RelativeDirection;
import gregtech.common.blocks.BlockWireCoil;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityElectricBlastFurnace;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeChemicalReactor;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityDualHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityEnergyHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMufflerHatch;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static gregtech.api.metatileentity.multiblock.MultiblockControllerBase.abilities;
import static org.junit.jupiter.api.Assertions.*;

class DualHatchPatternTest {

    private static final List<MultiblockAbility<?>> added = new ArrayList<>();
    private static IHeatingCoilBlockStats oldCoil;

    @BeforeAll
    static void setup() {
        Bootstrap.perform();
        for (var ability : new MultiblockAbility<?>[] { MultiblockAbility.IMPORT_ITEMS, MultiblockAbility.IMPORT_FLUIDS,
                MultiblockAbility.EXPORT_ITEMS, MultiblockAbility.EXPORT_FLUIDS, MultiblockAbility.INPUT_ENERGY,
                MultiblockAbility.MAINTENANCE_HATCH, MultiblockAbility.MUFFLER_HATCH }) {
            if (!MultiblockAbility.REGISTRY.containsKey(ability)) {
                MultiblockAbility.REGISTRY.put(ability, Collections.emptyList());
                added.add(ability);
            }
        }
        oldCoil = GregTechAPI.HEATING_COILS.put(Blocks.GOLD_BLOCK.getDefaultState(),
                BlockWireCoil.CoilType.CUPRONICKEL);
    }

    @AfterAll
    static void restore() {
        for (var ability : added) MultiblockAbility.REGISTRY.remove(ability);
        if (oldCoil == null) GregTechAPI.HEATING_COILS.remove(Blocks.GOLD_BLOCK.getDefaultState());
        else GregTechAPI.HEATING_COILS.put(Blocks.GOLD_BLOCK.getDefaultState(), oldCoil);
    }

    @Test
    void oneCombinedPartCountsTowardBothMinimumsAndCannotReplaceAnUnrelatedAbility() {
        TraceabilityPredicate item = abilities(MultiblockAbility.IMPORT_ITEMS).setMinGlobalLimited(1);
        TraceabilityPredicate fluid = abilities(MultiblockAbility.IMPORT_FLUIDS).setMinGlobalLimited(1);
        BlockWorldState state = state(dual(false));
        assertTrue(item.or(fluid).test(state));
        assertEquals(1, state.globalCount.get(item.limited.get(0)));
        assertEquals(1, state.globalCount.get(fluid.limited.get(0)));
        assertFalse(abilities(MultiblockAbility.MUFFLER_HATCH).test(state));
    }

    @Test
    void globalMaximumCannotBeBypassedByAnUncappedFluidOrCommonAlternative() {
        for (boolean common : new boolean[] { false, true }) {
            TraceabilityPredicate item = abilities(MultiblockAbility.IMPORT_ITEMS).setMaxGlobalLimited(1);
            TraceabilityPredicate other = common ? new TraceabilityPredicate(state -> true) :
                    abilities(MultiblockAbility.IMPORT_FLUIDS);
            TraceabilityPredicate combined = item.or(other);
            BlockWorldState state = state(dual(false));
            assertTrue(combined.test(state));
            assertFalse(combined.test(state));
            assertEquals(1, state.globalCount.get(item.limited.get(0)),
                    "Rejected attempts must restore their counters");
            assertTrue(state.error instanceof TraceabilityPredicate.SinglePredicateError);
            assertEquals(0, ((TraceabilityPredicate.SinglePredicateError) state.error).type);
            assertFalse(combined.test(state));
            assertEquals(1, state.globalCount.get(item.limited.get(0)));
        }
    }

    @Test
    void layerMaximumAndFirstRelevantErrorSurviveOtherMatchingArms() {
        TraceabilityPredicate first = abilities(MultiblockAbility.IMPORT_ITEMS).setMaxLayerLimited(0);
        TraceabilityPredicate second = abilities(MultiblockAbility.IMPORT_FLUIDS).setMaxGlobalLimited(0);
        BlockWorldState state = state(dual(false));
        assertFalse(first.or(second).or(new TraceabilityPredicate(s -> true)).test(state));
        assertEquals(2, ((TraceabilityPredicate.SinglePredicateError) state.error).type);
        assertSame(first.limited.get(0), ((TraceabilityPredicate.SinglePredicateError) state.error).predicate);
        assertTrue(state.globalCount.isEmpty());
        assertTrue(state.layerCount.isEmpty());
    }

    @Test
    void nonmatchingStaleOverflowDoesNotRejectThePartAndPredicatesAreNeverReplayed() {
        AtomicInteger calls = new AtomicInteger();
        TraceabilityPredicate nonmatching = new TraceabilityPredicate(state -> {
            calls.incrementAndGet();
            return false;
        })
                .setMaxGlobalLimited(0);
        BlockWorldState state = state(dual(false));
        state.globalCount.put(nonmatching.limited.get(0), 5);
        assertTrue(nonmatching.or(abilities(MultiblockAbility.IMPORT_FLUIDS)).test(state));
        assertEquals(1, calls.get());
        AtomicInteger matched = new AtomicInteger();
        TraceabilityPredicate both = new TraceabilityPredicate(s -> {
            matched.incrementAndGet();
            return true;
        })
                .setMaxGlobalLimited(1).setMaxLayerLimited(1);
        assertTrue(both.test(state(dual(false))));
        assertEquals(2, matched.get(), "The legacy global/layer calls remain exactly two, with no diagnostic replay");
    }

    @Test
    void ordinaryPartsKeepLegacyAlternativePredicateBehavior() {
        MetaTileEntityItemBus ordinary = new MetaTileEntityItemBus(GTUtility.gregtechId("ordinary_pattern"), 1, false);
        assertFalse(ordinary.requiresAllAbilityLimits());
        TraceabilityPredicate predicate = abilities(MultiblockAbility.IMPORT_ITEMS).setMaxGlobalLimited(0)
                .or(new TraceabilityPredicate(state -> true));
        assertTrue(predicate.test(state(ordinary)));
    }

    @Test
    void actualEbfPatternAcceptsCombinedChannelsButStillRequiresItsMuffler() throws Exception {
        MetaTileEntityElectricBlastFurnace controller = new MetaTileEntityElectricBlastFurnace(
                GTUtility.gregtechId("dual_pattern_ebf")) {

            @Override
            public void reinitializeStructurePattern() {}

            @Override
            protected IBlockState getCasingState() {
                return Blocks.IRON_BLOCK.getDefaultState();
            }

            @Override
            public boolean hasMaintenanceMechanics() {
                return false;
            }
        };
        String[][] layout = { { "XXX", "CCC", "CCC", "XXX" }, { "XXX", "C#C", "C#C", "XMX" },
                { "XSX", "CCC", "CCC", "XXX" } };
        DualPatternTestWorld world = scene(controller, layout, 1, 0, 2, false);
        BlockPattern pattern = pattern(controller);
        assertNotNull(pattern.checkPatternFastAt(world, BlockPos.ORIGIN, EnumFacing.NORTH, EnumFacing.NORTH, false));
        world.put(position(1, 3, 1, 1, 0, 2), dual(false));
        pattern.clearCache();
        assertNull(pattern.checkPatternFastAt(world, BlockPos.ORIGIN, EnumFacing.NORTH, EnumFacing.NORTH, false));
    }

    @Test
    void actualChemicalPatternAcceptsCombinedChannelsWithoutBypassingTheCoilLimit() throws Exception {
        MetaTileEntityLargeChemicalReactor controller = new MetaTileEntityLargeChemicalReactor(
                GTUtility.gregtechId("dual_pattern_chemical")) {

            @Override
            public void reinitializeStructurePattern() {}

            @Override
            protected IBlockState getCasingState() {
                return Blocks.IRON_BLOCK.getDefaultState();
            }

            @Override
            protected IBlockState getPipeCasingState() {
                return Blocks.DIAMOND_BLOCK.getDefaultState();
            }

            @Override
            public boolean hasMaintenanceMechanics() {
                return false;
            }
        };
        String[][] layout = { { "XXX", "XCX", "XXX" }, { "XCX", "CPC", "XCX" }, { "XXX", "XSX", "XXX" } };
        DualPatternTestWorld world = scene(controller, layout, 1, 1, 2, true);
        BlockPattern pattern = pattern(controller);
        assertNotNull(pattern.checkPatternFastAt(world, BlockPos.ORIGIN, EnumFacing.NORTH, EnumFacing.NORTH, false));
        world.put(position(1, 0, 1, 1, 1, 2), Blocks.GOLD_BLOCK.getDefaultState());
        pattern.clearCache();
        assertNull(pattern.checkPatternFastAt(world, BlockPos.ORIGIN, EnumFacing.NORTH, EnumFacing.NORTH, false));
    }

    private static BlockPattern pattern(RecipeMapMultiblockController controller) throws Exception {
        Method method = controller.getClass().getSuperclass().getDeclaredMethod("createStructurePattern");
        method.setAccessible(true);
        return (BlockPattern) method.invoke(controller);
    }

    private static DualPatternTestWorld scene(RecipeMapMultiblockController controller, String[][] layout,
                                              int cx, int cy, int cz, boolean chemical) {
        DualPatternTestWorld world = new DualPatternTestWorld();
        for (int z = 0; z < layout.length; z++) for (int y = 0; y < layout[z].length; y++) {
            for (int x = 0; x < layout[z][y].length(); x++) {
                char symbol = layout[z][y].charAt(x);
                BlockPos pos = position(x, y, z, cx, cy, cz);
                if (symbol == 'S') world.put(pos, controller);
                else if (symbol == 'M')
                    world.put(pos, new MetaTileEntityMufflerHatch(GTUtility.gregtechId("dual_pattern_muffler"), 1));
                else world.put(pos,
                        symbol == '#' ? Blocks.AIR.getDefaultState() :
                                symbol == 'P' ? Blocks.DIAMOND_BLOCK.getDefaultState() :
                                        symbol == 'C' && (!chemical || z == 0) ? Blocks.GOLD_BLOCK.getDefaultState() :
                                                Blocks.IRON_BLOCK.getDefaultState());
            }
        }
        world.put(position(0, 0, 0, cx, cy, cz),
                new MetaTileEntityEnergyHatch(GTUtility.gregtechId("dual_pattern_energy"), 1, 2, false));
        world.put(position(1, 0, 0, cx, cy, cz), dual(false));
        world.put(position(2, 0, 0, cx, cy, cz), dual(true));
        return world;
    }

    private static BlockPos position(int x, int y, int z, int cx, int cy, int cz) {
        return RelativeDirection.setActualRelativeOffset(x - cx, y - cy, z - cz, EnumFacing.NORTH, EnumFacing.NORTH,
                false,
                new RelativeDirection[] { RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK });
    }

    private static MetaTileEntityDualHatch dual(boolean output) {
        return new MetaTileEntityDualHatch(GTUtility.gregtechId("dual_pattern_" + output), GTValues.LuV, output);
    }

    private static BlockWorldState state(MetaTileEntity part) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder() {

            {
                setRawMetaTileEntity(part);
            }
        };
        BlockWorldState state = new BlockWorldState() {

            @Override
            public TileEntity getTileEntity() {
                return holder;
            }
        };
        state.update(null, BlockPos.ORIGIN, new PatternMatchContext(), new HashMap<>(), new HashMap<>(), null);
        return state;
    }
}
