package gregtech;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.block.machines.BlockMachine;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.category.GTRecipeCategory;
import gregtech.common.ConfigHolder;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityElectricBlastFurnace;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameStateSnapshotTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void configurationValuesNestedOptionsAndArrayElementsComeBack() {
        boolean research = ConfigHolder.machines.enableResearch;
        int ratio = ConfigHolder.compat.energy.feToEuRatio;
        String[] priorities = ConfigHolder.compat.modPriorities;
        List<String> priorityValues = List.of(priorities);
        GameStateSnapshot snapshot = GameStateSnapshot.take();

        ConfigHolder.machines.enableResearch = !research;
        ConfigHolder.compat.energy.feToEuRatio = ratio + 3;
        ConfigHolder.compat.modPriorities[0] = "snapshot_test";
        snapshot.restore();

        assertEquals(research, ConfigHolder.machines.enableResearch);
        assertEquals(ratio, ConfigHolder.compat.energy.feToEuRatio);
        assertEquals(priorityValues, List.of(ConfigHolder.compat.modPriorities));
        assertSame(priorities, ConfigHolder.compat.modPriorities, "the array a test held keeps its contents too");
    }

    @Test
    void aMapRegisteredUnderARealNameGivesWayAndTestMapsAndRecipesGo() {
        RecipeMap<?> blast = RecipeMaps.BLAST_RECIPES;
        int macerated = RecipeMaps.MACERATOR_RECIPES.getRecipeList().size();
        GameStateSnapshot snapshot = GameStateSnapshot.take();

        new RecipeMapBuilder<>(blast.unlocalizedName, new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        new RecipeMapBuilder<>("snapshot_test_map", new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        RecipeMaps.MACERATOR_RECIPES.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE))
                .outputs(new ItemStack(Blocks.GRAVEL)).EUt(2).duration(20).buildAndRegister();
        assertNotSame(blast, RecipeMap.getByName(blast.unlocalizedName));
        assertEquals(macerated + 1, RecipeMaps.MACERATOR_RECIPES.getRecipeList().size());
        snapshot.restore();

        assertSame(blast, RecipeMap.getByName(blast.unlocalizedName));
        assertNull(RecipeMap.getByName("snapshot_test_map"));
        assertNull(GTRecipeCategory.getByName("gregtech.snapshot_test_map"));
        assertEquals(macerated, RecipeMaps.MACERATOR_RECIPES.getRecipeList().size());
    }

    @Test
    void createdRegistriesGoAndAMachineOverARealKeyIsUnregistered() {
        MTERegistry machines = GregTechAPI.mteManager.getRegistry(GTValues.MODID);
        int registered = machines.getKeys().size();
        ResourceLocation blastKey = MetaTileEntities.ELECTRIC_BLAST_FURNACE.metaTileEntityId;
        int bus = MultiblockAbility.REGISTRY.get(MultiblockAbility.IMPORT_ITEMS).size();
        GameStateSnapshot snapshot = GameStateSnapshot.take();

        MTERegistry created = GregTechAPI.mteManager.createRegistry("snapshot_test");
        created.setBlock(new BlockMachine());
        MetaTileEntities.registerMetaTileEntity(0,
                new MetaTileEntityItemBus(new ResourceLocation("snapshot_test", "bus"), 1, false));
        MetaTileEntity shadow = new MetaTileEntityElectricBlastFurnace(blastKey);
        machines.register(Short.MAX_VALUE - 1, blastKey, shadow);
        assertSame(shadow, machines.getObject(blastKey));
        snapshot.restore();

        assertThrows(IllegalArgumentException.class, () -> GregTechAPI.mteManager.getRegistry("snapshot_test"));
        assertSame(MetaTileEntities.ELECTRIC_BLAST_FURNACE, machines.getObject(blastKey));
        assertNull(machines.getObjectById(Short.MAX_VALUE - 1));
        assertEquals(registered, machines.getKeys().size());
        assertEquals(bus, MultiblockAbility.REGISTRY.get(MultiblockAbility.IMPORT_ITEMS).size());
    }
}
