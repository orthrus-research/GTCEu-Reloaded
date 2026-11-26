package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.Bootstrap;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.Collections;

import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.*;

/** The item and fluid passthrough hatches share their code; these pin what both still do, and do alike. */
class PassthroughHatchTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        ASMDataTable capabilities = new ASMDataTable();
        if (CapabilityItemHandler.ITEM_HANDLER_CAPABILITY == null) {
            capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityItemHandler.class.getName(),
                    "ITEM_HANDLER_CAPABILITY", Collections.singletonMap("value", Type.getType(IItemHandler.class)));
        }
        if (CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY == null) {
            capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                    "FLUID_HANDLER_CAPABILITY", Collections.singletonMap("value", Type.getType(IFluidHandler.class)));
            capabilities.addASMData(null, CapabilityInject.class.getName(), CapabilityFluidHandler.class.getName(),
                    "FLUID_HANDLER_ITEM_CAPABILITY",
                    Collections.singletonMap("value", Type.getType(IFluidHandlerItem.class)));
        }
        CapabilityManager.INSTANCE.injectCapabilities(capabilities);
        if (CapabilityItemHandler.ITEM_HANDLER_CAPABILITY == null) CapabilityItemHandler.register();
        if (CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY == null) CapabilityFluidHandler.register();
        assertNotNull(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        assertNotNull(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
    }

    @Test
    void bothHatchesTakeInAtTheFrontAndGiveOutAtTheBackOnly() {
        MetaTileEntityPassthroughHatchItem itemHatch = itemHatch(3);
        MetaTileEntityPassthroughHatchFluid fluidHatch = fluidHatch(3);
        EnumFacing front = itemHatch.getFrontFacing();
        assertEquals(front, fluidHatch.getFrontFacing());

        IItemHandler itemFront = items(itemHatch, front);
        IItemHandler itemBack = items(itemHatch, front.getOpposite());
        ItemStack apples = new ItemStack(Items.APPLE, 5);
        assertTrue(itemFront.insertItem(0, apples.copy(), false).isEmpty());
        assertTrue(itemFront.extractItem(0, 5, false).isEmpty());
        assertEquals(5, itemBack.insertItem(0, apples.copy(), false).getCount());
        assertEquals(5, itemBack.extractItem(0, 5, false).getCount());

        IFluidHandler fluidFront = fluids(fluidHatch, front);
        IFluidHandler fluidBack = fluids(fluidHatch, front.getOpposite());
        FluidStack water = new FluidStack(FluidRegistry.WATER, 500);
        assertEquals(500, fluidFront.fill(water.copy(), true));
        assertNull(fluidFront.drain(500, true));
        assertEquals(0, fluidBack.fill(water.copy(), true));
        assertEquals(500, fluidBack.drain(500, true).amount);

        for (EnumFacing side : EnumFacing.VALUES) {
            if (side.getAxis() == front.getAxis()) continue;
            assertNull(items(itemHatch, side));
            assertNull(fluids(fluidHatch, side));
        }
    }

    @Test
    void bothHatchesSizeTheirStorageByTier() {
        assertEquals(16, front(itemHatch(3)).getSlots());
        assertEquals(100, front(itemHatch(9)).getSlots());
        assertEquals(100, front(itemHatch(12)).getSlots());
        IFluidTankProperties[] tanks = front(fluidHatch(3)).getTankProperties();
        assertEquals(4, tanks.length);
        for (IFluidTankProperties tank : tanks) assertEquals(16_000, tank.getCapacity());
        assertEquals(6, front(fluidHatch(5)).getTankProperties().length);
        assertEquals(IItemHandlerModifiable.class, itemHatch(3).getPassthroughType());
        assertEquals(IFluidHandler.class, fluidHatch(3).getPassthroughType());
    }

    @Test
    void bothHatchesSaveContentsAndWorkingStateUnderTheirOwnKeys() {
        MetaTileEntityPassthroughHatchItem itemHatch = itemHatch(3);
        MetaTileEntityPassthroughHatchFluid fluidHatch = fluidHatch(3);
        front(itemHatch).insertItem(2, new ItemStack(Items.APPLE, 5), false);
        front(fluidHatch).fill(new FluidStack(FluidRegistry.WATER, 500), true);
        assertTrue(itemHatch.isWorkingEnabled());
        assertTrue(fluidHatch.isWorkingEnabled());
        itemHatch.setWorkingEnabled(false);
        fluidHatch.setWorkingEnabled(false);

        NBTTagCompound itemTag = itemHatch.writeToNBT(new NBTTagCompound());
        NBTTagCompound fluidTag = fluidHatch.writeToNBT(new NBTTagCompound());
        assertTrue(itemTag.hasKey("Inventory", 10));
        assertFalse(itemTag.hasKey("FluidInventory"));
        assertTrue(fluidTag.hasKey("FluidInventory", 10));
        assertFalse(fluidTag.hasKey("Inventory"));
        for (NBTTagCompound tag : new NBTTagCompound[] { itemTag, fluidTag }) {
            assertTrue(tag.hasKey("WorkingEnabled", 1));
            assertFalse(tag.getBoolean("WorkingEnabled"));
        }

        MetaTileEntityPassthroughHatchItem loadedItems = itemHatch(3);
        MetaTileEntityPassthroughHatchFluid loadedFluids = fluidHatch(3);
        loadedItems.readFromNBT(itemTag);
        loadedFluids.readFromNBT(fluidTag);
        assertFalse(loadedItems.isWorkingEnabled());
        assertFalse(loadedFluids.isWorkingEnabled());
        assertEquals(5, back(loadedItems).extractItem(2, 64, true).getCount());
        assertEquals(500, back(loadedFluids).drain(1000, false).amount);
        assertEquals(itemTag.getTag("Inventory"), loadedItems.writeToNBT(new NBTTagCompound()).getTag("Inventory"));
        assertEquals(fluidTag.getTag("FluidInventory"),
                loadedFluids.writeToNBT(new NBTTagCompound()).getTag("FluidInventory"));

        // hatches saved before the working switch existed load enabled
        itemTag.removeTag("WorkingEnabled");
        fluidTag.removeTag("WorkingEnabled");
        MetaTileEntityPassthroughHatchItem oldItems = itemHatch(3);
        MetaTileEntityPassthroughHatchFluid oldFluids = fluidHatch(3);
        oldItems.readFromNBT(itemTag);
        oldFluids.readFromNBT(fluidTag);
        assertTrue(oldItems.isWorkingEnabled());
        assertTrue(oldFluids.isWorkingEnabled());
    }

    private static MetaTileEntityPassthroughHatchItem itemHatch(int tier) {
        return new MetaTileEntityPassthroughHatchItem(gregtechId("passthrough_test_item"), tier);
    }

    private static MetaTileEntityPassthroughHatchFluid fluidHatch(int tier) {
        return new MetaTileEntityPassthroughHatchFluid(gregtechId("passthrough_test_fluid"), tier);
    }

    private static IItemHandler front(MetaTileEntityPassthroughHatchItem hatch) {
        return items(hatch, hatch.getFrontFacing());
    }

    private static IItemHandler back(MetaTileEntityPassthroughHatchItem hatch) {
        return items(hatch, hatch.getFrontFacing().getOpposite());
    }

    private static IFluidHandler front(MetaTileEntityPassthroughHatchFluid hatch) {
        return fluids(hatch, hatch.getFrontFacing());
    }

    private static IFluidHandler back(MetaTileEntityPassthroughHatchFluid hatch) {
        return fluids(hatch, hatch.getFrontFacing().getOpposite());
    }

    private static IItemHandler items(MetaTileEntityPassthroughHatchItem hatch, EnumFacing side) {
        return hatch.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
    }

    private static IFluidHandler fluids(MetaTileEntityPassthroughHatchFluid hatch, EnumFacing side) {
        return hatch.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
    }
}
