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

import org.jetbrains.annotations.NotNull;

public class CommandFluidReconcile extends CommandBase {

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
        if (args.length < 4 || args.length > 5) throw new CommandException(getUsage(sender));
        BlockPos pos = parseBlockPos(sender, args, 0, false);
        World world = sender.getEntityWorld();
        if (!world.isBlockLoaded(pos)) throw new CommandException("gregtech.command.fluid_reconcile.unloaded");
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityFluidPipeTickable pipe)) {
            throw new CommandException("gregtech.command.fluid_reconcile.no_pipe");
        }
        NBTBase record = pipe.getUncertainFluidTransfer();
        if (record == null) throw new CommandException("gregtech.command.fluid_reconcile.no_record");
        if (args.length == 4 && "inspect".equals(args[3])) {
            sender.sendMessage(new TextComponentTranslation("gregtech.command.fluid_reconcile.inspect"));
            sender.sendMessage(new TextComponentString(record.toString()));
            return;
        }
        if (args.length == 5 && "clear".equals(args[3]) && "acknowledge".equals(args[4])) {
            GTLog.logger.warn("{} acknowledged uncertain fluid transfer at dimension {} position {}: {}",
                    sender.getName(), world.provider.getDimension(), pos, record);
            pipe.acknowledgeUncertainFluidTransfer();
            sender.sendMessage(new TextComponentTranslation("gregtech.command.fluid_reconcile.cleared"));
            return;
        }
        throw new CommandException(getUsage(sender));
    }
}
