package gregtech.api.configuration;

import gregtech.common.command.CommandFluidReconcile;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.NumberInvalidException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.server.command.CommandTreeBase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

public class FluidReconcileCommandTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);
    private static final String USAGE = "gregtech.command.fluid_reconcile.usage";

    @BeforeAll
    static void bootstrap() {
        PipeFamilyTestSupport.bootstrap();
    }

    @Test
    void inspectAndExplicitAcknowledgementDoNotMintFluid() throws Exception {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        BlockPos pos = new BlockPos(0, 64, 0);
        PipeTestFluidPipe pipe = PipeFamilyTestSupport.fluid(world, pos);
        NBTTagCompound saved = pipe.writeToNBT(new NBTTagCompound());
        saved.removeTag("PipeBlock");
        NBTTagCompound record = new NBTTagCompound();
        record.setInteger("Amount", 100);
        saved.setTag("UncertainFluidTransfer", record);
        pipe.readFromNBT(saved);
        List<ITextComponent> messages = new ArrayList<>();
        ICommandSender sender = (ICommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ICommandSender.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getPosition":
                            return pos;
                        case "getEntityWorld":
                            return world;
                        case "getName":
                            return "test-admin";
                        case "sendMessage":
                            messages.add((ITextComponent) args[0]);
                            return null;
                        case "canUseCommand":
                            return true;
                        default:
                            return null;
                    }
                });
        CommandFluidReconcile command = new CommandFluidReconcile();
        assertEquals(4, command.getRequiredPermissionLevel());
        ICommandSender lowerPermission = (ICommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { ICommandSender.class }, (proxy, method, args) -> {
                    if ("canUseCommand".equals(method.getName())) return ((Integer) args[0]) <= 2;
                    return null;
                });
        assertFalse(command.checkPermission(null, lowerPermission));
        assertTrue(command.checkPermission(null, sender));
        command.execute(null, sender, new String[] { "0", "64", "0", "inspect" });
        assertEquals(record, pipe.getUncertainFluidTransfer());
        assertEquals(2, messages.size());
        assertThrows(CommandException.class, () -> command.execute(null, sender,
                new String[] { "0", "64", "0", "clear" }));
        assertEquals(record, pipe.getUncertainFluidTransfer());
        command.execute(null, sender, new String[] { "0", "64", "0", "clear", "acknowledge" });
        assertNull(pipe.getUncertainFluidTransfer());
        assertNull(pipe.getContainedFluid(0));
        assertTrue(pipe.dirtyNotifications > 0);
        assertThrows(CommandException.class, () -> command.execute(null, sender,
                new String[] { "0", "64", "0", "inspect" }));
        world.unloaded.add(pos);
        assertThrows(CommandException.class, () -> command.execute(null, sender,
                new String[] { "0", "64", "0", "inspect" }));
    }

    @Test
    void gtCommandTreeRejectsLowPermissionSendersBeforeTheSubcommandRuns() throws Exception {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        PipeTestFluidPipe pipe = PipeFamilyTestSupport.fluid(world, POS);
        NBTTagCompound record = attachRecord(pipe, 100);
        CommandTreeBase gt = gtCommand();
        gt.addSubcommand(new CommandFluidReconcile());
        List<ITextComponent> messages = new ArrayList<>();
        ICommandSender operator = sender(world, 2, messages);

        CommandException denied = assertThrows(CommandException.class, () -> gt.execute(null, operator,
                new String[] { "fluid-reconcile", "0", "64", "0", "clear", "acknowledge" }));

        assertEquals("commands.generic.permission", denied.getMessage());
        assertEquals(record, pipe.getUncertainFluidTransfer());
        assertTrue(messages.isEmpty());

        ICommandSender admin = sender(world, 4, messages);
        gt.execute(null, admin, new String[] { "fluid-reconcile", "0", "64", "0", "inspect" });
        assertEquals(2, messages.size());
        gt.execute(null, admin, new String[] { "fluid-reconcile", "0", "64", "0", "clear", "acknowledge" });
        assertNull(pipe.getUncertainFluidTransfer());
    }

    @Test
    void missingPipeIsReported() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        ICommandSender sender = sender(world, 4, new ArrayList<>());
        CommandFluidReconcile command = new CommandFluidReconcile();

        CommandException missing = assertThrows(CommandException.class,
                () -> command.execute(null, sender, new String[] { "0", "64", "0", "inspect" }));
        assertEquals("gregtech.command.fluid_reconcile.no_pipe", missing.getMessage());

        world.put(POS, new PipeTestEndpoint(), world.getBlockState(POS));
        missing = assertThrows(CommandException.class,
                () -> command.execute(null, sender, new String[] { "0", "64", "0", "inspect" }));
        assertEquals("gregtech.command.fluid_reconcile.no_pipe", missing.getMessage());
    }

    @Test
    void malformedCoordinatesAndDimensionsAreRejectedWithoutTouchingTheRecord() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        PipeTestFluidPipe pipe = PipeFamilyTestSupport.fluid(world, POS);
        NBTTagCompound record = attachRecord(pipe, 100);
        ICommandSender sender = sender(world, 4, new ArrayList<>());
        CommandFluidReconcile command = command(Collections.singletonMap(0, world));

        for (String[] args : new String[][] {
                { "x", "64", "0", "clear", "acknowledge" },
                { "0", "64.5.1", "0", "clear", "acknowledge" },
                { "0", "64", "40000000", "clear", "acknowledge" },
                { "0", "64", "0", "nether", "clear", "acknowledge" },
                { "0", "64", "0", "0.5", "inspect" } }) {
            assertThrows(NumberInvalidException.class, () -> command.execute(null, sender, args),
                    Arrays.toString(args));
            assertEquals(record, pipe.getUncertainFluidTransfer());
        }
    }

    @Test
    void wrongArgumentShapesShowUsage() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        PipeTestFluidPipe pipe = PipeFamilyTestSupport.fluid(world, POS);
        NBTTagCompound record = attachRecord(pipe, 100);
        ICommandSender sender = sender(world, 4, new ArrayList<>());
        CommandFluidReconcile command = command(Collections.singletonMap(0, world));

        for (String[] args : new String[][] {
                {},
                { "0", "64", "0" },
                { "0", "64", "0", "0" },
                { "0", "64", "0", "bogus" },
                { "0", "64", "0", "clear" },
                { "0", "64", "0", "clear", "nope" },
                { "0", "64", "0", "inspect", "extra" },
                { "0", "64", "0", "inspect", "inspect" },
                { "0", "64", "0", "0", "clear" },
                { "0", "64", "0", "0", "inspect", "extra" },
                { "0", "64", "0", "0", "clear", "acknowledge", "extra" },
                // the shape is checked before coordinates are parsed
                { "x", "64", "0" },
                { "x", "y", "z", "bogus" },
                { "0", "64.5.1", "0", "clear" },
                { "x", "64", "0", "0", "inspect", "extra" } }) {
            CommandException usage = assertThrows(CommandException.class,
                    () -> command.execute(null, sender, args), Arrays.toString(args));
            assertEquals(USAGE, usage.getMessage(), Arrays.toString(args));
            assertEquals(record, pipe.getUncertainFluidTransfer());
        }
    }

    @Test
    void dimensionArgumentTargetsOnlyThatLoadedWorld() throws Exception {
        ConfigurationTestWorld home = new ConfigurationTestWorld();
        ConfigurationTestWorld remote = new ConfigurationTestWorld();
        NBTTagCompound homeRecord = attachRecord(PipeFamilyTestSupport.fluid(home, POS), 100);
        PipeTestFluidPipe remotePipe = PipeFamilyTestSupport.fluid(remote, POS);
        NBTTagCompound remoteRecord = attachRecord(remotePipe, 250);
        Map<Integer, World> loaded = new TreeMap<>();
        loaded.put(0, home);
        loaded.put(7, remote);
        CommandFluidReconcile command = command(loaded);
        List<ITextComponent> messages = new ArrayList<>();
        ICommandSender sender = sender(home, 4, messages);

        command.execute(null, sender, new String[] { "0", "64", "0", "7", "inspect" });
        assertEquals(2, messages.size());
        assertEquals(remoteRecord.toString(), messages.get(1).getUnformattedText());

        command.execute(null, sender, new String[] { "0", "64", "0", "7", "clear", "acknowledge" });
        assertNull(remotePipe.getUncertainFluidTransfer());
        assertEquals(homeRecord, ((PipeTestFluidPipe) home.getTileEntity(POS)).getUncertainFluidTransfer());

        CommandException unloaded = assertThrows(CommandException.class,
                () -> command.execute(null, sender, new String[] { "0", "64", "0", "3", "inspect" }));
        assertEquals("gregtech.command.fluid_reconcile.dim_unloaded", unloaded.getMessage());
        assertArrayEquals(new Object[] { 3 }, unloaded.getErrorObjects());
    }

    @Test
    void relativeCoordinatesAreRejectedWithAnExplicitDimension() throws Exception {
        ConfigurationTestWorld home = new ConfigurationTestWorld();
        ConfigurationTestWorld remote = new ConfigurationTestWorld();
        PipeTestFluidPipe homePipe = PipeFamilyTestSupport.fluid(home, POS);
        attachRecord(homePipe, 100);
        PipeTestFluidPipe remotePipe = PipeFamilyTestSupport.fluid(remote, POS);
        NBTTagCompound remoteRecord = attachRecord(remotePipe, 250);
        Map<Integer, World> loaded = new TreeMap<>();
        loaded.put(0, home);
        loaded.put(7, remote);
        CommandFluidReconcile command = command(loaded);
        List<ITextComponent> messages = new ArrayList<>();
        ICommandSender sender = sender(home, 4, messages);

        for (String[] args : new String[][] {
                { "~", "~", "~", "7", "clear", "acknowledge" },
                { "0", "~", "0", "7", "inspect" },
                { "0", "64", "~1", "0", "inspect" } }) {
            CommandException relative = assertThrows(CommandException.class,
                    () -> command.execute(null, sender, args), Arrays.toString(args));
            assertEquals("gregtech.command.fluid_reconcile.relative_with_dim", relative.getMessage(),
                    Arrays.toString(args));
        }
        assertEquals(remoteRecord, remotePipe.getUncertainFluidTransfer());
        assertTrue(messages.isEmpty());

        // without a dimension '~' still resolves from the sender, in the sender's world
        command.execute(null, sender, new String[] { "~", "~", "~", "clear", "acknowledge" });
        assertNull(homePipe.getUncertainFluidTransfer());
        assertEquals(remoteRecord, remotePipe.getUncertainFluidTransfer());
    }

    @Test
    void defaultDimensionLookupDoesNotLoadUnknownDimensions() {
        ConfigurationTestWorld world = new ConfigurationTestWorld();
        NBTTagCompound record = attachRecord(PipeFamilyTestSupport.fluid(world, POS), 100);
        ICommandSender sender = sender(world, 4, new ArrayList<>());

        CommandException unloaded = assertThrows(CommandException.class, () -> new CommandFluidReconcile()
                .execute(null, sender, new String[] { "0", "64", "0", "424242", "clear", "acknowledge" }));

        assertEquals("gregtech.command.fluid_reconcile.dim_unloaded", unloaded.getMessage());
        assertEquals(record, ((PipeTestFluidPipe) world.getTileEntity(POS)).getUncertainFluidTransfer());
    }

    @Test
    void tabCompletionOffersCoordinatesDimensionsAndActions() {
        Map<Integer, World> loaded = new TreeMap<>();
        loaded.put(0, null);
        loaded.put(7, null);
        CommandFluidReconcile command = command(loaded);
        BlockPos target = new BlockPos(1, 2, 3);

        assertEquals(Arrays.asList("~"), complete(command, null, ""));
        assertEquals(Arrays.asList("1"), complete(command, target, ""));
        assertEquals(Arrays.asList("2"), complete(command, target, "1", ""));
        assertEquals(Arrays.asList("3"), complete(command, target, "1", "2", ""));
        assertEquals(Arrays.asList("0", "7", "inspect", "clear"), complete(command, target, "1", "2", "3", ""));
        assertEquals(Arrays.asList("clear"), complete(command, target, "1", "2", "3", "c"));
        assertEquals(Arrays.asList("7"), complete(command, target, "1", "2", "3", "7"));
        assertEquals(Arrays.asList("acknowledge"), complete(command, target, "1", "2", "3", "clear", ""));
        assertEquals(Collections.emptyList(), complete(command, target, "1", "2", "3", "inspect", ""));
        assertEquals(Arrays.asList("inspect", "clear"), complete(command, target, "1", "2", "3", "7", ""));
        assertEquals(Arrays.asList("inspect"), complete(command, target, "1", "2", "3", "7", "i"));
        assertEquals(Arrays.asList("acknowledge"), complete(command, target, "1", "2", "3", "7", "clear", "a"));
        assertEquals(Collections.emptyList(), complete(command, target, "1", "2", "3", "7", "inspect", ""));
        assertEquals(Collections.emptyList(),
                complete(command, target, "1", "2", "3", "7", "clear", "acknowledge", ""));
    }

    private static List<String> complete(CommandFluidReconcile command, @Nullable BlockPos target, String... args) {
        return command.getTabCompletions(null, sender(null, 4, new ArrayList<>()), args, target);
    }

    /** The real /gt root; package-private in core, so constructed reflectively. */
    private static CommandTreeBase gtCommand() throws Exception {
        Constructor<?> constructor = Class.forName("gregtech.core.command.internal.GregTechCommand")
                .getDeclaredConstructor();
        constructor.setAccessible(true);
        return (CommandTreeBase) constructor.newInstance();
    }

    private static CommandFluidReconcile command(Map<Integer, World> loaded) {
        return new CommandFluidReconcile() {

            @Nullable
            @Override
            protected World getLoadedWorld(@NotNull MinecraftServer server, int dimension) {
                return loaded.get(dimension);
            }

            @NotNull
            @Override
            protected Integer[] getLoadedDimensions(@NotNull MinecraftServer server) {
                return loaded.keySet().toArray(new Integer[0]);
            }
        };
    }

    private static ICommandSender sender(@Nullable World world, int permission, List<ITextComponent> messages) {
        return (ICommandSender) Proxy.newProxyInstance(FluidReconcileCommandTest.class.getClassLoader(),
                new Class<?>[] { ICommandSender.class }, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getPosition":
                            return POS;
                        case "getEntityWorld":
                            return world;
                        case "getName":
                            return "test-sender";
                        case "sendMessage":
                            messages.add((ITextComponent) args[0]);
                            return null;
                        case "canUseCommand":
                            return (Integer) args[0] <= permission;
                        default:
                            return null;
                    }
                });
    }

    private static NBTTagCompound attachRecord(PipeTestFluidPipe pipe, int amount) {
        NBTTagCompound saved = pipe.writeToNBT(new NBTTagCompound());
        saved.removeTag("PipeBlock");
        NBTTagCompound record = new NBTTagCompound();
        record.setInteger("Amount", amount);
        saved.setTag("UncertainFluidTransfer", record);
        pipe.readFromNBT(saved);
        return record;
    }
}
