package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.definition.MaterialReplacement;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.recipes.fixtures.RecyclingUnificationFixture;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.MarkerMaterials;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.material.registry.IMaterialRegistryManager;
import gregtech.api.unification.ore.IOreRegistrationHandler;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.common.items.MetaItems;
import gregtech.core.unification.material.internal.MaterialRegistryManager;
import gregtech.datafix.RecipeBookMigrationTable;
import gregtech.datafix.impl.MigrateRecipeBook;
import gregtech.loaders.recipe.handlers.MaterialRecipeHandler;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.stats.RecipeBookServer;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedMaterialRecipeIdentityTest {

    private static final AtomicInteger IDS = new AtomicInteger();
    private IMaterialRegistryManager previous;
    private RecyclingUnificationFixture fixture;
    private Material core;
    private Material pack;
    private List<Recipe> packerRecipes;
    private String path;
    private MetaItem<?>.MetaValueItem previousCircuit;

    @BeforeEach
    void setup() throws Exception {
        Bootstrap.perform();
        previousCircuit = MetaItems.INTEGRATED_CIRCUIT;
        var carrier = MetaItems.ITEMS.getFirst();
        MetaItems.INTEGRATED_CIRCUIT = carrier.getItem("circuit.integrated");
        if (MetaItems.INTEGRATED_CIRCUIT == null)
            MetaItems.INTEGRATED_CIRCUIT = carrier.addItem(461, "circuit.integrated");
        fixture = new RecyclingUnificationFixture(List.of(), List.of(OrePrefix.dust, OrePrefix.dustSmall));
        previous = GregTechAPI.materialManager;
        var constructor = MaterialRegistryManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var manager = constructor.newInstance();
        GregTechAPI.materialManager = manager;
        manager.unfreezeRegistries();
        path = "generated_identity_" + IDS.incrementAndGet();
        core = Material.builder(71, new ResourceLocation("gregtech", path)).dust().build();
        pack = Material.builder(72, new ResourceLocation("susy", path)).dust().build();
        packerRecipes = List.copyOf(RecipeMaps.PACKER_RECIPES.getRecipeList());
    }

    @AfterEach
    void restore() throws Exception {
        var registry = (IForgeRegistryModifiable<IRecipe>) ForgeRegistries.RECIPES;
        List.copyOf(registry.getKeys()).stream().filter(name -> name.getPath().contains(path))
                .forEach(registry::remove);
        for (Recipe recipe : List.copyOf(RecipeMaps.PACKER_RECIPES.getRecipeList())) {
            if (!packerRecipes.contains(recipe)) RecipeMaps.PACKER_RECIPES.removeRecipe(recipe);
        }
        for (OrePrefix prefix : List.of(OrePrefix.dust, OrePrefix.dustSmall)) {
            OreDictionary.getOres(prefix.name() + core.toCamelCaseString()).clear();
        }
        OreDictionary.rebakeMap();
        GregTechAPI.materialManager = previous;
        MetaItems.INTEGRATED_CIRCUIT = previousCircuit;
        fixture.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void bothMaterialOwnersRetainTheirGeneratedRecipesRegardlessOfRegistrationOrder(boolean reversed) {
        OreDictUnifier.registerOre(new ItemStack(Items.COAL), OrePrefix.dustSmall, core);
        OreDictUnifier.registerOre(new ItemStack(Items.DIAMOND), OrePrefix.dust, core);
        OreDictUnifier.registerOre(new ItemStack(Items.STICK), OrePrefix.dustSmall, pack);
        OreDictUnifier.registerOre(new ItemStack(Items.EMERALD), OrePrefix.dust, pack);
        for (Material material : reversed ? List.of(pack, core) : List.of(core, pack)) {
            MaterialRecipeHandler.processSmallDust(OrePrefix.dustSmall, material,
                    material.getProperty(PropertyKey.DUST));
        }
        IRecipe coreRecipe = ForgeRegistries.RECIPES
                .getValue(new ResourceLocation("gregtech", "small_dust_assembling_" + path));
        IRecipe packRecipe = ForgeRegistries.RECIPES
                .getValue(new ResourceLocation("susy", "small_dust_assembling_" + path));
        assertNotNull(coreRecipe);
        assertNotNull(packRecipe);
        assertEquals(Items.DIAMOND, coreRecipe.getRecipeOutput().getItem());
        assertEquals(Items.EMERALD, packRecipe.getRecipeOutput().getItem());
        assertTrue(coreRecipe.getIngredients().getFirst().apply(new ItemStack(Items.COAL)));
        assertFalse(coreRecipe.getIngredients().getFirst().apply(new ItemStack(Items.STICK)));
        assertTrue(packRecipe.getIngredients().getFirst().apply(new ItemStack(Items.STICK)));
        assertFalse(packRecipe.getIngredients().getFirst().apply(new ItemStack(Items.COAL)));
    }

    @Test
    void generatedMappingsRecoverNativeRecipeBookEntriesBeforeDeserialization() {
        String previousPath = "previous/71_" + path;
        String coreName = ModHandler.materialRecipeName(core, "current_" + path, previousPath);
        String packName = ModHandler.materialRecipeName(pack, "current_" + path, previousPath);
        var previousName = new ResourceLocation("gregtech", previousPath);
        assertEquals(List.of(new ResourceLocation(coreName), new ResourceLocation(packName)),
                RecipeBookMigrationTable.GENERATED.targets(previousName));
        ModHandler.addShapedRecipe(coreName, new ItemStack(Items.DIAMOND), "A", 'A', Items.COAL);
        ModHandler.addShapedRecipe(packName, new ItemStack(Items.EMERALD), "A", 'A', Items.STICK);
        var coreRecipe = ForgeRegistries.RECIPES.getValue(new ResourceLocation(coreName));
        var packRecipe = ForgeRegistries.RECIPES.getValue(new ResourceLocation(packName));
        var player = new NBTTagCompound();
        var saved = new NBTTagCompound();
        var names = new NBTTagList();
        names.appendTag(new NBTTagString(previousName.toString()));
        saved.setTag("recipes", names);
        saved.setTag("toBeDisplayed", names.copy());
        player.setTag("recipeBook", saved);
        var withoutMigration = new RecipeBookServer();
        withoutMigration.read(saved);
        assertFalse(withoutMigration.isUnlocked(coreRecipe));
        assertFalse(withoutMigration.isUnlocked(packRecipe));
        var fixed = new MigrateRecipeBook().fixTagCompound(player.copy());
        var book = new RecipeBookServer();
        book.read(fixed.getCompoundTag("recipeBook"));
        assertTrue(book.isUnlocked(coreRecipe));
        assertTrue(book.isUnlocked(packRecipe));
        assertTrue(book.isNew(coreRecipe));
        assertTrue(book.isNew(packRecipe));
        ModHandler.removeRecipeByName(new ResourceLocation(packName));
        var afterRemoval = new MigrateRecipeBook().fixTagCompound(player.copy());
        assertEquals(1, afterRemoval.getCompoundTag("recipeBook").getTagList("recipes", 8).tagCount());
    }

    @Test
    void explicitlyQualifiedDuplicateCannotReplaceAnExistingRecipe() {
        String name = "susy:" + path;
        ModHandler.addShapedRecipe(name, new ItemStack(Items.DIAMOND), "A", 'A', Items.COAL);
        IRecipe original = ForgeRegistries.RECIPES.getValue(new ResourceLocation(name));
        ModHandler.addShapedRecipe(name, new ItemStack(Items.EMERALD), "A", 'A', Items.COAL);
        assertSame(original, ForgeRegistries.RECIPES.getValue(new ResourceLocation(name)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialHandlersKeepWorkEnqueuedByCallbacksForNextPass() throws Exception {
        var generatedField = OrePrefix.class.getDeclaredField("generatedMaterials");
        generatedField.setAccessible(true);
        Set<Material> queued = (Set<Material>) generatedField.get(OrePrefix.dustSmall);
        var handlersField = OrePrefix.class.getDeclaredField("oreProcessingHandlers");
        handlersField.setAccessible(true);
        var handlers = (List<IOreRegistrationHandler>) handlersField.get(OrePrefix.dustSmall);
        var previousHandlers = List.copyOf(handlers);
        var observed = new ArrayList<Material>();
        try {
            queued.clear();
            handlers.clear();
            handlers.add((prefix, material) -> {
                observed.add(material);
                if (material == core) prefix.processOreRegistration(pack);
            });
            OrePrefix.dustSmall.processOreRegistration(core);
            var run = OrePrefix.class.getDeclaredMethod("runGeneratedMaterialHandlers");
            run.setAccessible(true);
            run.invoke(OrePrefix.dustSmall);
            assertEquals(List.of(core), observed);
            run.invoke(OrePrefix.dustSmall);
            assertEquals(List.of(core, pack), observed);
        } finally {
            handlers.clear();
            handlers.addAll(previousHandlers);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void materialHandlersUseCurrentDeclarationsInQualifiedOrderWithoutRemovingPhysicalIds() throws Exception {
        var replacement = Material.builder(73, pack.getResourceLocation()).dust()
                .buildReplacing(new MaterialReplacement(
                        new SourceOrigin("java", "test", 1, 1, "replace", "MaterialEvent"), pack));
        var generatedField = OrePrefix.class.getDeclaredField("generatedMaterials");
        generatedField.setAccessible(true);
        Set<Material> queued = (Set<Material>) generatedField.get(OrePrefix.dustSmall);
        var handlersField = OrePrefix.class.getDeclaredField("oreProcessingHandlers");
        handlersField.setAccessible(true);
        var handlers = (List<IOreRegistrationHandler>) handlersField.get(OrePrefix.dustSmall);
        var previousHandlers = List.copyOf(handlers);
        var observed = new ArrayList<Material>();
        try {
            queued.clear();
            handlers.clear();
            handlers.add((prefix, material) -> observed.add(material));
            OrePrefix.dustSmall.processOreRegistration(replacement);
            OrePrefix.dustSmall.processOreRegistration(pack);
            OrePrefix.dustSmall.processOreRegistration(core);
            OrePrefix.dustSmall.processOreRegistration(MarkerMaterials.Tier.LV);
            var run = OrePrefix.class.getDeclaredMethod("runGeneratedMaterialHandlers");
            run.setAccessible(true);
            run.invoke(OrePrefix.dustSmall);
            assertEquals(List.of(core, MarkerMaterials.Tier.LV, replacement), observed);
            assertSame(pack, pack.getRegistry().getObjectById(72));
            assertSame(replacement, pack.getRegistry().getObjectById(73));
            assertNull(OrePrefix.getCurrentMaterial());
            assertNull(OrePrefix.getCurrentProcessingPrefix());
        } finally {
            handlers.clear();
            handlers.addAll(previousHandlers);
        }
    }
}
