package gregtech.integration.jei.multiblock;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;
import gregtech.client.utils.TrackedDummyWorld;
import gregtech.common.blocks.BlockWireCoil;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.metatileentities.multi.electric.*;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static gregtech.api.metatileentity.multiblock.MultiblockControllerBase.states;
import static gregtech.api.util.RelativeDirection.*;
import static org.junit.jupiter.api.Assertions.*;

public class MultiblockPreviewEditorTest {

    private static PreviewTestRegistry registry;
    private static PreviewTestController simple;
    private static PreviewTestController limited;
    private static PreviewTestController vertical;

    @BeforeAll
    static void setup() throws ReflectiveOperationException {
        registry = new PreviewTestRegistry();
        simple = registry.register(new PreviewTestController(PreviewTestRegistry.id("simple"),
                self -> FactoryBlockPattern.start().aisle("SX").aisle("XX").setRepeatable(0, 2)
                        .where('S', self.selfPredicate())
                        .where('X', states(Blocks.STONE.getDefaultState(), Blocks.IRON_BLOCK.getDefaultState()))
                        .build()));
        limited = registry.register(new PreviewTestController(PreviewTestRegistry.id("limited"), self -> {
            TraceabilityPredicate metal = states(Blocks.GOLD_BLOCK.getDefaultState(),
                    Blocks.DIAMOND_BLOCK.getDefaultState())
                            .setMinLayerLimited(1).setMaxLayerLimited(1);
            return FactoryBlockPattern.start().aisle("XXX").setRepeatable(1, 2).aisle("SXX")
                    .where('S', self.selfPredicate()).where('X', states(Blocks.STONE.getDefaultState()).or(metal))
                    .build();
        }));
        vertical = registry.register(new PreviewTestController(PreviewTestRegistry.id("vertical"),
                self -> FactoryBlockPattern.start(RIGHT, FRONT, UP).aisle("SX").aisle("XX").setRepeatable(0, 2)
                        .where('S', self.selfPredicate()).where('X', states(Blocks.STONE.getDefaultState())).build()));
    }

    @AfterAll
    static void restore() throws IllegalAccessException {
        if (registry != null) registry.close();
    }

    @Test
    void exactCoreFamiliesAdmitEditingAndUnknownSubclassesDoNot() {
        assertTrue(registry.blastFurnace.supportsPreviewEditing());
        assertTrue(registry.chemicalReactor.supportsPreviewEditing());
        assertTrue(registry.tower.supportsPreviewEditing());
        assertTrue(registry.assembly.supportsPreviewEditing());
        assertFalse(new MetaTileEntityElectricBlastFurnace(PreviewTestRegistry.id("addon_blast")) {}
                .supportsPreviewEditing());
        assertFalse(new MetaTileEntityLargeChemicalReactor(PreviewTestRegistry.id("addon_chemical")) {}
                .supportsPreviewEditing());
        assertFalse(
                new MetaTileEntityDistillationTower(PreviewTestRegistry.id("addon_tower")) {}.supportsPreviewEditing());
        assertFalse(
                new MetaTileEntityAssemblyLine(PreviewTestRegistry.id("addon_assembly")) {}.supportsPreviewEditing());
    }

    @Test
    void actualCoreExamplesValidateWithoutFormingOrMutatingTheirTiles() {
        for (MultiblockControllerBase controller : Arrays.asList(registry.blastFurnace, registry.chemicalReactor,
                registry.tower, registry.assembly)) {
            List<MultiblockShapeInfo> examples = controller.getMatchingShapes();
            List<NBTTagCompound> originalTiles = tileData(examples);
            try (MultiblockPreviewEditor editor = new MultiblockPreviewEditor(examples, TrackedDummyWorld::new)) {
                assertEquals(examples.size(), editor.exampleCount());
                for (int i = 0; i < examples.size(); i++) {
                    editor.selectExample(i);
                    PreviewWorld scene = editor.activate();
                    assertTrue(scene.valid, controller.metaTileEntityId + " example " + i + ": " + scene.error);
                    assertFalse(scene.controller.isStructureFormed());
                    assertNotSame(controller, scene.controller);
                    long blocks = editor.snapshot().blocks.values().stream()
                            .filter(block -> block.state.getBlock() != Blocks.AIR).count();
                    assertEquals(blocks, editor.snapshot().parts().stream().mapToInt(ItemStack::getCount).sum());
                }
            }
            assertEquals(originalTiles, tileData(examples));
        }
        assertEquals(5, registry.chemicalReactor.getMatchingShapes().size(),
                "All five custom coil positions remain available");
    }

