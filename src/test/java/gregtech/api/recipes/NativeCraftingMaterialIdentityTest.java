package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.recipes.fixtures.RecyclingUnificationFixture;
import gregtech.api.recipes.ingredients.GTRecipeOreInput;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.registry.IMaterialRegistryManager;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.ItemAndMetadata;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.core.unification.material.internal.MaterialRegistryManager;

import net.minecraft.client.util.RecipeItemHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerDispenser;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistryModifiable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeCraftingMaterialIdentityTest {

    private static final AtomicInteger IDS = new AtomicInteger();
    private final List<ResourceLocation> recipes = new ArrayList<>();
    private IMaterialRegistryManager previous;
    private RecyclingUnificationFixture fixture;
    private Material core;
    private Material pack;
    private String ore;

    @BeforeEach
    void setup() throws Exception {
        Bootstrap.perform();
        fixture = new RecyclingUnificationFixture(List.of(), List.of(OrePrefix.dust));
        previous = GregTechAPI.materialManager;
        var constructor = MaterialRegistryManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var manager = constructor.newInstance();
        GregTechAPI.materialManager = manager;
        manager.unfreezeRegistries();
        String name = "crafting_identity_" + IDS.incrementAndGet();
        core = Material.builder(51, new ResourceLocation("gregtech", name)).dust().build();
        pack = Material.builder(52, new ResourceLocation("susy", name)).dust().build();
        manager.closeRegistries();
        manager.freezeRegistries();
        ore = new UnificationEntry(OrePrefix.dust, core).toString();
    }

    @AfterEach
    void restore() throws Exception {
        recipes.forEach(((IForgeRegistryModifiable<IRecipe>) ForgeRegistries.RECIPES)::remove);
        OreDictionary.getOres(ore).clear();
        OreDictionary.rebakeMap();
        GregTechAPI.materialManager = previous;
        fixture.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void qualifiedRecipesKeepOwnersAndFollowLateMembership(boolean shaped) {
        register(new ItemStack(Items.COAL), core);
        register(new ItemStack(Items.DIAMOND), pack);
        IRecipe coreRecipe = recipe(shaped, new UnificationEntry(OrePrefix.dust, core));
        IRecipe packRecipe = recipe(shaped, new UnificationEntry(OrePrefix.dust, pack));
        IRecipe broad = recipe(shaped, ore);
        assertTrue(coreRecipe.matches(grid(new ItemStack(Items.COAL)), null));
        assertFalse(coreRecipe.matches(grid(new ItemStack(Items.DIAMOND)), null));
        assertTrue(packRecipe.matches(grid(new ItemStack(Items.DIAMOND)), null));
        assertFalse(packRecipe.matches(grid(new ItemStack(Items.COAL)), null));
        assertTrue(broad.matches(grid(new ItemStack(Items.COAL)), null));
        assertTrue(broad.matches(grid(new ItemStack(Items.DIAMOND)), null));
        register(new ItemStack(Items.EMERALD), core);
        assertTrue(coreRecipe.matches(grid(new ItemStack(Items.EMERALD)), null));
        assertFalse(packRecipe.matches(grid(new ItemStack(Items.EMERALD)), null));
        assertEquals(2, coreRecipe.getIngredients().getFirst().getMatchingStacks().length);
        OreDictionary.getOres(ore).removeIf(stack -> stack.getItem() == Items.EMERALD);
        assertFalse(coreRecipe.matches(grid(new ItemStack(Items.EMERALD)), null));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void emptyMaterialIngredientResolvesAfterRegistrationAndDisplayCopiesAreIsolated(boolean shaped) {
        IRecipe recipe = recipe(shaped, new UnificationEntry(OrePrefix.dust, core));
        assertFalse(recipe.matches(grid(new ItemStack(Items.COAL)), null));
        register(new ItemStack(Items.COAL), core);
        assertTrue(recipe.matches(grid(new ItemStack(Items.COAL)), null));
        recipe.getIngredients().getFirst().getMatchingStacks()[0].setCount(0);
        assertTrue(recipe.matches(grid(new ItemStack(Items.COAL)), null));
        assertEquals(1, recipe.getIngredients().getFirst().getMatchingStacks()[0].getCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void containersAndNbtClearingRemainNative(boolean shaped) {
        register(new ItemStack(Items.WATER_BUCKET), core);
        IRecipe ordinary = recipe(shaped, new UnificationEntry(OrePrefix.dust, core));
        var inventory = grid(new ItemStack(Items.WATER_BUCKET));
        assertTrue(ordinary.matches(inventory, null));
        assertEquals(Items.BUCKET, ordinary.getRemainingItems(inventory).getFirst().getItem());
        String name = name();
        if (shaped) ModHandler.addShapedNBTClearingRecipe(name, new ItemStack(Items.APPLE),
                "A", 'A', new UnificationEntry(OrePrefix.dust, core));
        else ModHandler.addShapelessNBTClearingRecipe(name, new ItemStack(Items.APPLE),
                new UnificationEntry(OrePrefix.dust, core));
        IRecipe clearing = ForgeRegistries.RECIPES.getValue(recipes.getLast());
        assertTrue(clearing.matches(inventory, null));
        assertTrue(clearing.getRemainingItems(inventory).getFirst().isEmpty());
        assertEquals(Items.WATER_BUCKET, inventory.getStackInSlot(0).getItem());
    }

    @Test
    void shapelessNativeIngredientsAndRenamedItemsPreserveCallerArguments() {
        register(new ItemStack(Items.COAL), core);
        Object[] inputs = { new UnificationEntry(OrePrefix.dust, core),
                Ingredient.fromStacks(new ItemStack(Items.DIAMOND)) };
        Object[] before = inputs.clone();
        String name = name();
        ModHandler.addShapelessRecipe(name, new ItemStack(Items.APPLE), inputs);
        assertArrayEquals(before, inputs);
        var inventory = grid(new ItemStack(Items.COAL).setStackDisplayName("Named coal"));
        inventory.setInventorySlotContents(3, new ItemStack(Items.DIAMOND).setStackDisplayName("Named diamond"));
        assertTrue(ForgeRegistries.RECIPES.getValue(recipes.getLast()).matches(inventory, null));
    }

    @Test
    void shapedMirroringRetainsQualifiedMatching() {
        register(new ItemStack(Items.COAL), core);
        register(new ItemStack(Items.DIAMOND), pack);
        Object[] inputs = { "AB", 'A', new UnificationEntry(OrePrefix.dust, core), 'B', Items.APPLE };
        String ordinaryName = name();
        ModHandler.addShapedRecipe(ordinaryName, new ItemStack(Items.BREAD), inputs);
        IRecipe ordinary = ForgeRegistries.RECIPES.getValue(recipes.getLast());
        String mirroredName = name();
        ModHandler.addMirroredShapedRecipe(mirroredName, new ItemStack(Items.BREAD), inputs);
        IRecipe mirrored = ForgeRegistries.RECIPES.getValue(recipes.getLast());
        var inventory = grid(new ItemStack(Items.APPLE));
        inventory.setInventorySlotContents(1, new ItemStack(Items.COAL));
        assertFalse(ordinary.matches(inventory, null));
        assertTrue(mirrored.matches(inventory, null));
        inventory.setInventorySlotContents(1, new ItemStack(Items.DIAMOND));
        assertFalse(mirrored.matches(inventory, null));
    }

    @Test
    void ordinaryShapelessRecipesAcceptNamedAndEnchantedItems() {
        IRecipe recipe = recipe(false, Items.COAL);
        var coal = new ItemStack(Items.COAL).setStackDisplayName("Named coal");
        coal.addEnchantment(Enchantments.UNBREAKING, 1);
        assertTrue(recipe.matches(grid(coal), null));
        assertEquals("stone", ModHandler.finalizeIngredient(new UnificationEntry(OrePrefix.stone)));
    }

    @Test
    void publicIdentityInspectionDistinguishesQualifiedBroadAndForeignIngredients() {
        var qualified = (Ingredient) ModHandler.finalizeIngredient(new UnificationEntry(OrePrefix.dust, core));
        var identity = CraftingDefinitionCompiler.ingredientIdentity(qualified).orElseThrow();
        assertEquals(ore, identity.oreName());
        assertEquals(core.getResourceLocation(), identity.material());
        var broad = new NativeCraftingIngredient(new GTRecipeOreInput(ore));
        var broadIdentity = CraftingDefinitionCompiler.ingredientIdentity(broad).orElseThrow();
        assertEquals(ore, broadIdentity.oreName());
        assertNull(broadIdentity.material());
        assertTrue(CraftingDefinitionCompiler.ingredientIdentity(Ingredient.fromItem(Items.COAL)).isEmpty());
        register(new ItemStack(Items.COAL), core);
        assertEquals(identity, CraftingDefinitionCompiler.ingredientIdentity(qualified).orElseThrow());
        assertTrue(qualified.apply(new ItemStack(Items.COAL)));
    }

    @Test
    void wildcardDisplayExpandsConcreteVariantsWithoutLosingMaterialOwnership() {
        register(new ItemStack(Items.DYE, 1, OreDictionary.WILDCARD_VALUE), core);
        register(new ItemStack(Items.COAL), pack);
        var ingredient = (Ingredient) ModHandler.finalizeIngredient(new UnificationEntry(OrePrefix.dust, core));
        var expected = NonNullList.<ItemStack>create();
        Items.DYE.getSubItems(CreativeTabs.SEARCH, expected);
        ItemStack[] actual = ingredient.getMatchingStacks();
        assertEquals(expected.size(), actual.length);
        for (int i = 0; i < actual.length; i++) {
            assertTrue(ItemStack.areItemStacksEqual(expected.get(i), actual[i]));
            assertTrue(ingredient.apply(actual[i]));
            assertNotEquals(OreDictionary.WILDCARD_VALUE, actual[i].getMetadata());
        }
        assertFalse(ingredient.apply(new ItemStack(Items.COAL)));
        actual[0].setCount(0);
        assertEquals(1, ingredient.getMatchingStacks()[0].getCount());
    }

    @Test
    void packedRecipeBookAlternativesFollowLateMembershipAndCannotBeMutatedByCallers() {
        var ingredient = (Ingredient) ModHandler.finalizeIngredient(new UnificationEntry(OrePrefix.dust, core));
        assertTrue(ingredient.getValidItemStacksPacked().isEmpty());
        register(new ItemStack(Items.DYE, 1, OreDictionary.WILDCARD_VALUE), core);
        var expected = NonNullList.<ItemStack>create();
        Items.DYE.getSubItems(CreativeTabs.SEARCH, expected);
        var packed = ingredient.getValidItemStacksPacked();
        assertEquals(expected.size(), packed.size());
        for (ItemStack stack : expected) assertTrue(packed.contains(RecipeItemHelper.pack(stack)));
        packed.clear();
        assertEquals(expected.size(), ingredient.getValidItemStacksPacked().size());
        register(new ItemStack(Items.COAL), core);
        OreDictionary.getOres(ore).removeIf(stack -> stack.getItem() == Items.DYE);
        assertEquals(List.of(RecipeItemHelper.pack(new ItemStack(Items.COAL))),
                ingredient.getValidItemStacksPacked());
    }

    @Test
    void recyclingUsesCanonicalStackWhenForeignMemberRegistersFirst() {
        // Forge ore membership is keyed by item id, so both fixture items need real registry ids.
        var foreign = new ItemStack(registered(new ResourceLocation("zz_foreign", "canonical_dust")));
        var canonical = new ItemStack(registered(new ResourceLocation("gregtech", "recycling_canonical_dust")));
        register(foreign, core);
        register(canonical, core);
        var ingredient = (Ingredient) ModHandler.finalizeIngredient(new UnificationEntry(OrePrefix.dust, core));
        // Native membership keeps registration order, so the first alternative is the foreign item.
        ItemStack[] members = ingredient.getMatchingStacks();
        assertEquals(2, members.length);
        assertSame(foreign.getItem(), members[0].getItem());
        assertSame(canonical.getItem(), OreDictUnifier.get(OrePrefix.dust, core).getItem());

        var inputs = RecyclingHandler.shapedInputs("AA", 'A', ingredient);
        assertEquals(Map.of(new ItemAndMetadata(canonical), 2L), inputs);
        var data = RecyclingHandler.getRecyclingIngredients(inputs, 1,
                stack -> new RecyclingData(new MaterialStack(stack.getItem() == canonical.getItem() ? core : pack,
                        GTValues.M)));
        assertNotNull(data);
        assertEquals(List.of(new MaterialStack(core, 2 * GTValues.M)), data.getMaterials());
    }

    private static Item registered(ResourceLocation name) {
        Item existing = ForgeRegistries.ITEMS.getValue(name);
        if (existing != null && name.equals(existing.getRegistryName())) return existing;
        Item item = new Item().setRegistryName(name);
        ForgeRegistries.ITEMS.register(item);
        return item;
    }

    private void register(ItemStack stack, Material material) {
        OreDictUnifier.registerOre(stack, OrePrefix.dust, material);
        OreDictUnifier.onItemRegistration(new OreDictionary.OreRegisterEvent(ore, stack));
    }

    private String name() {
        var name = new ResourceLocation("gregtech", "crafting_material_identity_" + IDS.incrementAndGet());
        recipes.add(name);
        return name.getPath();
    }

    private IRecipe recipe(boolean shaped, Object input) {
        String name = name();
        if (shaped) ModHandler.addShapedRecipe(name, new ItemStack(Items.APPLE), "A", 'A', input);
        else ModHandler.addShapelessRecipe(name, new ItemStack(Items.APPLE), input);
        return ForgeRegistries.RECIPES.getValue(recipes.getLast());
    }

    private static InventoryCrafting grid(ItemStack first) {
        var inventory = new InventoryCrafting(new ContainerDispenser(new InventoryPlayer(null),
                new InventoryBasic("crafting_material_identity", false, 9)), 2, 2);
        inventory.setInventorySlotContents(0, first);
        return inventory;
    }
}
