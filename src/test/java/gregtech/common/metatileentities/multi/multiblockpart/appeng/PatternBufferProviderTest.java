package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.RecipeInputView;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferCodec;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferHost;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferLink;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferStorage;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferTokens;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.networking.crafting.ICraftingProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.multi.multiblockpart.appeng.pattern.PatternBufferTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PatternBufferProviderTest {

    private static int nextMap;

    @BeforeAll
    static void setup() {
        bootstrap();
    }

    private static PatternBufferTestPart attached(PatternBufferTestWorld world, PatternBufferStorage storage) {
        PatternBufferTestPart buffer = new PatternBufferTestPart();
        world.put(BlockPos.ORIGIN, buffer);
        buffer.getPatternStorage().read(storage.write());
        return buffer;
    }

    @Test
    void pairedPrivateBatchesNeverCrossMatchAndExplicitSharedCatalystsStayPhysical() {
        PatternBufferStorage ledger = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT), red = drop(100, "red"), blue = drop(100, "blue");
        assertTrue(ledger.accept(0, details(iron, red), table(iron, red), true));
        assertTrue(ledger.accept(0, details(new ItemStack(Items.FEATHER), blue),
                table(new ItemStack(Items.FEATHER), blue), true));
        PatternBufferTestPart buffer = attached(new PatternBufferTestWorld(), ledger);
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("pattern_provider_" + nextMap++,
                new SimpleRecipeBuilder())
                        .itemInputs(2).itemOutputs(1).fluidInputs(1).build();
        Recipe forbidden = map.recipeBuilder().inputs(iron).fluidInputs(PatternBufferTokens.fluid(blue, true))
                .outputs(new ItemStack(Items.DIAMOND)).duration(1).EUt(1).build().getResult();
        List<RecipeInputView> views = buffer.getRecipeInputViews();
        assertEquals(2, views.size());
        for (RecipeInputView view : views) assertFalse(forbidden.matches(false, view.getItems(), view.getFluids()));
        buffer.getPatternStorage().getSharedItems().insertItem(0, new ItemStack(Items.STICK), false);
        Recipe allowed = map.recipeBuilder().inputs(iron).notConsumable(new ItemStack(Items.STICK))
                .fluidInputs(PatternBufferTokens.fluid(red, true)).outputs(new ItemStack(Items.DIAMOND))
                .duration(1).EUt(1).build().getResult();
        RecipeInputView selected = buffer.getRecipeInputViews().get(0);
        assertTrue(buffer.isRecipeInputViewValid(selected));
        assertTrue(allowed.matches(true, selected.getItems(), selected.getFluids()));
        assertEquals(2, buffer.getPatternStorage().getBatches().size(), "Matching itself does not retire a batch");
        buffer.onRecipeInputViewStarted(selected);
        assertEquals(1, buffer.getPatternStorage().getBatches().size());
        assertEquals(1, buffer.getPatternStorage().getSharedItems().getStackInSlot(0).getCount());
        assertEquals(100, buffer.getPatternStorage().getBatches().get(0).getFluids().getTankAt(0).getFluidAmount());
        assertFalse(buffer.isRecipeInputViewValid(selected));
    }

    @Test
    void staleViewsRejectReloadedHandlersRefundDisableAndDetachment() {
        PatternBufferStorage ledger = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 3);
        assertTrue(ledger.accept(0, details(iron), table(iron), false));
        PatternBufferTestPart buffer = attached(new PatternBufferTestWorld(), ledger);
        RecipeInputView original = buffer.getRecipeInputViews().get(0);
        NBTTagCompound saved = buffer.getPatternStorage().write();
        buffer.getPatternStorage().read(saved);
        RecipeInputView loaded = buffer.getRecipeInputViews().get(0);
        assertEquals(original.getSelectionKey(), loaded.getSelectionKey());
        assertFalse(buffer.isRecipeInputViewValid(original), "Same key must not admit detached pre-reload handlers");
        assertTrue(buffer.isRecipeInputViewValid(loaded));
        buffer.setWorkingEnabled(false);
        assertFalse(buffer.isRecipeInputViewValid(loaded));
        assertTrue(buffer.getRecipeInputViews().isEmpty());
        buffer.setWorkingEnabled(true);
        assertFalse(buffer.isRecipeInputViewValid(loaded));
        loaded = buffer.getRecipeInputViews().get(0);
        assertTrue(buffer.isRecipeInputViewValid(loaded));
        buffer.getPatternStorage().requestRefund(0);
        assertFalse(buffer.isRecipeInputViewValid(loaded));
        assertTrue(buffer.getRecipeInputViews().isEmpty());
        buffer.getPatternStorage().read(saved);
        RecipeInputView beforeDetach = buffer.getRecipeInputViews().get(0);
        buffer.controller = null;
        assertFalse(buffer.isRecipeInputViewValid(beforeDetach));
    }

    @Test
    void physicalAbilitiesAreDistinctEmptyPlaceholdersAndAutomationExposesOnlySharedStores() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferStorage ledger = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 4);
        assertTrue(ledger.accept(0, details(iron), table(iron), false));
        PatternBufferTestPart first = attached(world, ledger), second = new PatternBufferTestPart();
        MetaTileEntityMEPatternBufferProxy proxy = new MetaTileEntityMEPatternBufferProxy(
                gregtechId("pattern_proxy_test"));
        Object previousItems = null, previousFluid = null;
        for (var part : new gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart<?>[] { first, second,
                proxy }) {
            assertTrue(part.requiresAllAbilityLimits());
            AbilityInstances items = new AbilityInstances(MultiblockAbility.IMPORT_ITEMS);
            AbilityInstances fluids = new AbilityInstances(MultiblockAbility.IMPORT_FLUIDS);
            part.registerAbilities(items);
            part.registerAbilities(fluids);
            assertEquals(1, items.size());
            assertEquals(1, fluids.size());
            assertEquals(0, ((IItemHandler) items.get(0)).getSlots());
            assertEquals(0, ((IFluidTank) fluids.get(0)).getCapacity());
            assertNotSame(previousItems, items.get(0));
            assertNotSame(previousFluid, fluids.get(0));
            previousItems = items.get(0);
            previousFluid = fluids.get(0);
        }
        assertSame(first.getPatternStorage().getSharedItems(),
                first.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null));
        assertSame(first.getPatternStorage().getSharedFluids(),
                first.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null));
        assertEquals(4, first.getRecipeInputViews().get(0).getItems().getStackInSlot(0).getCount());
    }

    @Test
    void proxyLinkChecksLoadedDimensionOwnerIdentityAndIncarnationBeforeExposingStorage() {
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        PatternBufferTestPart buffer = attached(world, storage(() -> {}));
        PatternBufferLink link = PatternBufferLink.capture(buffer);
        assertSame(buffer, link.resolve(world, buffer.owner));
        assertEquals(link.write(), PatternBufferLink.read(link.write()).write());
        world.loaded = false;
        int lookups = world.lookups;
        assertNull(link.resolve(world, buffer.owner));
        assertEquals(lookups, world.lookups, "Unloaded chunks must not reach a tile lookup");
        world.loaded = true;
        assertNull(link.resolve(world, UUID.randomUUID()));
        assertEquals(lookups, world.lookups);
        PatternBufferTestWorld anotherDimension = new PatternBufferTestWorld();
        anotherDimension.provider.setDimension(world.provider.getDimension() - 1);
        assertNull(link.resolve(anotherDimension, buffer.owner));
        assertEquals(0, anotherDimension.lookups);
        buffer.getPatternStorage().renewIncarnation();
        assertNull(link.resolve(world, buffer.owner));
        PatternBufferLink replacement = PatternBufferLink.capture(buffer);
        buffer.owner = UUID.randomUUID();
        assertNull(replacement.resolve(world, buffer.owner));
    }

    @Test
    void repeatedPortableSnapshotsDoNotClearOwnedMaterialsAndPlacementChangesOnlyLinkIncarnation() {
        PatternBufferStorage ledger = storage(() -> {});
        ItemStack iron = new ItemStack(Items.IRON_INGOT, 300), fluid = drop(1347, "portable");
        assertTrue(ledger.accept(0, details(iron, fluid), table(iron, fluid), true));
        PatternBufferTestPart buffer = attached(new PatternBufferTestWorld(), ledger);
        NBTTagCompound first = new NBTTagCompound(), second = new NBTTagCompound();
        buffer.writeItemStackData(first);
        buffer.writeItemStackData(second);
        assertTrue(buffer.keepsInventory());
        assertEquals(first, second);
        assertEquals(ledger.write(), buffer.getPatternStorage().write());
        PatternBufferTestPart restored = new PatternBufferTestPart();
        restored.initFromItemStackData(first);
        assertEquals(ledger.getIdentity(), restored.getPatternStorage().getIdentity());
        assertNotEquals(ledger.getIncarnation(), restored.getPatternStorage().getIncarnation());
        NBTTagCompound expected = ledger.write(), actual = restored.getPatternStorage().write();
        expected.removeTag("IncarnationMost");
        expected.removeTag("IncarnationLeast");
        actual.removeTag("IncarnationMost");
        actual.removeTag("IncarnationLeast");
        assertEquals(expected, actual);
    }

    @Test
    void overlimitRecoveryCannotEnterAnOrdinaryItemPacketAndMalformedPortableRootStaysInactive() {
        PatternBufferTestPart buffer = attached(new PatternBufferTestWorld(), storage(() -> {}));
        NBTTagCompound unknown = new NBTTagCompound();
        unknown.setByteArray("Owned", new byte[PatternBufferCodec.MAX_PORTABLE_BYTES + 1]);
        buffer.getPatternStorage().read(unknown);
        assertTrue(buffer.isRecoveryBlocked());
        assertEquals(-1, buffer.getBlockHardness());
        assertTrue(buffer.getWitherProof());
        NBTTagCompound portable = new NBTTagCompound();
        buffer.writeItemStackData(portable);
        assertTrue(portable.getBoolean("PatternRecoveryBlocked"));
        assertFalse(portable.hasKey("PatternBuffer"));
        assertEquals(unknown, buffer.getPatternStorage().write());
        PatternBufferTestPart marker = new PatternBufferTestPart();
        marker.initFromItemStackData(portable);
        assertTrue(marker.getPatternStorage().isQuarantined(), "A recovery marker is never an empty usable buffer");
        assertFalse(marker.getPatternStorage().canAccept());
        assertEquals(portable, marker.getPatternStorage().write());
        NBTTagCompound wrongType = new NBTTagCompound();
        wrongType.setString("PatternBuffer", "unknown payload");
        PatternBufferTestPart restored = new PatternBufferTestPart();
        restored.initFromItemStackData(wrongType);
        assertTrue(restored.getPatternStorage().isQuarantined());
        assertEquals(wrongType, restored.getPatternStorage().write());
    }

    @Test
    void craftingNodeWrapperRequiresARealHolderAndOtherAePartsKeepTheirExistingHost() {
        PatternBufferTestPart buffer = new PatternBufferTestPart();
        assertNull(buffer.gridOwner());
        PatternBufferTestWorld world = new PatternBufferTestWorld();
        MetaTileEntityHolder holder = world.put(BlockPos.ORIGIN, buffer);
        assertTrue(buffer.gridOwner() instanceof PatternBufferHost);
        assertTrue(buffer.gridOwner() instanceof ICraftingProvider);
        assertSame(buffer.gridOwner(), buffer.gridOwner());
        assertEquals(holder.getLocation(), buffer.gridOwner().getLocation());
        StockingTestBus existing = new StockingTestBus();
        MetaTileEntityHolder ordinaryHolder = world.put(BlockPos.ORIGIN.east(), existing);
        assertSame(ordinaryHolder, existing.getGridProxyOwner());
        assertFalse(existing.getGridProxyOwner() instanceof ICraftingProvider);
    }
}
