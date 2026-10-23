package gregtech.integration.jei.multiblock;

import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTLog;
import gregtech.api.util.ItemStackHashStrategy;

import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Recipe-local editor. A proposed layout becomes visible only after full, independent pattern validation. */
final class MultiblockPreviewEditor implements AutoCloseable {

    private static final int MAX_EXAMPLES = 128;
    private static final int MAX_CANDIDATES = 512;
    private final List<PreviewStructureSnapshot> originals = new ArrayList<>();
    private final List<PreviewStructureSnapshot> examples = new ArrayList<>();
    private final Supplier<? extends World> worldFactory;
    private PreviewStructureSnapshot generatedOriginal;
    private PreviewStructureSnapshot generatedLayout;
    private PreviewWorld active;
    private int exampleIndex;
    private boolean generated;
    private int[] repetitions;
    private String message = "";

    MultiblockPreviewEditor(List<MultiblockShapeInfo> shapes, Supplier<? extends World> worldFactory) {
        if (shapes.isEmpty() || shapes.size() > MAX_EXAMPLES) throw new IllegalArgumentException("Preview page limit");
        this.worldFactory = worldFactory;
        for (MultiblockShapeInfo shape : shapes) originals.add(PreviewStructureSnapshot.capture(shape));
        examples.addAll(originals);
    }

    PreviewWorld activate() {
        if (active == null) {
            this.active = PreviewWorld.open(snapshot(), worldFactory);
            this.message = active.valid ? "" : active.error;
        }
        return active;
    }

    PreviewStructureSnapshot snapshot() {
        return generated ? generatedLayout : examples.get(exampleIndex);
    }

