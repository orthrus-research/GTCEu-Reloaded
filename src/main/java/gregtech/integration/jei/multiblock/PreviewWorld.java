package gregtech.integration.jei.multiblock;

import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.PatternError;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTLog;
import gregtech.client.utils.TrackedDummyWorld;

import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Owns only the tiles it creates. No ticking or controller formation callbacks run here. */
final class PreviewWorld implements AutoCloseable {

    final World world;
    final Map<BlockPos, TraceabilityPredicate> predicates = new HashMap<>();
    private final Map<BlockPos, TileEntity> tiles = new HashMap<>();
    MultiblockControllerBase controller;
    String error = "";
    boolean valid;
    private boolean closed;

    private PreviewWorld(World world) {
        this.world = world;
    }

    static PreviewWorld open(PreviewStructureSnapshot snapshot, Supplier<? extends World> factory) {
        PreviewWorld result = new PreviewWorld(factory.get());
        try {
            for (Map.Entry<BlockPos, PreviewBlock> entry : snapshot.blocks.entrySet()) {
                BlockInfo block = entry.getValue().instantiate();
                // Track before applying, so partially installed tiles are disposed on failure too.
                if (block.getTileEntity() != null) result.tiles.put(entry.getKey(), block.getTileEntity());
                block.apply(result.world, entry.getKey());
                if (block.getTileEntity() instanceof MetaTileEntityHolder holder &&
                        holder.getMetaTileEntity() instanceof MultiblockControllerBase controller) {
                    if (result.controller != null) throw new IllegalArgumentException("Multiple preview controllers");
                    result.controller = controller;
                }
            }
            result.validate(snapshot);
            return result;
        } catch (RuntimeException exception) {
            result.close();
            throw exception;
        }
    }

    private void validate(PreviewStructureSnapshot snapshot) {
        if (controller == null || !controller.supportsPreviewEditing()) {
            error = I18n.format("gregtech.multiblock.editor.unsupported");
            return;
        }
        controller.reinitializeStructurePattern();
        if (controller.structurePattern == null) {
            error = I18n.format("gregtech.multiblock.editor.unsupported");
            return;
        }
        controller.structurePattern.clearCache();
        valid = controller.structurePattern.checkPatternFastAt(world, controller.getPos(),
                controller.getFrontFacing().getOpposite(), controller.getUpwardsFacing(), controller.allowsFlip()) !=
                null;
        if (valid) {
            valid = snapshot.matchesRepetitions(controller.structurePattern.formedRepetitionCount) &&
                    snapshot.blocks.entrySet().stream()
                            .filter(entry -> entry.getValue().state.getBlock() != Blocks.AIR)
                            .allMatch(entry -> controller.structurePattern.cache.containsKey(entry.getKey().toLong()));
        }
        if (valid) {
            controller.structurePattern.cache.forEach(
                    (pos, info) -> predicates.put(BlockPos.fromLong(pos), (TraceabilityPredicate) info.getInfo()));
        } else {
            PatternError failure = controller.structurePattern.getError();
            error = failure == null ? I18n.format("gregtech.multiblock.editor.invalid") : failure.getErrorInfo();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (world instanceof TrackedDummyWorld tracked) {
            tracked.setRenderFilter(null);
            tracked.renderedBlocks.clear();
        }
        predicates.clear();
        if (controller != null) {
            if (controller.structurePattern != null) controller.structurePattern.clearCache();
            controller.structurePattern = null;
            controller = null;
        }
        tiles.forEach((pos, tile) -> {
            try {
                if (!tile.isInvalid()) tile.invalidate();
            } catch (RuntimeException exception) {
                GTLog.logger.warn("Could not invalidate an owned preview tile", exception);
            } finally {
                try {
                    world.removeTileEntity(pos);
                } catch (RuntimeException exception) {
                    GTLog.logger.warn("Could not remove an owned preview tile", exception);
                }
            }
        });
        tiles.clear();
    }
}