    @Test
    void mixedBlastFurnaceCoilsRejectAtomicallyButMatchingCoilsChangeTogether() {
        try (MultiblockPreviewEditor editor = editor(registry.blastFurnace)) {
            PreviewWorld beforeWorld = editor.activate();
            PreviewStructureSnapshot before = editor.snapshot();
            IBlockState copper = MetaBlocks.WIRE_COIL.getState(BlockWireCoil.CoilType.CUPRONICKEL);
            IBlockState kanthal = MetaBlocks.WIRE_COIL.getState(BlockWireCoil.CoilType.KANTHAL);
            BlockPos pos = position(editor, block -> block.state == copper);
            PreviewCandidate candidate = candidate(editor, pos, block -> block.state == kanthal);
            assertFalse(editor.replace(pos, candidate, false));
            assertSame(before, editor.snapshot());
            assertSame(beforeWorld, editor.activate());
            assertFalse(editor.message().isEmpty());
            assertTrue(editor.replace(pos, candidate, true));
            assertEquals(16,
                    editor.snapshot().blocks.values().stream().filter(block -> block.state == kanthal).count());
            assertEquals(0, editor.snapshot().blocks.values().stream().filter(block -> block.state == copper).count());
            assertNotSame(beforeWorld, editor.activate());
            assertTrue(editor.reset());
            assertSame(before, editor.snapshot());
        }
    }

    @Test
    void realHatchLimitsRejectRemovingRequiredEnergyAndAddingTooMany() {
        try (MultiblockPreviewEditor editor = editor(registry.blastFurnace)) {
            editor.activate();
            ItemStack energy = MetaTileEntities.ENERGY_INPUT_HATCH[1].getStackForm();
            BlockPos energyPos = position(editor, block -> ItemStack.areItemsEqual(block.stack(), energy));
            PreviewCandidate casing = candidate(editor, energyPos,
                    block -> block.state.getBlock() == MetaBlocks.METAL_CASING);
            assertEquals(2, editor.snapshot().blocks.values().stream()
                    .filter(block -> ItemStack.areItemsEqual(block.stack(), energy)).count());
            assertTrue(editor.replace(energyPos, casing, false));
            energyPos = position(editor, block -> ItemStack.areItemsEqual(block.stack(), energy));
            casing = candidate(editor, energyPos, block -> block.state.getBlock() == MetaBlocks.METAL_CASING);
            PreviewStructureSnapshot initial = editor.snapshot();
            assertFalse(editor.replace(energyPos, casing, false));
            assertSame(initial, editor.snapshot());
            BlockPos first = position(editor, block -> block.state.getBlock() == MetaBlocks.METAL_CASING);
            PreviewCandidate extra = candidate(editor, first, block -> ItemStack.areItemsEqual(block.stack(), energy));
            assertTrue(editor.replace(first, extra, false));
            BlockPos second = position(editor, block -> block.state.getBlock() == MetaBlocks.METAL_CASING);
            PreviewCandidate third = candidate(editor, second, block -> ItemStack.areItemsEqual(block.stack(), energy));
            PreviewStructureSnapshot two = editor.snapshot();
            assertFalse(editor.replace(second, third, false));
            assertSame(two, editor.snapshot());
        }
    }

    @Test
    void eachCustomExampleKeepsItsOwnEditsAndResetRestoresItsExactBaseline() {
        try (MultiblockPreviewEditor editor = editor(registry.chemicalReactor)) {
            editor.activate();
            PreviewStructureSnapshot baseline = editor.snapshot();
            BlockPos coil = position(editor, block -> block.state.getBlock() == MetaBlocks.WIRE_COIL);
            assertTrue(editor.replace(coil, candidate(editor, coil,
                    block -> block.state == MetaBlocks.WIRE_COIL.getState(BlockWireCoil.CoilType.KANTHAL)), false));
            PreviewStructureSnapshot edited = editor.snapshot();
            editor.selectExample(1);
            assertTrue(editor.activate().valid);
            assertEquals(1,
                    editor.snapshot().blocks.values().stream().filter(
                            block -> block.state == MetaBlocks.WIRE_COIL.getState(BlockWireCoil.CoilType.CUPRONICKEL))
                            .count());
            editor.selectExample(0);
            assertSame(edited, editor.snapshot());
            assertTrue(editor.reset());
            assertSame(baseline, editor.snapshot());
            assertFalse(editor.setGenerated(true), "Nonrepeatable custom examples do not invent dimension controls");
        }
    }

