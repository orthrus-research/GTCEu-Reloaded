package gregtech.integration.groovy;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.CraftingDefinitionCompiler;
import gregtech.api.recipes.definition.*;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import com.cleanroommc.groovyscript.compat.vanilla.CraftingRecipe;
import com.cleanroommc.groovyscript.helper.ingredient.OreDictIngredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroovyCraftingDefinitionsTest {

    private static final SourceOrigin SOURCE = new SourceOrigin("groovy", "adapter_test", 12, 1, "recipe", "postInit");
    private static final ResourceLocation NAME = new ResourceLocation("groovy_adapter_test", "recipe");

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void ordinaryShapedInputsKeepNullCellsSnapshotsAndGroovyMatchingPolicy() {
        var input = new ItemStack(Items.PAPER);
        input.setTagCompound(new NBTTagCompound());
        input.getTagCompound().setString("authoring", "ignored by default");
        var output = new ItemStack(Items.DIAMOND, 2);
        var rows = new ArrayList<List<?>>(List.of(Arrays.asList(input, null)));
        var definition = assertInstanceOf(ShapedCraftingDefinition.class,
                GroovyCraftingDefinitions.shaped(SOURCE, NAME, output, rows));
        rows.clear();
        input.setCount(8);
        output.setCount(9);
        assertEquals(2, definition.width());
        assertEquals(1, definition.height());
        assertFalse(definition.mirrored());
        assertNull(definition.inputs().get(1));
        assertEquals(2, definition.output().stack().getCount());
        var nativeInput = assertInstanceOf(ItemIngredient.class, definition.inputs().getFirst());
        assertEquals(CapabilityPolicy.IGNORE, nativeInput.capabilities());
        assertEquals(1, nativeInput.alternatives().getFirst().stack().getCount());
        assertTrue(nativeInput.compile().acceptsStack(new ItemStack(Items.PAPER)));
        assertTrue(CraftingDefinitionCompiler.compile(definition).value().isPresent());
    }

    @Test
    void ingredientCreatedBeforeItsOreRegistrationResolvesWhenUsedAfterRegistration() {
        String name = "overhaulAdapterLateCreatedOre";
        var ingredient = new OreDictIngredient(name);
        assertFalse(OreDictionary.doesOreNameExist(name));
        assertThrows(IllegalArgumentException.class,
                () -> GroovyCraftingDefinitions.recoveryInputs(List.of(ingredient)));
        assertFalse(OreDictionary.doesOreNameExist(name), "Validation must not allocate a missing ore entry");
        OreDictionary.registerOre(name, new ItemStack(Items.PAPER));
        ingredient.setAmount(8);
        var inputs = GroovyCraftingDefinitions.recoveryInputs(List.of(ingredient));
        assertEquals(1, inputs.size());
        assertEquals(8, inputs.getFirst().getAmount());
        assertTrue(inputs.getFirst().acceptsStack(new ItemStack(Items.PAPER)));
        ingredient.setAmount(1);
        assertInstanceOf(ShapelessCraftingDefinition.class,
                GroovyCraftingDefinitions.shapeless(SOURCE, NAME, new ItemStack(Items.DIAMOND), List.of(ingredient)));
    }

    @Test
    void intentionalZeroQuantitiesAndNullCellsDoNotBecomeMissingIngredientErrors() {
        String name = "overhaulAdapterZeroOre";
        OreDictionary.registerOre(name, new ItemStack(Items.PAPER));
        var zero = new OreDictIngredient(name);
        zero.setAmount(0);
        var inputs = GroovyCraftingDefinitions.recoveryInputs(Arrays.asList(null, zero, new ItemStack(Items.COAL, 0),
                List.of(new ItemStack(Items.PAPER, 3))));
        assertEquals(1, inputs.size());
        assertEquals(3, inputs.getFirst().getAmount());
        var missing = new OreDictIngredient("overhaulAdapterNeverRegisteredOre");
        missing.setAmount(8);
        assertThrows(IllegalArgumentException.class, () -> GroovyCraftingDefinitions.shapeless(SOURCE, NAME,
                new ItemStack(Items.DIAMOND), List.of(missing)));
        assertThrows(IllegalArgumentException.class, () -> GroovyCraftingDefinitions.shaped(SOURCE, NAME,
                new ItemStack(Items.DIAMOND), List.of(List.of(missing))));
        assertThrows(IllegalArgumentException.class, () -> GroovyCraftingDefinitions.recoveryInputs(List.of(missing)));
        assertFalse(OreDictionary.doesOreNameExist(missing.getOreDict()));
    }

    @Test
    void customConditionsAndRemaindersRemainExplicitAndSurviveAuthoringMutation() {
        String name = "overhaulAdapterCustomOre";
        OreDictionary.registerOre(name, new ItemStack(Items.PAPER));
        var custom = new OreDictIngredient(name)
                .when(stack -> stack.hasTagCompound() && stack.getTagCompound().getBoolean("allowed"))
                .transform(stack -> new ItemStack(Items.STICK));
        var rows = new ArrayList<List<?>>(List.of(Arrays.asList(custom, null)));
        var definition = assertInstanceOf(CustomCraftingDefinition.class,
                GroovyCraftingDefinitions.shaped(SOURCE, NAME, new ItemStack(Items.DIAMOND), rows));
        rows.clear();
        custom.setAmount(7);
        var recipe = assertInstanceOf(CraftingRecipe.class, definition.factory().get());
        assertTrue(recipe.canFit(2, 1));
        assertFalse(recipe.canFit(1, 1));
        assertNotSame(recipe, definition.factory().get());
        // Exercise the captured engine ingredient directly: the unit bootstrap does not apply ItemStack mixins.
        var captured = recipe.getMatchingList(grid(new ItemStack(Items.PAPER)));
        assertTrue(captured.isEmpty());
        var allowed = new ItemStack(Items.PAPER);
        allowed.setTagCompound(new NBTTagCompound());
        allowed.getTagCompound().setBoolean("allowed", true);
        var matches = recipe.getMatchingList(grid(allowed));
        assertEquals(1, matches.size());
        var ingredient = matches.getFirst().getRecipeIngredient();
        assertEquals(1, ingredient.getAmount());
        assertEquals(Items.STICK, ingredient.applyTransform(allowed).getItem());
    }

    private static net.minecraft.inventory.InventoryCrafting grid(ItemStack input) {
        var inventory = new net.minecraft.inventory.InventoryCrafting(
                new net.minecraft.inventory.ContainerDispenser(new net.minecraft.entity.player.InventoryPlayer(null),
                        new net.minecraft.inventory.InventoryBasic("adapter", false, 9)),
                2, 1);
        inventory.setInventorySlotContents(0, input);
        return inventory;
    }
}
