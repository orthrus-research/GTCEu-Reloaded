package gregtech.api.metatileentity.multiblock;

import gregtech.GameStateExtension;
import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.PatternError;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;
import gregtech.client.utils.TrackedDummyWorld;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Builds each example a registered multiblock controller shows in JEI and the in-world preview, its matching shapes,
 * in a world of its own, and checks that the controller placed there matches its own structure pattern and takes
 * every shown block into the structure: an example the pattern refuses is one a player would build from the preview
 * and never see form.
 */
@ExtendWith(GameStateExtension.class)
class MatchingShapesFormTest {

    /**
     * Controllers whose examples are known not to form, with the reason. Each must still fail, so an entry is
     * removed when the controller is fixed.
     */
    private static final Map<String, String> KNOWN_UNFORMED = Map.of();

    static Stream<Named<MultiblockControllerBase>> controllers() {
        List<MultiblockControllerBase> controllers = new ArrayList<>();
        for (MetaTileEntity machine : GregTechAPI.mteManager.getRegistry(GTValues.MODID)) {
            if (machine instanceof MultiblockControllerBase controller) controllers.add(controller);
        }
        assertFalse(controllers.isEmpty(), "no multiblock controller is registered");
        controllers.sort(Comparator.comparing(controller -> controller.metaTileEntityId.toString()));
        return controllers.stream().map(controller -> Named.of(controller.metaTileEntityId.toString(), controller));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("controllers")
    void everyExampleFormsItsOwnPattern(MultiblockControllerBase sample) {
        List<MultiblockShapeInfo> examples = sample.getMatchingShapes();
        assertFalse(examples.isEmpty(), () -> sample.metaTileEntityId + " shows no example");
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < examples.size(); i++) {
            String failure = check(examples.get(i));
            if (failure != null) failures.add("example " + i + ": " + failure);
        }
        String known = KNOWN_UNFORMED.get(sample.metaTileEntityId.toString());
        if (known != null) {
            assertFalse(failures.isEmpty(), () -> sample.metaTileEntityId + " forms every example now (" + known +
                    "): remove it from KNOWN_UNFORMED");
            return;
        }
        assertTrue(failures.isEmpty(), () -> sample.metaTileEntityId + " " + String.join("; ", failures));
    }

    /** @return why the example does not match its controller's pattern, or {@code null} when it does */
    private static String check(MultiblockShapeInfo example) {
        TrackedDummyWorld world = new TrackedDummyWorld();
        Map<BlockPos, BlockInfo> blocks = new LinkedHashMap<>();
        MultiblockControllerBase controller = null;
        BlockInfo[][][] infos = example.getBlocks();
        for (int x = 0; x < infos.length; x++) {
            for (int y = 0; y < infos[x].length; y++) {
                for (int z = 0; z < infos[x][y].length; z++) {
                    BlockInfo info = infos[x][y][z];
                    if (info == null) continue;
                    blocks.put(new BlockPos(x, y, z), info);
                    if (info.getTileEntity() instanceof MetaTileEntityHolder holder &&
                            holder.getMetaTileEntity() instanceof MultiblockControllerBase placed) {
                        if (controller != null) return "it holds more than one controller";
                        controller = placed;
                    }
                }
            }
        }
        if (controller == null) return "it holds no controller";
        world.addBlocks(blocks);

        controller.reinitializeStructurePattern();
        if (controller.structurePattern == null) return "its controller has no pattern";
        controller.structurePattern.clearCache();
        boolean formed = controller.structurePattern.checkPatternFastAt(world, controller.getPos(),
                controller.getFrontFacing().getOpposite(), controller.getUpwardsFacing(),
                controller.allowsFlip()) != null;
        if (formed) {
            // every block the example shows must be one the pattern took part of the structure
            for (Map.Entry<BlockPos, BlockInfo> block : blocks.entrySet()) {
                if (block.getValue().getBlockState().getBlock() != Blocks.AIR &&
                        !controller.structurePattern.cache.containsKey(block.getKey().toLong())) {
                    return "the pattern does not take the block at " + block.getKey() + " into the structure";
                }
            }
            return null;
        }
        PatternError error = controller.structurePattern.getError();
        if (error == null) return "the pattern does not match";
        return describe(error) + " at " + error.getPos() + " (controller at " + controller.getPos() + " facing " +
                controller.getFrontFacing() + ')';
    }

    private static String describe(PatternError error) {
        String expected = error.getCandidates().stream().flatMap(List::stream).limit(4)
                .map(stack -> stack.getTranslationKey()).collect(Collectors.joining(", "));
        if (error instanceof TraceabilityPredicate.SinglePredicateError limit) {
            String[] kinds = { "at most %d", "at least %d", "at most %d per layer", "at least %d per layer" };
            int count = switch (limit.type) {
                case 0 -> limit.predicate.maxGlobalCount;
                case 1 -> limit.predicate.minGlobalCount;
                case 2 -> limit.predicate.maxLayerCount;
                default -> limit.predicate.minLayerCount;
            };
            return "needs " + String.format(kinds[limit.type], count) + " of " + expected;
        }
        return "needs one of " + expected;
    }
}