    List<ItemStack> ingredients() {
        Set<ItemStack> result = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAllButCount());
        for (PreviewStructureSnapshot original : originals) {
            for (ItemStack stack : original.parts()) {
                stack.setCount(1);
                result.add(stack);
            }
        }
        return new ArrayList<>(result);
    }

    List<PreviewCandidate> candidates(BlockPos pos) {
        PreviewWorld scene = activate();
        PreviewBlock selected = snapshot().blocks.get(pos);
        TraceabilityPredicate predicate = scene.predicates.get(pos);
        if (!scene.valid || selected == null || selected.isController() || predicate == null) {
            return Collections.emptyList();
        }
        List<TraceabilityPredicate.SimplePredicate> choices = new ArrayList<>(predicate.common);
        choices.addAll(predicate.limited);
        List<PreviewCandidate> result = new ArrayList<>();
        try {
            for (TraceabilityPredicate.SimplePredicate choice : choices) {
                if (choice.candidates == null) continue;
                BlockInfo[] infos = choice.candidates.get();
                if (infos == null) continue;
                if (result.size() + infos.length > MAX_CANDIDATES) {
                    this.message = I18n.format("gregtech.multiblock.editor.limit");
                    return Collections.emptyList();
                }
                for (BlockInfo info : infos) {
                    if (info == null || info.getBlockState().getBlock() == Blocks.AIR) continue;
                    PreviewBlock block = PreviewBlock.capture(info);
                    if (!block.isController() && !block.stack().isEmpty())
                        result.add(new PreviewCandidate(choice, block));
                }
            }
        } catch (RuntimeException exception) {
            this.message = I18n.format("gregtech.multiblock.editor.unsupported");
            return Collections.emptyList();
        }
        return result;
    }

    boolean replace(BlockPos pos, PreviewCandidate candidate, boolean matchingParts) {
        PreviewWorld scene = activate();
        PreviewBlock old = snapshot().blocks.get(pos);
        TraceabilityPredicate selectedPredicate = scene.predicates.get(pos);
        if (!scene.valid || old == null || old.isController() || !accepts(selectedPredicate, candidate.predicate)) {
            return false;
        }
        Map<BlockPos, PreviewBlock> proposal = new LinkedHashMap<>(snapshot().blocks);
        for (Map.Entry<BlockPos, PreviewBlock> entry : snapshot().blocks.entrySet()) {
            boolean samePosition = entry.getKey().equals(pos);
            boolean sameGroup = matchingParts && old.samePart(entry.getValue()) &&
                    accepts(scene.predicates.get(entry.getKey()), candidate.predicate);
            if (samePosition || sameGroup) proposal.put(entry.getKey(), candidate.block.withFacingOf(entry.getValue()));
        }
        return accept(snapshot().withBlocks(proposal));
    }

    private static boolean accepts(TraceabilityPredicate parent, TraceabilityPredicate.SimplePredicate choice) {
        return parent != null && (parent.common.contains(choice) || parent.limited.contains(choice));
    }

    private boolean accept(PreviewStructureSnapshot proposal) {
        PreviewWorld staged = null;
        try {
            staged = PreviewWorld.open(proposal, worldFactory);
            if (!staged.valid) {
                this.message = staged.error;
                return false;
            }
            PreviewWorld previous = active;
            this.active = staged;
            staged = null;
            if (generated) this.generatedLayout = proposal;
            else this.examples.set(exampleIndex, proposal);
            this.message = "";
            if (previous != null) previous.close();
            return true;
        } catch (RuntimeException exception) {
            this.message = I18n.format("gregtech.multiblock.editor.failed");
            GTLog.logger.warn("Could not prepare an isolated multiblock preview", exception);
            return false;
        } finally {
            if (staged != null) staged.close();
        }
    }

    boolean reset() {
        return accept(generated ? generatedOriginal : originals.get(exampleIndex));
    }

    void selectExample(int index) {
        if (index < 0 || index >= examples.size()) return;
        close();
        this.exampleIndex = index;
        this.generated = false;
    }

    boolean setGenerated(boolean enabled) {
        if (enabled == generated) return true;
        if (!enabled) {
            selectExample(exampleIndex);
            return true;
        }
        if (!activate().valid || variableAisles().isEmpty()) return false;
        if (generatedLayout == null) {
            BlockPattern pattern = active.controller.structurePattern;
            int[] counts = new int[pattern.aisleRepetitions.length];
            for (int i = 0; i < counts.length; i++) counts[i] = pattern.aisleRepetitions[i][0];
            PreviewStructureSnapshot proposal = generate(pattern, counts);
            if (proposal == null) return false;
            // Keep the example untouched while the generated mode is validated.
            this.generatedLayout = proposal;
            this.generatedOriginal = proposal;
            this.repetitions = counts;
        }
        this.generated = true;
        if (accept(generatedLayout)) return true;
        this.generated = false;
        return false;
    }

    boolean setRepetition(int aisle, int count) {
        if (!generated || !activate().valid || aisle < 0 || aisle >= repetitions.length) return false;
        int[] proposedCounts = repetitions.clone();
        proposedCounts[aisle] = count;
        PreviewStructureSnapshot proposal = generate(active.controller.structurePattern, proposedCounts);
        if (proposal == null || !accept(proposal)) return false;
        this.repetitions = proposedCounts;
        this.generatedOriginal = proposal;
        return true;
    }

    private PreviewStructureSnapshot generate(BlockPattern pattern, int[] counts) {
        long volume = pattern.getPreviewVolume(counts);
        if (volume <= 0 || volume > PreviewStructureSnapshot.MAX_BLOCKS) {
            this.message = I18n.format("gregtech.multiblock.editor.limit");
            return null;
        }
        try {
            return PreviewStructureSnapshot.generated(new MultiblockShapeInfo(pattern.getPreview(counts)), counts);
        } catch (RuntimeException exception) {
            this.message = I18n.format("gregtech.multiblock.editor.failed");
            return null;
        }
    }

    List<Integer> variableAisles() {
        PreviewWorld scene = activate();
        if (!scene.valid) return Collections.emptyList();
        List<Integer> result = new ArrayList<>();
        int[][] bounds = scene.controller.structurePattern.aisleRepetitions;
        for (int i = 0; i < bounds.length; i++) if (bounds[i][0] != bounds[i][1]) result.add(i);
        return result;
    }

    int repetition(int aisle) {
        return repetitions == null ? 0 : repetitions[aisle];
    }

    int exampleIndex() {
        return exampleIndex;
    }

    int exampleCount() {
        return examples.size();
    }

    boolean isGenerated() {
        return generated;
    }

    String message() {
        return message;
    }

    @Override
    public void close() {
        if (active != null) active.close();
        this.active = null;
    }
}
