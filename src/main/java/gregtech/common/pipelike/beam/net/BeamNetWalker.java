package gregtech.common.pipelike.beam.net;

import gregtech.api.pipenet.PipeNetWalker;
import gregtech.api.util.GTUtility;
import gregtech.common.pipelike.beam.tile.TileEntityBeamPipe;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Walks a beam line from the face of the pipe a request entered, and stops at the first endpoint beside the line
 * other than the one on that face. The family's walker says what an endpoint is.
 */
public abstract class BeamNetWalker<T extends TileEntityBeamPipe<?, ?, ?>,
        RouteType extends BeamRoutePath<T>> extends PipeNetWalker<T> {

    private RouteType routePath;
    private BlockPos sourcePipe;
    private EnumFacing facingToHandler;

    protected BeamNetWalker(World world, BlockPos sourcePipe, int distance) {
        super(world, sourcePipe, distance);
    }

    /**
     * Walks the line from a face of the source pipe, where the walker starts.
     *
     * @return the route to the line's endpoint, null when it has none, or {@code failedMarker} when the walk failed
     */
    @Nullable
    protected static <R extends BeamRoutePath<?>> R walk(BeamNetWalker<?, R> walker, BlockPos sourcePipe,
                                                         EnumFacing faceToSourceHandler, R failedMarker) {
        walker.sourcePipe = sourcePipe;
        walker.facingToHandler = faceToSourceHandler;
        walker.traversePipeNet();
        return walker.isFailed() ? failedMarker : walker.routePath;
    }

    /**
     * Creates a walker of the family at {@code pos}, {@code walkedBlocks} from the source; it is told where the walk
     * started.
     */
    protected abstract BeamNetWalker<T, RouteType> createWalker(World world, BlockPos pos, int walkedBlocks);

    /** Whether the tile beside the line is an endpoint of it, reached from {@code side} of the tile. */
    protected abstract boolean isEndpoint(TileEntity tile, EnumFacing side);

    protected abstract RouteType createRoutePath(T targetPipe, EnumFacing faceToHandler, int distance);

    /** The face of the source pipe the request entered through. */
    protected EnumFacing getFacingToHandler() {
        return facingToHandler;
    }

    @Override
    protected PipeNetWalker<T> createSubWalker(World world, EnumFacing facingToNextPos, BlockPos nextPos,
                                               int walkedBlocks) {
        BeamNetWalker<T, RouteType> walker = createWalker(world, nextPos, walkedBlocks);
        walker.facingToHandler = facingToHandler;
        walker.sourcePipe = sourcePipe;
        return walker;
    }

    @Override
    protected void checkPipe(T pipeTile, BlockPos pos) {}

    @Override
    protected void checkNeighbour(T pipeTile, BlockPos pipePos, EnumFacing faceToNeighbour,
                                  @Nullable TileEntity neighbourTile) {
        if (neighbourTile == null ||
                (GTUtility.arePosEqual(pipePos, sourcePipe) && faceToNeighbour == facingToHandler)) {
            return;
        }

        @SuppressWarnings("unchecked")
        BeamNetWalker<T, RouteType> rootWalker = (BeamNetWalker<T, RouteType>) root;
        if (rootWalker.routePath == null && isEndpoint(neighbourTile, faceToNeighbour.getOpposite())) {
            rootWalker.routePath = createRoutePath(pipeTile, faceToNeighbour, getWalkedBlocks());
            stop();
        }
    }
}
