package gregtech.api.configuration;

import gregtech.common.command.CommandFluidReconcile;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FluidReconcileCommandTest {

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
}
