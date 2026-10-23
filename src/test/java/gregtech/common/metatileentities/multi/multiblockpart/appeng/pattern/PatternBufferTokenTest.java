package gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PatternBufferTokenTest {

    @BeforeAll
    static void setup() {
        bootstrap();
    }

    @Test
    void dropRequirementsAndActualPacketTableHaveTheSameTaggedMaterialMeaning() {
        ItemStack drop = drop(1347, "exact-tag");
        FluidStack expected = PatternBufferTokens.fluid(drop, true);
        ItemStack packet = new ItemStack(token("fluid_packet"));
        packet.setTagCompound(new NBTTagCompound());
        packet.getTagCompound().setTag("FluidStack", expected.writeToNBT(new NBTTagCompound()));
        PatternBufferStorage storage = storage(() -> {});
        InventoryCrafting delivered = table(new ItemStack(Items.IRON_INGOT, 3), packet);
        assertTrue(storage.accept(0, details(new ItemStack(Items.IRON_INGOT, 3), drop), delivered, true));
        assertTrue(delivered.isEmpty());
        assertEquals(3, storage.getBatches().get(0).getItems().getStackInSlot(0).getCount());
        FluidStack saved = storage.getBatches().get(0).getFluids().getTankAt(0).getFluid();
        assertEquals(1347, saved.amount);
        assertTrue(expected.isFluidEqual(saved));
        assertEquals("exact-tag", saved.tag.getString("batch"));
    }

    @Test
    void unavailableBridgeGasDisplayOnlyAndSpoofedTokenIdentityFailClosed() {
        ItemStack valid = drop(1000, "tag");
        assertThrows(IllegalArgumentException.class, () -> PatternBufferTokens.fluid(valid, false));
        assertNull(PatternBufferTokens.fluid(new ItemStack(Items.IRON_INGOT), false));
        assertThrows(IllegalArgumentException.class,
                () -> PatternBufferTokens.fluid(new ItemStack(token("gas_drop")), true));
        ItemStack display = valid.copy();
        display.getTagCompound().setBoolean("DisplayOnly", true);
        assertThrows(IllegalArgumentException.class, () -> PatternBufferTokens.fluid(display, true));
        ItemStack spoof = new ItemStack(new Item().setRegistryName("ae2fc", "fluid_drop"), 1000);
        spoof.setTagCompound(valid.getTagCompound().copy());
        assertThrows(IllegalArgumentException.class, () -> PatternBufferTokens.fluid(spoof, true));
        PatternBufferStorage storage = storage(() -> {});
        InventoryCrafting delivered = table(valid);
        NBTTagCompound before = storage.write();
        assertFalse(storage.accept(0, details(valid), delivered, false));
        assertEquals(before, storage.write());
        assertTrue(ItemStack.areItemStacksEqual(valid, delivered.getStackInSlot(0)));
    }

    @Test
    void wrongTypedOrUnknownDropPayloadNeverFallsBackToWater() {
        for (String key : new String[] { "Fluid", "FluidTag", "DisplayOnly" }) {
            ItemStack malformed = drop(10, "tag");
            malformed.getTagCompound().setInteger(key, 1);
            assertThrows(IllegalArgumentException.class, () -> PatternBufferTokens.fluid(malformed, true), key);
        }
        ItemStack unknown = drop(10, "tag");
        unknown.getTagCompound().setString("Fluid", "a6_nonexistent_fluid");
        assertThrows(IllegalArgumentException.class, () -> PatternBufferTokens.fluid(unknown, true));
    }

    @Test
    void packetCountAndPresentFluidFieldsAreStrictAndAmountIsNeverMultiplied() {
        ItemStack packet = new ItemStack(token("fluid_packet"));
        packet.setTagCompound(new NBTTagCompound());
        packet.getTagCompound().setTag("FluidStack",
                new FluidStack(FluidRegistry.WATER, 1347).writeToNBT(new NBTTagCompound()));
        assertEquals(1347, PatternBufferTokens.fluid(packet, true).amount);
        packet.setCount(2);
        assertThrows(IllegalArgumentException.class, () -> PatternBufferTokens.fluid(packet, true));
        packet.setCount(1);
        for (String key : new String[] { "FluidName", "Amount", "Tag" }) {
            ItemStack malformed = packet.copy();
            malformed.getTagCompound().getCompoundTag("FluidStack").setString(key, "wrong");
            assertThrows(IllegalArgumentException.class, () -> PatternBufferTokens.fluid(malformed, true), key);
        }
        for (int amount : new int[] { 0, -1 }) {
            ItemStack malformed = packet.copy();
            malformed.getTagCompound().getCompoundTag("FluidStack").setInteger("Amount", amount);
            assertThrows(IllegalArgumentException.class, () -> PatternBufferTokens.fluid(malformed, true));
        }
    }

    @Test
    void taggedFluidBatchesCannotCrossMatchAndOverflowIsAtomic() {
        PatternBufferStorage storage = storage(() -> {});
        ItemStack red = drop(100, "red"), blue = drop(100, "blue");
        InventoryCrafting table = table(blue);
        NBTTagCompound before = storage.write();
        assertFalse(storage.accept(0, details(red), table, true));
        assertEquals(before, storage.write());
        assertTrue(ItemStack.areItemStacksEqual(blue, table.getStackInSlot(0)));
        ItemStack huge = drop(Integer.MAX_VALUE, "same");
        InventoryCrafting overflow = table(huge, huge);
        assertFalse(storage.accept(0, details(huge, huge), overflow, true));
        assertEquals(before, storage.write());
        assertEquals(Integer.MAX_VALUE, overflow.getStackInSlot(1).getCount());
    }

    @Test
    void persistedAmountsAndListsRejectWrongTypesRatherThanDefaulting() {
        NBTTagCompound valid = PatternBufferCodec.writeItem(new ItemStack(Items.IRON_INGOT, 300));
        assertEquals(1, valid.getCompoundTag("Stack").getByte("Count"));
        assertEquals(300, PatternBufferCodec.readItem(valid).getCount());
        for (String key : new String[] { "Stack", "Amount" }) {
            NBTTagCompound malformed = valid.copy();
            malformed.setString(key, "wrong");
            assertThrows(IllegalArgumentException.class, () -> PatternBufferCodec.readItem(malformed));
        }
        NBTTagCompound nativeIntegerCount = valid.copy();
        nativeIntegerCount.getCompoundTag("Stack").setInteger("Count", 1);
        assertEquals(300, PatternBufferCodec.readItem(nativeIntegerCount).getCount());
        for (String key : new String[] { "id", "Damage", "tag", "ForgeCaps" }) {
            NBTTagCompound malformed = valid.copy();
            malformed.getCompoundTag("Stack").setInteger(key, 1);
            assertThrows(IllegalArgumentException.class, () -> PatternBufferCodec.readItem(malformed), key);
        }
        NBTTagCompound badList = new NBTTagCompound();
        NBTTagList strings = new NBTTagList();
        strings.appendTag(new NBTTagString("not compound"));
        badList.setTag("Items", strings);
        assertThrows(IllegalArgumentException.class, () -> PatternBufferCodec.list(badList, "Items", 100));
    }

    @Test
    void sharedHandlersValidateSimulationAndCannotAcquireOversizedIdentities() {
        PatternBufferStorage storage = storage(() -> {});
        ItemStack huge = new ItemStack(Items.IRON_INGOT);
        huge.setTagCompound(new NBTTagCompound());
        huge.getTagCompound().setByteArray("oversized", new byte[PatternBufferCodec.MAX_STACK_BYTES]);
        assertEquals(1, storage.getSharedItems().insertItem(0, huge, true).getCount());
        assertEquals(1, storage.getSharedItems().insertItem(0, huge, false).getCount());
        assertTrue(storage.getSharedItems().getStackInSlot(0).isEmpty());
        FluidStack fluid = new FluidStack(FluidRegistry.WATER, 1000);
        fluid.tag = huge.getTagCompound().copy();
        assertEquals(0, storage.getSharedFluids().fill(fluid, false));
        assertEquals(0, storage.getSharedFluids().fill(fluid, true));
        assertTrue(storage.isPortable());
    }
}
