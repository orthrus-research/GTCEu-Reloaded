package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.definition.CapabilityPolicy;
import gregtech.api.recipes.definition.CraftingDefinition;
import gregtech.api.recipes.definition.ItemIngredient;
import gregtech.api.recipes.definition.ItemInput;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.NbtPolicy;
import gregtech.api.recipes.definition.OreIngredient;
import gregtech.api.recipes.definition.ShapedCraftingDefinition;
import gregtech.api.recipes.definition.ShapelessCraftingDefinition;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerDispenser;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CraftingDefinitionCompilerTest {

    private static final SourceOrigin SOURCE = new SourceOrigin("java", "crafting_test", 12, 4, "recipe", "postInit");
    private static final ResourceLocation NAME = new ResourceLocation("crafting_definition_test", "recipe");

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void shapedNullCellsOffsetsAndMirrorPolicyUseExactGridMatching() {
        var inputs = Arrays.<ItemInput>asList(exact(Items.COAL), null, exact(Items.DIAMOND), exact(Items.PAPER));
        var recipe = compile(shaped(2, 2, true, inputs));
        var inventory = grid(3, 3);
        inventory.setInventorySlotContents(4, new ItemStack(Items.COAL));
        inventory.setInventorySlotContents(7, new ItemStack(Items.DIAMOND));
        inventory.setInventorySlotContents(8, new ItemStack(Items.PAPER));
        assertTrue(recipe.matches(inventory, null));
        inventory.setInventorySlotContents(0, new ItemStack(Items.STICK));
        assertFalse(recipe.matches(inventory, null));
        inventory.setInventorySlotContents(0, ItemStack.EMPTY);
        inventory.setInventorySlotContents(4, ItemStack.EMPTY);
        inventory.setInventorySlotContents(5, new ItemStack(Items.COAL));
        inventory.setInventorySlotContents(7, new ItemStack(Items.PAPER));
        inventory.setInventorySlotContents(8, new ItemStack(Items.DIAMOND));
        assertTrue(recipe.matches(inventory, null));
        assertFalse(compile(shaped(2, 2, false, inputs)).matches(inventory, null));
        assertFalse(recipe.canFit(1, 2));
        assertEquals(2, ((IShapedRecipe) recipe).getRecipeWidth());
        assertEquals(2, ((IShapedRecipe) recipe).getRecipeHeight());
        assertTrue(recipe.getIngredients().get(1).apply(ItemStack.EMPTY));
    }

    @Test
    void overlappingShapelessAlternativesPairEveryInputAndRetainTheCorrectSlot() {
        var broad = new ItemIngredient(List.of(ItemValue.of(new ItemStack(Items.COAL)),
                ItemValue.of(new ItemStack(Items.DIAMOND))), 1, true, NbtPolicy.any(), CapabilityPolicy.MATCH);
        var retained = new ItemIngredient(List.of(ItemValue.of(new ItemStack(Items.COAL))), 1, false,
                NbtPolicy.any(), CapabilityPolicy.MATCH);
        var recipe = compile(shapeless(List.of(broad, retained)));
        for (boolean reversed : List.of(false, true)) {
            var inventory = grid(3, 3);
            int coal = reversed ? 8 : 0;
            int diamond = reversed ? 0 : 8;
            inventory.setInventorySlotContents(coal, new ItemStack(Items.COAL, 9));
            inventory.setInventorySlotContents(diamond, new ItemStack(Items.DIAMOND));
            assertTrue(recipe.matches(inventory, null));
            var remaining = recipe.getRemainingItems(inventory);
            assertEquals(Items.COAL, remaining.get(coal).getItem());
            assertEquals(1, remaining.get(coal).getCount());
            assertTrue(remaining.get(diamond).isEmpty());
            assertEquals(9, inventory.getStackInSlot(coal).getCount());
            inventory.setInventorySlotContents(coal, new ItemStack(Items.DIAMOND));
            assertFalse(recipe.matches(inventory, null));
            assertTrue(recipe.getRemainingItems(inventory).stream().allMatch(ItemStack::isEmpty));
        }
    }

    @Test
    void nbtPoliciesSurviveCompilationAndDisplayMutation() {
        var tag = new NBTTagCompound();
        tag.setString("grade", "pure");
        var stack = new ItemStack(Items.COAL);
        stack.setTagCompound(tag);
        var recipe = compile(shapeless(List.of(ItemIngredient.exact(stack))));
        stack.getTagCompound().setString("grade", "changed");
        var inventory = grid(2, 2);
        inventory.setInventorySlotContents(0, stack);
        assertFalse(recipe.matches(inventory, null));
        var display = recipe.getIngredients().getFirst().getMatchingStacks();
        display[0].getTagCompound().setString("grade", "changed");
        recipe.getIngredients().clear();
        var correct = new ItemStack(Items.COAL, 32);
        var correctTag = new NBTTagCompound();
        correctTag.setString("grade", "pure");
        correct.setTagCompound(correctTag);
        inventory.setInventorySlotContents(0, correct);
        assertTrue(recipe.matches(inventory, null));
        correct.getTagCompound().setBoolean("extra", true);
        assertFalse(recipe.matches(inventory, null));
        var required = new NBTTagCompound();
        required.setString("grade", "pure");
        var contains = new ItemIngredient(List.of(ItemValue.of(new ItemStack(Items.COAL))), 1, true,
                NbtPolicy.contains(required), CapabilityPolicy.IGNORE);
        assertTrue(compile(shapeless(List.of(contains))).matches(inventory, null));
    }

    @Test
    void ordinaryContainerItemsAndNativeToolDamagePreserveInputStacks() {
        var bucketRecipe = compile(shapeless(List.of(exact(Items.WATER_BUCKET))));
        var inventory = grid(2, 2);
        inventory.setInventorySlotContents(3, new ItemStack(Items.WATER_BUCKET));
        assertTrue(bucketRecipe.matches(inventory, null));
        assertEquals(Items.BUCKET, bucketRecipe.getRemainingItems(inventory).get(3).getItem());
        assertEquals(Items.WATER_BUCKET, inventory.getStackInSlot(3).getItem());
        Item tool = new Item() {

            @Override
            public boolean hasContainerItem(ItemStack stack) {
                return true;
            }

            @Override
            public ItemStack getContainerItem(ItemStack stack) {
                stack.setItemDamage(stack.getItemDamage() + 1);
                return stack;
            }
        }.setMaxDamage(10);
        var original = new ItemStack(tool, 1, 2);
        var toolRecipe = compile(shapeless(List.of(ItemIngredient.exact(original))));
        inventory.setInventorySlotContents(3, original);
        assertTrue(toolRecipe.matches(inventory, null));
        assertEquals(3, toolRecipe.getRemainingItems(inventory).get(3).getItemDamage());
        assertEquals(2, original.getItemDamage());
    }

    @Test
    void definitionsAndOutputsAreSnapshotsAndCompilationDoesNotRegister() {
        var output = new ItemStack(Items.EMERALD, 2);
        var inputs = new ArrayList<ItemInput>(Arrays.asList(exact(Items.COAL), null));
        var definition = new ShapedCraftingDefinition(SOURCE, NAME, "test_group", ItemValue.of(output),
                2, 1, false, inputs);
        inputs.clear();
        assertEquals(2, definition.inputs().size());
        output.setCount(20);
        assertThrows(UnsupportedOperationException.class, () -> definition.inputs().clear());
        var keys = List.copyOf(ForgeRegistries.RECIPES.getKeys());
        var recipe = compile(definition);
        assertEquals(keys, List.copyOf(ForgeRegistries.RECIPES.getKeys()));
        assertFalse(ForgeRegistries.RECIPES.containsKey(NAME));
        recipe.getRecipeOutput().setCount(30);
        recipe.getCraftingResult(grid(2, 2)).setCount(40);
        assertEquals(2, recipe.getRecipeOutput().getCount());
        assertEquals("test_group", recipe.getGroup());
        assertEquals(NAME, recipe.getRegistryName());
    }

    @Test
    void invalidDimensionsEmptySlotsAndAmountsProduceSourceDiagnosticsWithoutARecipe() {
        assertFalse(OreDictionary.doesOreNameExist("missingCraftingDefinitionTestOre"));
        var invalid = List.<CraftingDefinition>of(
                shaped(2, 2, false, List.of(exact(Items.COAL))),
                shaped(Integer.MAX_VALUE, Integer.MAX_VALUE, false, List.of(exact(Items.COAL))),
                shaped(1, 1, false, Arrays.asList((ItemInput) null)),
                shapeless(List.of()),
                shapeless(Arrays.asList(exact(Items.COAL), null)),
                shapeless(List.of(ItemIngredient.exact(new ItemStack(Items.COAL, 2)))),
                shapeless(List.of(new OreIngredient("missingCraftingDefinitionTestOre", 1, true, NbtPolicy.any()))),
                new ShapelessCraftingDefinition(SOURCE, NAME, "", ItemValue.of(ItemStack.EMPTY),
                        List.of(exact(Items.COAL))));
        for (var definition : invalid) {
            var result = CraftingDefinitionCompiler.compile(definition);
            assertTrue(result.value().isEmpty());
            assertFalse(result.diagnostics().isEmpty());
            result.diagnostics().forEach(diagnostic -> {
                assertEquals(SOURCE, diagnostic.origin());
                assertTrue(diagnostic.code().startsWith("crafting."));
                assertFalse(diagnostic.field().isBlank());
            });
        }
        assertFalse(OreDictionary.doesOreNameExist("missingCraftingDefinitionTestOre"));
    }

    @Test
    void oreIngredientsAndLargerNativeGridsRemainSupported() {
        OreDictionary.registerOre("craftingDefinitionTestOre", new ItemStack(Items.COAL));
        var ore = new OreIngredient("craftingDefinitionTestOre", 1, true, NbtPolicy.any());
        var recipe = compile(shaped(4, 1, false, List.of(ore, ore, ore, ore)));
        var inventory = grid(4, 1);
        for (int slot = 0; slot < 4; slot++) inventory.setInventorySlotContents(slot, new ItemStack(Items.COAL));
        assertTrue(recipe.matches(inventory, null));
        assertFalse(recipe.canFit(3, 3));
        var shapeless = compile(shapeless(List.of(ore, ore, ore, ore)));
        assertTrue(shapeless.matches(inventory, null));
        assertTrue(shapeless.canFit(2, 2));
        assertFalse(shapeless.canFit(Integer.MAX_VALUE, -1));
        inventory.setInventorySlotContents(0, ItemStack.EMPTY);
        assertFalse(shapeless.matches(inventory, null));
    }

    private static ItemInput exact(Item item) {
        return ItemIngredient.exact(new ItemStack(item));
    }

    private static ShapedCraftingDefinition shaped(int width, int height, boolean mirrored, List<ItemInput> inputs) {
        return new ShapedCraftingDefinition(SOURCE, NAME, "", ItemValue.of(new ItemStack(Items.EMERALD)),
                width, height, mirrored, inputs);
    }

    private static ShapelessCraftingDefinition shapeless(List<ItemInput> inputs) {
        return new ShapelessCraftingDefinition(SOURCE, NAME, "", ItemValue.of(new ItemStack(Items.EMERALD)), inputs);
    }

    private static IRecipe compile(CraftingDefinition definition) {
        var result = CraftingDefinitionCompiler.compile(definition);
        assertTrue(result.value().isPresent(), () -> result.diagnostics().toString());
        return result.value().orElseThrow();
    }

    private static InventoryCrafting grid(int width, int height) {
        return new InventoryCrafting(new ContainerDispenser(new InventoryPlayer(null),
                new InventoryBasic("crafting_probe", false, 9)), width, height);
    }
}
