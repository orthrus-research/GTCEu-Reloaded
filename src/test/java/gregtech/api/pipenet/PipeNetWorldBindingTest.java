package gregtech.api.pipenet;

import gregtech.Bootstrap;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PipeNetWorldBindingTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void initialReadDefersWorldAwareCallbacksUntilBindingAndBoundReloadStillNotifies() {
        BlockPos pos = new BlockPos(0, 64, 0);
        DummyWorld world = new DummyWorld();
        WorldAwareCallbackWorld source = new WorldAwareCallbackWorld();
        source.attach(world);
        source.addNode(pos, 7, 0, 63, true);
        NBTTagCompound saved = source.writeToNBT(new NBTTagCompound());
        WorldAwareCallbackWorld restored = new WorldAwareCallbackWorld();

        assertDoesNotThrow(() -> restored.readFromNBT(saved));

        assertNull(restored.getWorld());
        assertFalse(restored.hasQuarantinedData(),
                "A valid saved graph must not be quarantined by an unbound callback");
        WorldAwareCallbackNetwork initial = restored.getNetFromPos(pos);
        assertNotNull(initial);
        assertEquals(0, initial.callbacks);
        assertEquals(saved, restored.writeToNBT(new NBTTagCompound()));

        restored.attach(world);

        assertEquals(1, initial.callbacks);
        assertEquals(world.getTotalWorldTime(), initial.observedTime);
        assertSame(world, initial.getWorldData());
        restored.readFromNBT(saved);
        WorldAwareCallbackNetwork reloaded = restored.getNetFromPos(pos);
        assertNotSame(initial, reloaded);
        assertFalse(initial.isValid());
        assertEquals(1, reloaded.callbacks, "Already-bound manual reload keeps its notification");
        assertSame(world, reloaded.getWorldData());
        assertFalse(restored.hasQuarantinedData());
    }
}
