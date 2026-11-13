package gregtech.api.util;

import gregtech.Bootstrap;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NetworkUtilFluidStackTest {

    @BeforeAll
    public static void prepare() {
        Bootstrap.perform();
    }

    @Test
    public void nullStackLeavesFollowingValuesAligned() {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        NetworkUtil.writeFluidStack(buffer, null);
        buffer.writeInt(12345);

        assertNull(NetworkUtil.readFluidStack(buffer));
        assertEquals(12345, buffer.readInt());
    }

    @Test
    public void stackRoundTrips() {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        NetworkUtil.writeFluidStack(buffer, new FluidStack(FluidRegistry.WATER, 250));
        buffer.writeInt(12345);

        FluidStack read = NetworkUtil.readFluidStack(buffer);
        assertNotNull(read);
        assertTrue(read.isFluidEqual(new FluidStack(FluidRegistry.WATER, 1)));
        assertEquals(250, read.amount);
        assertEquals(12345, buffer.readInt());
    }
}