    @Test
    void actualRepeatableCorePatternsRespectBoundsAndRecomputeWholeStructureCounts() {
        for (MultiblockControllerBase controller : Arrays.asList(registry.tower, registry.assembly)) {
            try (MultiblockPreviewEditor editor = editor(controller)) {
                PreviewStructureSnapshot example = editor.snapshot();
                assertTrue(editor.setGenerated(true), editor.message());
                int aisle = editor.variableAisles().get(0);
                int[] bounds = editor.activate().controller.structurePattern.aisleRepetitions[aisle];
                int initialParts = editor.snapshot().parts().stream().mapToInt(ItemStack::getCount).sum();
                assertTrue(editor.setRepetition(aisle, bounds[1]), editor.message());
                assertTrue(editor.snapshot().parts().stream().mapToInt(ItemStack::getCount).sum() > initialParts);
                PreviewStructureSnapshot largest = editor.snapshot();
                assertFalse(editor.setRepetition(aisle, bounds[1] + 1));
                assertFalse(editor.setRepetition(aisle, bounds[0] - 1));
                assertSame(largest, editor.snapshot());
                assertTrue(editor.setGenerated(false));
                assertSame(example, editor.snapshot());
            }
        }
    }

    @Test
    void perLayerLimitsAreCheckedByTheRealPatternMatcher() {
        try (MultiblockPreviewEditor editor = editor(limited)) {
            assertTrue(editor.activate().valid, editor.message());
            BlockPos gold = position(editor, block -> block.state.getBlock() == Blocks.GOLD_BLOCK);
            assertFalse(editor.replace(gold, candidate(editor, gold, block -> block.state.getBlock() == Blocks.STONE),
                    false));
            BlockPos stone = position(editor, block -> block.state.getBlock() == Blocks.STONE);
            assertFalse(editor.replace(stone,
                    candidate(editor, stone, block -> block.state.getBlock() == Blocks.GOLD_BLOCK), false));
            assertTrue(editor.replace(gold,
                    candidate(editor, gold, block -> block.state.getBlock() == Blocks.DIAMOND_BLOCK), true));
            assertTrue(editor.setGenerated(true));
            assertTrue(editor.setRepetition(0, 2));
            assertEquals(3, editor.snapshot().blocks.values().stream()
                    .filter(block -> block.state.getBlock() == Blocks.GOLD_BLOCK).count());
        }
    }

    @Test
    void layerFilteringDoesNotChangeValidationOrCountsAndRepetitionResetsSubstitutions() {
        try (MultiblockPreviewEditor editor = editor(simple)) {
            assertTrue(editor.setGenerated(true));
            BlockPos stone = position(editor, block -> block.state.getBlock() == Blocks.STONE);
            List<ItemStack> beforeParts = editor.snapshot().parts();
            ((TrackedDummyWorld) editor.activate().world).setRenderFilter(pos -> false);
            assertEquals(beforeParts.toString(), editor.snapshot().parts().toString());
            assertTrue(editor.replace(stone,
                    candidate(editor, stone, block -> block.state.getBlock() == Blocks.IRON_BLOCK), true));
            assertTrue(editor.setRepetition(1, 2));
            assertEquals(0, editor.snapshot().blocks.values().stream()
                    .filter(block -> block.state.getBlock() == Blocks.IRON_BLOCK).count());
            assertEquals(6, editor.snapshot().parts().stream().mapToInt(ItemStack::getCount).sum());
        }
    }

