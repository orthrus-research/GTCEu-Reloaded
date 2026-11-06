package gregtech.api.recipes.ingredients;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.definition.MaterialReplacement;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.RecipeDefinitionRegistrar;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.definition.ItemValue;
import gregtech.api.recipes.definition.MaterialIngredient;
import gregtech.api.recipes.definition.NbtPolicy;
import gregtech.api.recipes.definition.RecipeDefinition;
import gregtech.api.recipes.fixtures.RecyclingUnificationFixture;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.registry.IMaterialRegistryManager;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.core.unification.material.internal.MaterialRegistryManager;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MaterialOreIdentityTest {

    private static final AtomicInteger IDS = new AtomicInteger();
    private IMaterialRegistryManager previous;
    private RecyclingUnificationFixture fixture;
    private Material core;
    private Material pack;
    private Material previousPack;
    private String ore;

    @BeforeEach
    void setup() throws Exception {
        Bootstrap.perform();
        fixture = new RecyclingUnificationFixture(List.of(), List.of(OrePrefix.dust, OrePrefix.dustSmall));
        previous = GregTechAPI.materialManager;
        var constructor = MaterialRegistryManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var manager = constructor.newInstance();
        GregTechAPI.materialManager = manager;
        manager.unfreezeRegistries();
        String name = "ore_identity_" + IDS.incrementAndGet();
        core = Material.builder(51, new ResourceLocation("gregtech", name)).dust().build();
        previousPack = Material.builder(52, new ResourceLocation("susy", name)).dust().build();
        pack = Material.builder(53, new ResourceLocation("susy", name)).dust().buildReplacing(
                new MaterialReplacement(new SourceOrigin("java", "identity-test", 0, 0, "replace", "MaterialEvent"),
                        previousPack));
        manager.closeRegistries();
        manager.freezeRegistries();
        ore = new UnificationEntry(OrePrefix.dust, core).toString();
    }

    @AfterEach
    void restore() throws Exception {
        GregTechAPI.materialManager = previous;
        if (fixture != null) fixture.close();
    }

    private void register(ItemStack stack, Material material) {
        OreDictUnifier.registerOre(stack, OrePrefix.dust, material);
        // Bootstrap does not install the Forge event subscriber. Also exercises replay after typed registration.
        OreDictUnifier.onItemRegistration(new OreDictionary.OreRegisterEvent(ore, stack));
    }

    @Test
    void explicitOwnersSurviveBareNameCollisionAndEventReplay() {
        var coreStack = new ItemStack(Items.COAL);
        var packStack = new ItemStack(Items.DIAMOND);
        register(coreStack, core);
        register(packStack, pack);
        register(coreStack, core);
        assertSame(core, OreDictUnifier.getMaterial(coreStack).material);
        assertSame(pack, OreDictUnifier.getMaterial(packStack).material);
        assertEquals(Items.COAL, OreDictUnifier.get(OrePrefix.dust, core).getItem());
        assertEquals(Items.DIAMOND, OreDictUnifier.get(OrePrefix.dust, pack).getItem());
        assertEquals(1, OreDictUnifier.getAll(new UnificationEntry(OrePrefix.dust, core)).size());
        assertEquals(1, OreDictUnifier.getAll(new UnificationEntry(OrePrefix.dust, pack)).size());
        assertEquals(2, OreDictUnifier.getAllWithOreDictionaryName(ore).size());
    }

    @Test
    void materialInputsAreDistinctWhileNamedOreStaysBroadAndLive() {
        register(new ItemStack(Items.COAL), core);
        register(new ItemStack(Items.DIAMOND), pack);
        var coreInput = new GTRecipeOreInput(OrePrefix.dust, core, 2);
        var packInput = new GTRecipeOreInput(OrePrefix.dust, pack, 2);
        var broad = new GTRecipeOreInput(ore, 2);
        assertNotEquals(coreInput, packInput);
        assertNotEquals(coreInput, broad);
        assertFalse(coreInput.equalIgnoreAmount(packInput));
        assertTrue(coreInput.acceptsStack(new ItemStack(Items.COAL)));
        assertFalse(coreInput.acceptsStack(new ItemStack(Items.DIAMOND)));
        assertTrue(packInput.acceptsStack(new ItemStack(Items.DIAMOND)));
        assertFalse(packInput.acceptsStack(new ItemStack(Items.COAL)));
        assertTrue(broad.acceptsStack(new ItemStack(Items.COAL)));
        assertTrue(broad.acceptsStack(new ItemStack(Items.DIAMOND)));
        assertEquals(1, coreInput.getInputStacks().length);
        register(new ItemStack(Items.EMERALD), core);
        assertTrue(coreInput.acceptsStack(new ItemStack(Items.EMERALD)));
        assertFalse(packInput.acceptsStack(new ItemStack(Items.EMERALD)));
        assertEquals(2, coreInput.getInputStacks().length);
        var members = OreDictionary.getOres(ore);
        members.removeIf(stack -> stack.getItem() == Items.EMERALD);
        assertFalse(coreInput.acceptsStack(new ItemStack(Items.EMERALD)));
        members.clear();
        assertEquals(0, coreInput.getInputStacks().length);
        assertFalse(coreInput.acceptsStack(new ItemStack(Items.COAL)));
        OreDictionary.rebakeMap();
        register(new ItemStack(Items.COAL), core);
        assertTrue(coreInput.acceptsStack(new ItemStack(Items.COAL)));
    }

    @Test
    void nativeIndexKeepsBothOwnersAndAcceptsLaterMembers() {
        register(new ItemStack(Items.COAL), core);
        register(new ItemStack(Items.DIAMOND), pack);
        RecipeMap<?> map = new RecipeMapBuilder<>("material_ore_identity_" + IDS.incrementAndGet(),
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().input(OrePrefix.dust, core, 2).outputs(new ItemStack(Items.APPLE))
                .duration(40).EUt(8).buildAndRegister();
        map.recipeBuilder().input(OrePrefix.dust, pack, 2).outputs(new ItemStack(Items.BREAD))
                .duration(40).EUt(8).buildAndRegister();
        assertEquals(2, map.getRecipeList().size());
        var coreRecipe = map.findRecipe(8, List.of(new ItemStack(Items.COAL, 2)), List.of(), true);
        var packRecipe = map.findRecipe(8, List.of(new ItemStack(Items.DIAMOND, 2)), List.of(), true);
        assertNotNull(coreRecipe);
        assertNotNull(packRecipe);
        assertEquals(Items.APPLE, coreRecipe.getOutputs().getFirst().getItem());
        assertEquals(Items.BREAD, packRecipe.getOutputs().getFirst().getItem());
        register(new ItemStack(Items.EMERALD), core);
        assertSame(coreRecipe, map.findRecipe(8, List.of(new ItemStack(Items.EMERALD, 2)), List.of(), true));
        assertNull(map.findRecipe(8, List.of(new ItemStack(Items.EMERALD)), List.of(), true));
        assertTrue(map.removeRecipe(coreRecipe));
        assertNull(map.findRecipe(8, List.of(new ItemStack(Items.COAL, 2)), List.of(), true));
        assertSame(packRecipe, map.findRecipe(8, List.of(new ItemStack(Items.DIAMOND, 2)), List.of(), true));
    }

    @Test
    void copiedAndSerializedInputsPreserveMaterialIdentity() {
        register(new ItemStack(Items.COAL), core);
        register(new ItemStack(Items.DIAMOND), pack);
        var input = new GTRecipeOreInput(OrePrefix.dust, core, 2);
        var decoded = GTRecipeInput.readFromNBT(GTRecipeInput.writeToNBT(input));
        assertEquals(input, decoded);
        assertFalse(decoded.acceptsStack(new ItemStack(Items.DIAMOND)));
        var tag = new NBTTagCompound();
        tag.setString("grade", "pure");
        var copy = input.setNonConsumable().setNBTMatchingCondition(NbtPolicy.contains(tag).compile(), null)
                .copyWithAmount(4);
        var valid = new ItemStack(Items.COAL);
        assertFalse(copy.acceptsStack(valid));
        valid.setTagCompound(tag);
        assertTrue(copy.acceptsStack(valid));
        var wrongOwner = new ItemStack(Items.DIAMOND);
        wrongOwner.setTagCompound(tag.copy());
        assertFalse(copy.acceptsStack(wrongOwner));
        assertTrue(copy.isNonConsumable());
        assertEquals(4, copy.getAmount());
    }

    @Test
    void typedRegistrationCanCorrectAnEarlierInferredAlias() {
        var stack = new ItemStack(Items.COAL);
        OreDictionary.registerOre(ore, stack);
        OreDictUnifier.onItemRegistration(new OreDictionary.OreRegisterEvent(ore, stack));
        register(stack, core);
        assertSame(core, OreDictUnifier.getMaterial(stack).material);
        assertEquals(1, OreDictUnifier.getAll(new UnificationEntry(OrePrefix.dust, core)).size());
        assertTrue(OreDictUnifier.getAll(new UnificationEntry(OrePrefix.dust, pack)).isEmpty());
    }

    @Test
    void sameQualifiedReplacementAcceptsOldAndNewPhysicalFormsWithoutMergingOtherOwners() {
        register(new ItemStack(Items.COAL), core);
        register(new ItemStack(Items.DIAMOND), previousPack);
        register(new ItemStack(Items.EMERALD), pack);
        var before = new GTRecipeOreInput(OrePrefix.dust, previousPack);
        var after = new GTRecipeOreInput(OrePrefix.dust, pack);
        assertEquals(before, after);
        assertTrue(after.acceptsStack(new ItemStack(Items.DIAMOND)));
        assertTrue(after.acceptsStack(new ItemStack(Items.EMERALD)));
        assertFalse(after.acceptsStack(new ItemStack(Items.COAL)));
        assertSame(previousPack, OreDictUnifier.getMaterial(new ItemStack(Items.DIAMOND)).material);
        assertSame(pack, OreDictUnifier.getMaterial(new ItemStack(Items.EMERALD)).material);
    }

    @Test
    void multiplePrefixesAndLaterInferredAliasesPreserveExplicitPrimaryOwnership() {
        var stack = new ItemStack(Items.COAL);
        register(stack, core);
        String smallName = new UnificationEntry(OrePrefix.dustSmall, pack).toString();
        OreDictionary.registerOre(smallName, stack);
        OreDictUnifier.onItemRegistration(new OreDictionary.OreRegisterEvent(smallName, stack));
        assertSame(core, OreDictUnifier.getMaterial(stack).material);
        OreDictUnifier.registerOre(stack, OrePrefix.dustSmall, core);
        assertTrue(new GTRecipeOreInput(OrePrefix.dust, core).acceptsStack(stack));
        assertTrue(new GTRecipeOreInput(OrePrefix.dustSmall, core).acceptsStack(stack));
        assertFalse(new GTRecipeOreInput(OrePrefix.dustSmall, pack).acceptsStack(stack));
        assertEquals(1, OreDictUnifier.getAll(new UnificationEntry(OrePrefix.dust, core)).size());
        assertEquals(1, OreDictUnifier.getAll(new UnificationEntry(OrePrefix.dustSmall, core)).size());
        assertTrue(OreDictUnifier.getAll(new UnificationEntry(OrePrefix.dustSmall, pack)).isEmpty());
    }

    @Test
    void wildcardAndNbtPolicyUseQualifiedNativeIndex() {
        register(new ItemStack(Items.DYE, 1, OreDictionary.WILDCARD_VALUE), core);
        register(new ItemStack(Items.DIAMOND), pack);
        var tag = new NBTTagCompound();
        tag.setString("grade", "pure");
        var input = new GTRecipeOreInput(OrePrefix.dust, core, 2).setNonConsumable()
                .setNBTMatchingCondition(NbtPolicy.contains(tag).compile(), null);
        RecipeMap<?> map = new RecipeMapBuilder<>("material_wildcard_" + IDS.incrementAndGet(),
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(input).outputs(new ItemStack(Items.APPLE)).duration(40).EUt(8).buildAndRegister();
        var stack = new ItemStack(Items.DYE, 2, 3);
        assertNull(map.findRecipe(8, List.of(stack), List.of(), true));
        stack.setTagCompound(tag);
        var recipe = map.findRecipe(8, List.of(stack), List.of(), true);
        assertNotNull(recipe);
        assertTrue(recipe.matches(true, List.of(stack), List.of()));
        assertEquals(2, stack.getCount());
        var wrongOwner = new ItemStack(Items.DIAMOND, 2);
        wrongOwner.setTagCompound(tag.copy());
        assertNull(map.findRecipe(8, List.of(wrongOwner), List.of(), true));
    }

    @Test
    void typedJavaAndCheckedGroovyDescriptorsShareOwnedReplacementAndLateResolution() {
        var source = new SourceOrigin("java", "identity", 7, 1, "material_recipe", "postInit");
        RecipeMap<?> map = new RecipeMapBuilder<>("material_descriptor_" + IDS.incrementAndGet(),
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        var ingredient = new MaterialIngredient(OrePrefix.dust, core, 2, true, NbtPolicy.any());
        var declaration = new RecipeDefinition(source, map.unlocalizedName, List.of(ingredient),
                List.of(ItemValue.of(new ItemStack(Items.APPLE))), 40, 8, false);
        var registrar = new RecipeDefinitionRegistrar();
        var early = registrar.replace("material", List.of(declaration));
        assertTrue(early.value().isEmpty());
        assertEquals("ingredient.material.unresolved", early.diagnostics().getFirst().code());
        assertFalse(OreDictionary.doesOreNameExist(ore));
        register(new ItemStack(Items.COAL), core);
        register(new ItemStack(Items.DIAMOND), pack);
        Binding binding = new Binding();
        binding.setVariable("prefix", OrePrefix.dust);
        binding.setVariable("material", core.getResourceLocation());
        var groovy = new GroovyShell(getClass().getClassLoader(), binding).evaluate("""
                import groovy.transform.CompileStatic
                import gregtech.api.recipes.definition.*
                import gregtech.api.unification.ore.OrePrefix
                import net.minecraft.util.ResourceLocation
                @CompileStatic
                class Author {
                    static MaterialIngredient make(OrePrefix prefix, ResourceLocation material) {
                        new MaterialIngredient(prefix, material, 2, true, NbtPolicy.any())
                    }
                }
                Author.make(prefix, material)
                """);
        assertEquals(ingredient, groovy);
        assertTrue(registrar.replace("material", List.of(declaration)).value().isPresent());
        assertNotNull(map.findRecipe(8, List.of(new ItemStack(Items.COAL, 2)), List.of(), true));
        assertNull(map.findRecipe(8, List.of(new ItemStack(Items.DIAMOND, 2)), List.of(), true));
        assertTrue(registrar.replace("material", List.of()).value().isPresent());
        assertTrue(map.getRecipeList().isEmpty());
        assertTrue(registrar.replace("material", List.of(declaration)).value().isPresent());
        assertNotNull(map.findRecipe(8, List.of(new ItemStack(Items.COAL, 2)), List.of(), true));
        assertTrue(registrar.replace("material", List.of()).value().isPresent());
    }
}
