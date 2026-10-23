package gregtech.integration.jei.multiblock;

import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.util.BlockInfo;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PreviewStructureSnapshot {

    static final int MAX_BLOCKS = 32768;
    final Map<BlockPos, PreviewBlock> blocks;
    private final List<ItemStack> parts;
    private final int[] repetitions;

    PreviewStructureSnapshot(Map<BlockPos, PreviewBlock> blocks) {
        this(blocks, null);
    }

    private PreviewStructureSnapshot(Map<BlockPos, PreviewBlock> blocks, int[] repetitions) {
        if (blocks.isEmpty() || blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("Preview size limit");
        this.blocks = Collections.unmodifiableMap(new LinkedHashMap<>(blocks));
        this.repetitions = repetitions == null ? null : repetitions.clone();
        List<ItemStack> stacks = new ArrayList<>();
        blocks.values().stream().filter(PreviewBlock::isController).forEach(block -> stacks.add(block.stack()));
        blocks.values().stream().filter(block -> !block.isController()).forEach(block -> stacks.add(block.stack()));
        this.parts = PreviewParts.count(stacks);
    }

    static PreviewStructureSnapshot capture(MultiblockShapeInfo shape) {
        Map<BlockPos, PreviewBlock> result = new LinkedHashMap<>();
        BlockInfo[][][] blocks = shape.getBlocks();
        for (int x = 0; x < blocks.length; x++) {
            for (int y = 0; y < blocks[x].length; y++) {
                for (int z = 0; z < blocks[x][y].length; z++) {
                    if (result.size() >= MAX_BLOCKS) throw new IllegalArgumentException("Preview size limit");
                    result.put(new BlockPos(x, y, z), PreviewBlock.capture(blocks[x][y][z]));
                }
            }
        }
        return new PreviewStructureSnapshot(result);
    }

    static PreviewStructureSnapshot generated(MultiblockShapeInfo shape, int[] repetitions) {
        PreviewStructureSnapshot captured = capture(shape);
        Map<BlockPos, PreviewBlock> blocks = new LinkedHashMap<>();
        captured.blocks.forEach((pos, block) -> blocks.put(pos, block.withGeneratedControllerOrientation()));
        return new PreviewStructureSnapshot(blocks, repetitions);
    }

    PreviewStructureSnapshot withBlocks(Map<BlockPos, PreviewBlock> blocks) {
        return new PreviewStructureSnapshot(blocks, repetitions);
    }

    boolean matchesRepetitions(int[] matched) {
        return repetitions == null || Arrays.equals(repetitions, matched);
    }

    List<ItemStack> parts() {
        return PreviewParts.page(parts, 0, parts.size());
    }
}