    @Test
    void generatedControllerFrameMatchesCoreAndNonDefaultAxesWithoutChangingSourceTiles() {
        for (MultiblockControllerBase controller : Arrays.asList(registry.blastFurnace, registry.chemicalReactor,
                registry.tower, registry.assembly, simple, vertical)) {
            controller.reinitializeStructurePattern();
            int[] repetitions = Arrays.stream(controller.structurePattern.aisleRepetitions)
                    .mapToInt(bounds -> bounds[1]).toArray();
            NBTTagCompound prototypeBefore = controller.writeToNBT(new NBTTagCompound());
            MultiblockShapeInfo shape = new MultiblockShapeInfo(controller.structurePattern.getPreview(repetitions));
            List<NBTTagCompound> sourceBefore = tileData(Arrays.asList(shape));
            PreviewStructureSnapshot generated = PreviewStructureSnapshot.generated(shape, repetitions);
            try (PreviewWorld world = PreviewWorld.open(generated, TrackedDummyWorld::new)) {
                assertTrue(world.valid, controller.metaTileEntityId + ": " + world.error);
                assertEquals(EnumFacing.SOUTH, world.controller.getFrontFacing());
                assertEquals(EnumFacing.NORTH, world.controller.getUpwardsFacing());
                assertArrayEquals(repetitions, world.controller.structurePattern.formedRepetitionCount);
                assertTrue(generated.blocks.entrySet().stream()
                        .filter(entry -> entry.getValue().state.getBlock() != Blocks.AIR)
                        .allMatch(entry -> world.predicates.containsKey(entry.getKey())));
            }
            assertEquals(prototypeBefore, controller.writeToNBT(new NBTTagCompound()));
            assertEquals(sourceBefore, tileData(Arrays.asList(shape)));
        }
    }

    @Test
    void optionalRepeatCannotAcceptOnlyTheZeroLengthSubsetOrExtraDisplayedBlocks() {
        // The old generator points this first-slice controller outwards, away from its positive-Z repeated slices.
        PreviewStructureSnapshot supplied = PreviewStructureSnapshot.capture(simple.getMatchingShapes().get(2));
        try (PreviewWorld rejected = PreviewWorld.open(supplied, TrackedDummyWorld::new)) {
            assertFalse(rejected.valid);
            assertEquals(0, rejected.controller.structurePattern.formedRepetitionCount[1]);
        }
        try (MultiblockPreviewEditor editor = editor(simple)) {
            PreviewStructureSnapshot original = editor.snapshot();
            assertTrue(editor.setGenerated(true));
            assertTrue(editor.setRepetition(1, 2));
            assertArrayEquals(new int[] { 1, 2 }, editor.activate().controller.structurePattern.formedRepetitionCount);
            assertEquals(6, editor.snapshot().parts().stream().mapToInt(ItemStack::getCount).sum());
            Map<BlockPos, PreviewBlock> extra = new LinkedHashMap<>(editor.snapshot().blocks);
            extra.put(new BlockPos(99, 0, 0), PreviewBlock.capture(new BlockInfo(Blocks.STONE.getDefaultState())));
            try (PreviewWorld rejected = PreviewWorld.open(editor.snapshot().withBlocks(extra),
                    TrackedDummyWorld::new)) {
                assertFalse(rejected.valid);
            }
            assertTrue(editor.setGenerated(false));
            assertSame(original, editor.snapshot());
        }
    }

    @Test
    void failedStagingAndRepeatedEditsReleaseOnlyOwnedTiles() {
        List<TrackedDummyWorld> worlds = new ArrayList<>();
        AtomicBoolean failNext = new AtomicBoolean();
        AtomicInteger peak = new AtomicInteger();
        try (MultiblockPreviewEditor editor = new MultiblockPreviewEditor(simple.getMatchingShapes(), () -> {
            int living = (int) worlds.stream()
                    .filter(world -> world.loadedTileEntityList.stream().anyMatch(tile -> !tile.isInvalid())).count();
            peak.set(Math.max(peak.get(), living + 1));
            boolean fail = failNext.getAndSet(false);
            TrackedDummyWorld world = new TrackedDummyWorld() {

                private int writes;

                @Override
                public boolean setBlockState(BlockPos pos, IBlockState state, int flags) {
                    if (fail && ++writes == 2) throw new IllegalStateException("Injected staging failure");
                    return super.setBlockState(pos, state, flags);
                }
            };
            worlds.add(world);
            return world;
        })) {
            PreviewWorld previous = editor.activate();
            PreviewStructureSnapshot original = editor.snapshot();
            BlockPos pos = position(editor, block -> block.state.getBlock() == Blocks.STONE);
            PreviewCandidate iron = candidate(editor, pos, block -> block.state.getBlock() == Blocks.IRON_BLOCK);
            failNext.set(true);
            assertFalse(editor.replace(pos, iron, true));
            assertSame(previous, editor.activate());
            assertSame(original, editor.snapshot());
            for (int i = 0; i < 100; i++) {
                boolean useIron = i % 2 == 0;
                PreviewCandidate choice = candidate(editor, pos,
                        block -> block.state.getBlock() == (useIron ? Blocks.IRON_BLOCK : Blocks.STONE));
                assertTrue(editor.replace(pos, choice, true));
            }
            assertTrue(peak.get() <= 2, "Only displayed and staging worlds may retain live tiles");
            editor.close();
            assertTrue(worlds.stream()
                    .allMatch(world -> world.loadedTileEntityList.stream().allMatch(TileEntity::isInvalid)));
            assertTrue(editor.activate().valid, "Reopen rebuilds from retained layout data");
        }
    }

