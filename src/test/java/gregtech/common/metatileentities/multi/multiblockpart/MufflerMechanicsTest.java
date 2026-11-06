package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.Bootstrap;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MufflerMechanicsTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void exhaustObstructionIsSampledEveryTenTicksAndOnlyChangesAreSent() {
        var state = new AtomicReference<>(Blocks.AIR.getDefaultState());
        var world = new DummyWorld() {

            @Override
            public IBlockState getBlockState(BlockPos pos) {
                return state.get();
            }
        };
        var hatch = new MufflerTestHatch(world);
        hatch.tick = 1;
        hatch.update();
        assertFalse(hatch.isFrontFaceFree());
        assertTrue(hatch.updates.isEmpty());
        hatch.tick = 10;
        hatch.update();
        assertTrue(hatch.isFrontFaceFree());
        hatch.tick = 20;
        hatch.update();
        assertEquals(List.of(true), hatch.updates);
        state.set(Blocks.STONE.getDefaultState());
        hatch.tick = 21;
        hatch.update();
        assertTrue(hatch.isFrontFaceFree());
        hatch.tick = 30;
        hatch.update();
        assertFalse(hatch.isFrontFaceFree());
        state.set(Blocks.SNOW_LAYER.getDefaultState());
        hatch.tick = 40;
        hatch.update();
        assertTrue(hatch.isFrontFaceFree());
        assertSame(Blocks.SNOW_LAYER, state.get().getBlock(), "An inactive controller must leave snow in place");
        assertEquals(List.of(true, false, true), hatch.updates);
    }

    @Test
    void initialAndIncrementalSyncPreserveTheExhaustState() {
        var source = new MufflerTestHatch();
        var target = new MufflerTestHatch();
        source.tick = 10;
        source.update();
        assertTrue(source.isFrontFaceFree());
        var buffer = new PacketBuffer(Unpooled.buffer());
        try {
            source.writeInitialSyncData(buffer);
            target.receiveInitialSyncData(buffer);
            assertTrue(target.isFrontFaceFree());
            assertEquals(0, buffer.readableBytes());
            buffer.clear().writeBoolean(false);
            target.receiveCustomData(GregtechDataCodes.MUFFLER_OBSTRUCTED, buffer);
            assertFalse(target.isFrontFaceFree());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void exhaustHatchHasNoRecoveryInventoryOrInventoryScreen() {
        var hatch = new MufflerTestHatch();
        assertEquals(0, hatch.getImportItems().getSlots());
        assertEquals(0, hatch.getExportItems().getSlots());
        assertSame(MultiblockAbility.MUFFLER_HATCH, hatch.getAbility());
        assertFalse(hatch.openGUIOnRightClick());
        var dropped = new ArrayList<ItemStack>();
        hatch.clearMachineInventory(dropped);
        assertTrue(dropped.isEmpty());
        var saved = hatch.writeToNBT(new NBTTagCompound());
        assertFalse(saved.hasKey("RecoveryInventory"));
        assertFalse(saved.hasKey("RecoveryChance"));
    }
}
