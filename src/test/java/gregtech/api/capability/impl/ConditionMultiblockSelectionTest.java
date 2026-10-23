package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.block.machines.BlockMachine;
import gregtech.api.block.machines.MachineItemBlock;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Biomes;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ConditionMultiblockSelectionTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void distinctCachedBusConditionFallsBackWithoutConsumingTheRejectedBus() {
        RecipeMap<SimpleRecipeBuilder> map = ConditionRecipeSelectionTest.map();
        Recipe blocked = ConditionRecipeSelectionTest.recipe(map).conditionHeight(90, 110).build().getResult();
        Recipe allowed = map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT)).outputs(new ItemStack(Items.APPLE))
                .EUt(1).duration(8).conditionHeight(40, 70).build().getResult();
        assertTrue(map.compileRecipe(blocked));
        assertTrue(map.compileRecipe(allowed));
        RecipeCandidateTestController controller = new RecipeCandidateTestController(map);
        IdleRecipeTestMachine.attachHolder(controller).setPos(new BlockPos(32, 64, 32));
        ItemStackHandler first = new ItemStackHandler(1);
        first.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 8));
        ItemStackHandler second = new ItemStackHandler(1);
        second.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 8));
        RecipeCandidateDistinctLogic logic = new RecipeCandidateDistinctLogic(controller, Arrays.asList(first, second));
        logic.previousRecipe = blocked;
        logic.trySearchNewRecipeDistinct();
        assertSame(allowed, logic.previousRecipe);
        assertEquals(8, first.getStackInSlot(0).getCount());
        assertEquals(7, second.getStackInSlot(0).getCount());
        assertEquals(1, controller.checks);
        assertEquals(1, controller.commits);
    }

    @Test
    void actualProcessingArrayDiscoveryLocatesItsClonedDelegateAtTheControllerWithoutMutatingPrototype() {
        RecipeMap<SimpleRecipeBuilder> map = ConditionRecipeSelectionTest.map();
        MTERegistry registry = GregTechAPI.mteManager.createRegistry("condition_array_test");
        BlockMachine block = new BlockMachine();
        block.setRegistryName("condition_array_test", "machine");
        registry.setBlock(block);
        SimpleMachineMetaTileEntity prototype = new SimpleMachineMetaTileEntity(
                new ResourceLocation("condition_array_test", "installed"), map, null, 1, true);
        registry.register(0, prototype.metaTileEntityId, prototype);
        MachineItemBlock item = new MachineItemBlock(block);
        item.setRegistryName("condition_array_test", "machine");
        ConditionProcessingArray array = new ConditionProcessingArray();
        array.environment.biomes.put(array.position, Biomes.DESERT);
        array.machineSlot.setStackInSlot(0, new ItemStack(item, 1, 0));
        array.discoverInstalledMachine();
        Recipe atController = ConditionRecipeSelectionTest.recipe(map).conditionHeight(180, 180)
                .conditionBiome("minecraft:desert").build().getResult();
        assertTrue(array.getRecipeMapWorkable().evaluateRecipeEligibility(atController).isEmpty(),
                "Both controller and actual installed-machine delegate must see the non-origin height and biome");
        Recipe atOrigin = ConditionRecipeSelectionTest.recipe(map).conditionHeight(0, 0)
                .conditionBiome("minecraft:plains").build().getResult();
        assertFalse(array.getRecipeMapWorkable().evaluateRecipeEligibility(atOrigin).isEmpty());
        assertNull(prototype.getWorld());
        assertNull(prototype.getPos());
        assertSame(map, array.getRecipeMapWorkable().getRecipeMap());
        assertEquals(1, array.machineSlot.getStackInSlot(0).getCount());
    }
}
