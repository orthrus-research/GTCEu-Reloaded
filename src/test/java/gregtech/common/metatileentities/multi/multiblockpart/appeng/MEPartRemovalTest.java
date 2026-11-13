package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.Bootstrap;
import gregtech.common.inventory.appeng.SerializableFluidList;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedFluidStack;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedItemStack;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MEPartRemovalTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void anOfflineInputBusDropsItsStock() {
        MetaTileEntityMEInputBus bus = new MetaTileEntityMEInputBus(gregtechId("removal_test_bus"));
        bus.getAEItemHandler().getInventory()[0]
                .setStock(WrappedItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT, 70)));

        List<ItemStack> drops = new ArrayList<>();
        bus.clearMachineInventory(drops);

        assertEquals(2, drops.size());
        assertEquals(64, drops.get(0).getCount());
        assertEquals(6, drops.get(1).getCount());
        assertTrue(drops.get(0).getItem() == Items.IRON_INGOT);
        assertNull(bus.getAEItemHandler().getInventory()[0].getStock());
        // the later onRemoval flush finds nothing left to return
        assertTrue(bus.flushInventory().isEmpty());
    }

    @Test
    void anOfflineInputHatchKeepsWhatTheNetworkDidNotTake() {
        MetaTileEntityMEInputHatch hatch = new MetaTileEntityMEInputHatch(gregtechId("removal_test_hatch"));
        hatch.getAEFluidHandler().getInventory()[0]
                .setStock(WrappedFluidStack.fromFluidStack(new FluidStack(FluidRegistry.WATER, 500)));

        List<IAEFluidStack> left = hatch.flushInventory();

        assertEquals(1, left.size());
        assertEquals(500, left.get(0).getStackSize());
        assertNull(hatch.getAEFluidHandler().getInventory()[0].getStock());
    }

    @Test
    void theOutputHatchBufferTravelsWithTheDroppedItem() {
        SerializableFluidList buffer = new SerializableFluidList();
        buffer.add(AEFluidStack.fromFluidStack(new FluidStack(FluidRegistry.LAVA, 64_000)));
        NBTTagCompound dropped = new NBTTagCompound();
        dropped.setTag(MetaTileEntityMEOutputHatch.FLUID_BUFFER_TAG, buffer.serializeNBT());

        MetaTileEntityMEOutputHatch placed = new MetaTileEntityMEOutputHatch(gregtechId("removal_test_output"));
        placed.initFromItemStackData(dropped);
        NBTTagCompound again = new NBTTagCompound();
        placed.writeItemStackData(again);

        assertEquals(dropped.getTag(MetaTileEntityMEOutputHatch.FLUID_BUFFER_TAG),
                again.getTag(MetaTileEntityMEOutputHatch.FLUID_BUFFER_TAG));
        NBTTagCompound empty = new NBTTagCompound();
        new MetaTileEntityMEOutputHatch(gregtechId("removal_test_output")).writeItemStackData(empty);
        assertTrue(empty.isEmpty());
    }
}