    @Test
    void snapshotsCloneNbtAndTileIdentityAndRejectUnsupportedTiles() {
        List<MultiblockShapeInfo> shapes = simple.getMatchingShapes();
        PreviewStructureSnapshot snapshot = PreviewStructureSnapshot.capture(shapes.get(0));
        try (PreviewWorld first = PreviewWorld.open(snapshot, TrackedDummyWorld::new);
                PreviewWorld second = PreviewWorld.open(snapshot, TrackedDummyWorld::new)) {
            assertNotSame(first.controller, second.controller);
            assertNotSame(first.controller.structurePattern, second.controller.structurePattern);
            assertNotSame(first.controller.getHolder(), second.controller.getHolder());
            assertFalse(first.controller.isStructureFormed());
            first.controller.setPaintingColor(0x123456);
            assertNotEquals(first.controller.getPaintingColor(), second.controller.getPaintingColor());
        }
        assertThrows(IllegalArgumentException.class,
                () -> PreviewBlock.capture(new BlockInfo(Blocks.CHEST.getDefaultState(), new TileEntityChest())));
    }

    @Test
    void generatedSizeQueryRejectsMalformedVectorsAndAllowsOptionalZeroSlices() {
        simple.reinitializeStructurePattern();
        BlockPattern pattern = simple.structurePattern;
        assertEquals(2, pattern.getPreviewVolume(new int[] { 1, 0 }));
        assertEquals(6, pattern.getPreviewVolume(new int[] { 1, 2 }));
        assertEquals(-1, pattern.getPreviewVolume(null));
        assertEquals(-1, pattern.getPreviewVolume(new int[] { 1 }));
        assertEquals(-1, pattern.getPreviewVolume(new int[] { 1, -1 }));
        assertEquals(-1, pattern.getPreviewVolume(new int[] { 1, Integer.MAX_VALUE }));
    }

    @Test
    void aBrokenCloneFactoryCannotReparentItsPrototype() {
        PreviewTestController broken = new PreviewTestController(PreviewTestRegistry.id("broken_factory"),
                self -> null) {

            @Override
            public MetaTileEntity createMetaTileEntity(IGregTechTileEntity holder) {
                return this;
            }
        };
        assertNull(broken.getHolder());
        assertThrows(IllegalArgumentException.class,
                () -> PreviewTileEntityHolder.copyOf(broken, new NBTTagCompound()));
        assertNull(broken.getHolder());
    }

    private static MultiblockPreviewEditor editor(MultiblockControllerBase controller) {
        return new MultiblockPreviewEditor(controller.getMatchingShapes(), TrackedDummyWorld::new);
    }

    private static BlockPos position(MultiblockPreviewEditor editor, Predicate<PreviewBlock> filter) {
        return editor.snapshot().blocks.entrySet().stream().filter(entry -> filter.test(entry.getValue()))
                .map(Map.Entry::getKey).findFirst().orElseThrow(AssertionError::new);
    }

    private static PreviewCandidate candidate(MultiblockPreviewEditor editor, BlockPos pos,
                                              Predicate<PreviewBlock> filter) {
        return editor.candidates(pos).stream().filter(choice -> filter.test(choice.block)).findFirst()
                .orElseThrow(AssertionError::new);
    }

    private static List<NBTTagCompound> tileData(List<MultiblockShapeInfo> shapes) {
        return shapes.stream().flatMap(shape -> Arrays.stream(shape.getBlocks())).flatMap(Arrays::stream)
                .flatMap(Arrays::stream)
                .filter(info -> info.getTileEntity() instanceof MetaTileEntityHolder)
                .map(info -> ((MetaTileEntityHolder) info.getTileEntity()).getMetaTileEntity()
                        .writeToNBT(new NBTTagCompound()))
                .collect(Collectors.toList());
    }
}
