package gregtech.api.capability.impl;

import gregtech.api.GregTechAPI;
import gregtech.api.block.machines.BlockMachine;
import gregtech.api.block.machines.MachineItemBlock;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.metatileentity.registry.MTEManager;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMultiSmelter;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityProcessingArray;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static gregtech.api.capability.impl.PaintGroupSelectionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class PaintGroupSpecializedTest {

    private static DualHatchTestItems items;

    @BeforeAll
    static void setup() throws Exception {
        items = new DualHatchTestItems();
    }

    @AfterAll
    static void restore() throws Exception {
        items.close();
    }

    @Test
    void actualMultiSmelterAppendCombinesRecipesOnlyWithinItsSelectedColor() {
        assertTrue(PaintGroupSupport.supports(MetaTileEntityMultiSmelter.class));
        Recipe wheat = RecipeMaps.FURNACE_RECIPES.recipeBuilder().inputs(new ItemStack(Items.WHEAT))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(128).EUt(4).build().getResult();
        Recipe iron = RecipeMaps.FURNACE_RECIPES.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.DIAMOND)).duration(128).EUt(4).build().getResult();
        try {
            assertTrue(RecipeMaps.FURNACE_RECIPES.compileRecipe(wheat));
            assertTrue(RecipeMaps.FURNACE_RECIPES.compileRecipe(iron));
            PaintGroupTestPart red = input(RED, Items.WHEAT, 2), blue = input(BLUE, Items.WHEAT, 7);
            red.getImportItems().setStackInSlot(1, new ItemStack(Items.IRON_INGOT, 3));
            PaintGroupTestPart target = output(RED), other = output(BLUE);
            PaintGroupTestSmelter smelter = new PaintGroupTestSmelter(red, blue, target, other);
            MultiblockRecipeLogic logic = smelter.getRecipeMapWorkable();
            logic.trySearchNewRecipe();
            assertEquals(1, logic.getProgress());
            assertEquals(5, logic.getParallelRecipesPerformed());
            assertTrue(red.getImportItems().getStackInSlot(0).isEmpty());
            assertTrue(red.getImportItems().getStackInSlot(1).isEmpty());
            assertEquals(7, blue.getImportItems().getStackInSlot(0).getCount());
            assertEquals(RED, logic.getActiveOutputColor());
            assertEquals(2, logic.itemOutputs.stream().filter(s -> s.getItem() == Items.GOLD_INGOT)
                    .mapToInt(ItemStack::getCount).sum());
            assertEquals(3, logic.itemOutputs.stream().filter(s -> s.getItem() == Items.DIAMOND)
                    .mapToInt(ItemStack::getCount).sum());
            logic.tryCompleteRecipe();
            assertEquals(5, count(target));
            assertEquals(0, count(other));
        } finally {
            RecipeMaps.FURNACE_RECIPES.removeRecipe(wheat);
            RecipeMaps.FURNACE_RECIPES.removeRecipe(iron);
        }
    }

    @Test
    void actualProcessingArrayDiscoveryKeepsInstalledVoltageParallelAndColorBoundaries() throws Exception {
        assertTrue(PaintGroupSupport.supports(MetaTileEntityProcessingArray.class));
        RecipeMap<SimpleRecipeBuilder> map = map();
        compile(map.recipeBuilder().inputs(new ItemStack(Items.WHEAT)));
        Recipe tooHigh = map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.DIAMOND)).duration(8).EUt(64).build().getResult();
        assertTrue(map.compileRecipe(tooHigh));
        // A local manager permits actual machine item lookup without retaining test registries globally.
        MTEManager previous = GregTechAPI.mteManager;
        Field networkId = MTEManager.class.getDeclaredField("networkId");
        networkId.setAccessible(true);
        int previousNetworkId = networkId.getInt(null);
        Constructor<MTEManager> constructor = MTEManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        try {
            GregTechAPI.mteManager = constructor.newInstance();
            GregTechAPI.mteManager.createRegistry("gregtech");
            MTERegistry registry = GregTechAPI.mteManager.createRegistry("paint_array_test");
            BlockMachine block = new BlockMachine();
            block.setRegistryName("paint_array_test", "machine");
            registry.setBlock(block);
            SimpleMachineMetaTileEntity prototype = new SimpleMachineMetaTileEntity(
                    new ResourceLocation("paint_array_test", "installed"), map, null, 1, true);
            registry.register(0, prototype.metaTileEntityId, prototype);
            MachineItemBlock item = new MachineItemBlock(block);
            item.setRegistryName("paint_array_test", "machine");
            PaintGroupTestPart red = input(RED, Items.IRON_INGOT, 3), blue = input(BLUE, Items.WHEAT, 5);
            PaintGroupTestPart target = output(BLUE), other = output(RED);
            PaintGroupTestArray array = new PaintGroupTestArray(map, new ItemStack(item, 2), red, blue, target, other);
            MultiblockRecipeLogic logic = array.getRecipeMapWorkable();
            assertTrue(logic.shouldSearchForRecipes());
            logic.trySearchNewRecipe();
            assertEquals(1, logic.getProgress());
            assertSame(map, logic.getRecipeMap());
            assertEquals(2, logic.getParallelRecipesPerformed());
            assertEquals(3, red.getImportItems().getStackInSlot(0).getCount(),
                    "128V supply must not bypass installed LV tier");
            assertEquals(3, blue.getImportItems().getStackInSlot(0).getCount());
            assertEquals(BLUE, logic.getActiveOutputColor());
            assertNull(prototype.getWorld());
            logic.tryCompleteRecipe();
            assertEquals(2, count(target));
            assertEquals(0, count(other));
        } finally {
            GregTechAPI.mteManager = previous;
            networkId.setInt(null, previousNetworkId);
        }
    }

    private static int count(PaintGroupTestPart part) {
        int count = 0;
        for (int i = 0; i < part.getExportItems().getSlots(); i++)
            count += part.getExportItems().getStackInSlot(i).getCount();
        return count;
    }
}
