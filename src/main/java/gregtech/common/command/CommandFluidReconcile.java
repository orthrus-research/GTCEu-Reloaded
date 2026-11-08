package gregtech.common.command;

import gregtech.api.util.GTLog;
import gregtech.common.pipelike.fluidpipe.tile.TileEntityFluidPipeTickable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTBase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code /gt fluid-reconcile <x> <y> <z> [dim] inspect | clear acknowledge}. Without a dimension the sender's world
 * is used.
 */
public class CommandFluidReconcile extends CommandBase {

    private static final String INSPECT = "inspect";
    private static final String CLEAR = "clear";
    private static final String ACKNOWLEDGE = "acknowledge";

    @NotNull
    @Override
    public String getName() {
        return "fluid-reconcile";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "gregtech.command.fluid_reconcile.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        if (args.length < 4 || args.length > 6) throw new CommandException(getUsage(sender));
        int action = isAction(args[3]) ? 3 : 4;
        int actionWords = args.length - action;
        boolean inspect = actionWords == 1 && INSPECT.equals(args[action]);
        boolean clear = actionWords == 2 && CLEAR.equals(args[action]) && ACKNOWLEDGE.equals(args[action + 1]);
        if (!inspect && !clear) throw new CommandException(getUsage(sender));

        boolean explicitDimension = action == 4;
        if (explicitDimension) {
            // '~' would resolve against the sender's position in the sender's own dimension, not the named one
            for (int i = 0; i < 3; i++) {
                if (args[i].startsWith("~")) {
                    throw new CommandException("gregtech.command.fluid_reconcile.relative_with_dim");
                }
            }
        }
        BlockPos pos = parseBlockPos(sender, args, 0, false);

        World world = explicitDimension ? resolveWorld(server, parseInt(args[3])) : sender.getEntityWorld();
        if (!world.isBlockLoaded(pos)) throw new CommandException("gregtech.command.fluid_reconcile.unloaded");
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityFluidPipeTickable pipe)) {
            throw new CommandException("gregtech.command.fluid_reconcile.no_pipe");
        }
        NBTBase record = pipe.getUncertainFluidTransfer();
        if (record == null) throw new CommandException("gregtech.command.fluid_reconcile.no_record");

        if (inspect) {
            sender.sendMessage(new TextComponentTranslation("gregtech.command.fluid_reconcile.inspect"));
            sender.sendMessage(new TextComponentString(record.toString()));
            return;
        }
        GTLog.logger.warn("{} acknowledged uncertain fluid transfer at dimension {} position {}: {}",
                sender.getName(), world.provider.getDimension(), pos, record);
        pipe.acknowledgeUncertainFluidTransfer();
        sender.sendMessage(new TextComponentTranslation("gregtech.command.fluid_reconcile.cleared"));
    }

    @NotNull
    @Override
    public List<String> getTabCompletions(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                                          @NotNull String[] args, @Nullable BlockPos targetPos) {
        if (args.length <= 3) return getTabCompletionCoordinate(args, 0, targetPos);
        if (args.length == 4) {
            List<String> options = new ArrayList<>();
            for (int dimension : getLoadedDimensions(server)) options.add(Integer.toString(dimension));
            options.add(INSPECT);
            options.add(CLEAR);
            return getListOfStringsMatchingLastWord(args, options);
        }

        int action = isAction(args[3]) ? 3 : 4;
        int word = args.length - 1 - action;
        if (word == 0) return getListOfStringsMatchingLastWord(args, INSPECT, CLEAR);
        if (word == 1 && CLEAR.equals(args[action])) return getListOfStringsMatchingLastWord(args, ACKNOWLEDGE);
        return Collections.emptyList();
    }

    @NotNull
    private World resolveWorld(@NotNull MinecraftServer server, int dimension) throws CommandException {
        World world = getLoadedWorld(server, dimension);
        if (world == null) throw new CommandException("gregtech.command.fluid_reconcile.dim_unloaded", dimension);
        return world;
    }

    /**
     * Looks up an already loaded dimension. Unlike {@link MinecraftServer#getWorld(int)}, this never initializes an
     * unloaded dimension as a side effect of an inspection command.
     */
    @Nullable
    protected World getLoadedWorld(@NotNull MinecraftServer server, int dimension) {
        return DimensionManager.getWorld(dimension);
    }

    @NotNull
    protected Integer[] getLoadedDimensions(@NotNull MinecraftServer server) {
        return DimensionManager.getIDs();
    }

    private static boolean isAction(@NotNull String arg) {
        return INSPECT.equals(arg) || CLEAR.equals(arg);
    }
}
