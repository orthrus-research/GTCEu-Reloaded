package gregtech.common.metatileentities.storage;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.unification.material.Materials;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class CrateStorageTest {

    @BeforeAll
    static void setup() {
        Bootstrap.perform();
    }

    private static MetaTileEntityCrate crate(int slots, int rowSize) {
        return new MetaTileEntityCrate(new ResourceLocation("gregtech", "crate_storage_test"), Materials.Wood, slots,
                rowSize);
    }

    @Test
    void layoutRejectsPartialRowsAndCopiesTheNativeConfiguration() {
        for (int[] shape : new int[][] { { 27, 9 }, { 54, 9 }, { 72, 9 }, { 90, 10 }, { 108, 12 }, { 126, 14 },
                { 144, 16 }, { 4, 2 }, { 16, 4 } }) {
            var original = crate(shape[0], shape[1]);
            var copy = (MetaTileEntityCrate) original.createMetaTileEntity(null);
            assertEquals(shape[0], copy.getItemInventory().getSlots());
            assertEquals(shape[0], copy.getInventorySize());
            assertEquals(shape[1], copy.getRowSize());
            assertSame(Materials.Wood, copy.getMaterial());
        }
        assertThrows(IllegalArgumentException.class, () -> crate(0, 9));
        assertThrows(IllegalArgumentException.class, () -> crate(4, 0));
        assertThrows(IllegalArgumentException.class, () -> crate(4, 9));
    }

    @Test
    void ordinaryCrateSpillsContentsAndDoesNotDuplicateThemInTheDroppedItem() {
        var crate = crate(27, 9);
        crate.getItemInventory().insertItem(26, new ItemStack(Items.DIAMOND, 5), false);
        assertFalse(crate.keepsInventory());
        var item = new NBTTagCompound();
        crate.writeItemStackData(item);
        assertFalse(item.hasKey("Inventory"));
        var dropped = new ArrayList<ItemStack>();
        crate.clearMachineInventory(dropped);
        assertEquals(1, dropped.size());
        assertEquals(5, dropped.getFirst().getCount());
        assertTrue(crate.getItemInventory().getStackInSlot(26).isEmpty());
    }

    @Test
    void tapedSaveCarriesInventoryOnceAndPlacementRemovesTheTape() {
        var crate = crate(27, 9);
        crate.getItemInventory().insertItem(26, new ItemStack(Items.DIAMOND, 5), false);
        var saved = crate.writeToNBT(new NBTTagCompound());
        saved.setBoolean("Taped", true);
        crate.readFromNBT(saved);
        assertTrue(crate.keepsInventory());
        var dropped = new ArrayList<ItemStack>();
        crate.clearMachineInventory(dropped);
        assertTrue(dropped.isEmpty());
        var item = new NBTTagCompound();
        crate.writeItemStackData(item);
        assertTrue(item.getBoolean("Taped"));
        var placed = crate(27, 9);
        placed.initFromItemStackData(item);
        assertFalse(placed.keepsInventory());
        assertEquals(5, placed.getItemInventory().getStackInSlot(26).getCount());
        assertFalse(item.hasKey("Taped"));
    }

    @Test
    void permanentRetentionSurvivesWorldAndItemReadsWithoutPrivateTapeMutation() {
        var crate = new RetainedCrate();
        crate.getItemInventory().insertItem(3, new ItemStack(Items.APPLE, 8), false);
        var saved = crate.writeToNBT(new NBTTagCompound());
        assertFalse(saved.getBoolean("Taped"));
        var loaded = new RetainedCrate();
        loaded.readFromNBT(saved);
        assertTrue(loaded.keepsInventory());
        var item = new NBTTagCompound();
        loaded.writeItemStackData(item);
        assertTrue(item.getBoolean("Taped"));
        var placed = new RetainedCrate();
        placed.initFromItemStackData(item);
        assertTrue(placed.keepsInventory());
        assertEquals(8, placed.getItemInventory().getStackInSlot(3).getCount());
        var dropped = new ArrayList<ItemStack>();
        placed.clearMachineInventory(dropped);
        assertTrue(dropped.isEmpty());
    }

    @Test
    void emptyPlacementDataKeepsTheDeclaredInventoryCapacity() {
        var crate = new RetainedCrate();
        crate.initFromItemStackData(new NBTTagCompound());
        assertEquals(4, crate.getItemInventory().getSlots());
    }

    private static final class RetainedCrate extends MetaTileEntityCrate {

        private RetainedCrate() {
            super(new ResourceLocation("gregtech", "retained_crate_test"), Materials.Wood, 4, 2);
        }

        @Override
        public boolean keepsInventory() {
            return true;
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new RetainedCrate();
        }
    }
}
