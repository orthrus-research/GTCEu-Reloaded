package gregtech.api.recipes.definition;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeDefinitionCompiler;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeDefinitionTest {

    private static final SourceOrigin ORIGIN = new SourceOrigin("test", "recipes/metals.groovy", 12, 4,
            "iron_dust", "recipes");
    private static final AtomicInteger MAP_IDS = new AtomicInteger();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void staticallyCheckedGroovyAndJavaCompileEquivalentNativeRecipes() {
        RecipeMap<?> map = map();
        ItemStack input = new ItemStack(Items.IRON_INGOT, 2);
        ItemStack output = new ItemStack(Items.GOLD_INGOT, 3);
        RecipeDefinition java = definition(map, List.of(ItemIngredient.exact(input)), List.of(ItemValue.of(output)));
        Binding variables = new Binding();
        variables.setVariable("origin", ORIGIN);
        variables.setVariable("mapName", map.unlocalizedName);
        variables.setVariable("input", input);
        variables.setVariable("output", output);
        RecipeDefinition groovy = (RecipeDefinition) new GroovyShell(getClass().getClassLoader(), variables).evaluate(
                """
                        import groovy.transform.CompileStatic
                        import gregtech.api.definition.SourceOrigin
                        import gregtech.api.recipes.definition.*
                        import net.minecraft.item.ItemStack
                        @CompileStatic
                        class AuthoredRecipes {
                            static RecipeDefinition make(SourceOrigin origin, String mapName, ItemStack input, ItemStack output) {
                                new RecipeDefinition(origin, mapName, List.of(ItemIngredient.exact(input)),
                                    List.of(ItemValue.of(output)), 40, 8L, false)
                            }
                        }
                        AuthoredRecipes.make(origin, mapName, input, output)
                        """);
        assertEquals(java, groovy);
        Recipe fromJava = RecipeDefinitionCompiler.compile(java).value().orElseThrow();
        Recipe fromGroovy = RecipeDefinitionCompiler.compile(groovy).value().orElseThrow();
        assertEquals(fromJava.getInputs(), fromGroovy.getInputs());
        assertEquals(fromJava.getDuration(), fromGroovy.getDuration());
        assertEquals(fromJava.getEUt(), fromGroovy.getEUt());
        assertTrue(ItemStack.areItemStacksEqual(fromJava.getOutputs().getFirst(), fromGroovy.getOutputs().getFirst()));
        assertTrue(map.getRecipeList().isEmpty(), "Preparation must not register recipes");
        assertTrue(map.compileRecipe(fromGroovy));
        try {
            assertSame(fromGroovy, map.findRecipe(8, List.of(input), List.of(), true));
            assertNull(map.findRecipe(8, List.of(new ItemStack(Items.IRON_INGOT)), List.of(), true));
            assertNull(map.findRecipe(7, List.of(input), List.of(), true));
        } finally {
            assertTrue(map.removeRecipe(fromGroovy));
        }
    }

    @Test
    void definitionsOwnTheirListsTagsAndStackCounts() {
        RecipeMap<?> map = map();
        ItemStack input = new ItemStack(Items.IRON_INGOT, 2);
        input.setTagCompound(tag(7));
        ItemStack output = new ItemStack(Items.GOLD_INGOT, 3);
        var inputs = new ArrayList<>(List.of(ItemIngredient.exact(input)));
        var outputs = new ArrayList<>(List.of(ItemValue.of(output)));
        RecipeDefinition definition = definition(map, inputs, outputs);
        int hash = definition.hashCode();
        inputs.clear();
        outputs.clear();
        input.getTagCompound().setInteger("value", 99);
        output.setCount(64);
        ((ItemIngredient) definition.inputs().getFirst()).alternatives().getFirst().stack().getTagCompound()
                .setInteger("value", 13);
        definition.outputs().getFirst().stack().setCount(21);
        assertEquals(hash, definition.hashCode());
        Recipe compiled = RecipeDefinitionCompiler.compile(definition).value().orElseThrow();
        assertEquals(3, compiled.getOutputs().getFirst().getCount());
        ItemStack original = new ItemStack(Items.IRON_INGOT, 2);
        original.setTagCompound(tag(7));
        assertTrue(compiled.getInputs().getFirst().acceptsStack(original));
        assertFalse(compiled.getInputs().getFirst().acceptsStack(input));
        assertThrows(UnsupportedOperationException.class, () -> definition.inputs().clear());
    }

    @Test
    void alternativesAndWildcardsUseNativeLookupWithoutCountDependentNbtBypasses() {
        RecipeMap<?> map = map();
        ItemIngredient input = new ItemIngredient(List.of(ItemValue.of(new ItemStack(Items.IRON_INGOT)),
                ItemValue.of(new ItemStack(Items.GOLD_INGOT))), 2, true, NbtPolicy.contains(tag(1)),
                CapabilityPolicy.MATCH);
        Recipe recipe = RecipeDefinitionCompiler.compile(definition(map, List.of(input),
                List.of(ItemValue.of(new ItemStack(Items.DIAMOND))))).value().orElseThrow();
        assertTrue(map.compileRecipe(recipe));
        try {
            for (Item item : List.of(Items.IRON_INGOT, Items.GOLD_INGOT)) {
                for (int count : new int[] { 1, 2, 64 }) {
                    ItemStack candidate = new ItemStack(item, count);
                    candidate.setTagCompound(tag(1));
                    candidate.getTagCompound().setBoolean("extra", true);
                    assertEquals(count >= 2 ? recipe : null, map.findRecipe(8, List.of(candidate), List.of(), true));
                    candidate.getTagCompound().setInteger("value", 2);
                    assertNull(map.findRecipe(8, List.of(candidate), List.of(), true));
                }
            }
        } finally {
            assertTrue(map.removeRecipe(recipe));
        }
        var wildcard = new ItemIngredient(List.of(ItemValue.of(new ItemStack(Items.DYE, 1, GTValues.W))),
                1, false, NbtPolicy.any(), CapabilityPolicy.MATCH).compile();
        assertTrue(wildcard.isNonConsumable());
        assertTrue(wildcard.copyWithAmount(4).isNonConsumable());
        for (int metadata = 0; metadata < 16; metadata++) {
            assertTrue(wildcard.acceptsStack(new ItemStack(Items.DYE, 1, metadata)));
        }
    }

    @Test
    void nbtPolicyDoesNotSilentlyIgnoreCapabilities() {
        Item item = new Item() {

            @Override
            public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
                return new StateCapability();
            }
        };
        ItemStack required = withCapability(item, 1);
        ItemStack different = withCapability(item, 2);
        assertFalse(required.areCapsCompatible(different), "Fixture must exercise different serialized capabilities");
        for (NbtPolicy policy : List.of(NbtPolicy.any(), NbtPolicy.exact(null),
                NbtPolicy.contains(new NBTTagCompound()))) {
            var strict = new ItemIngredient(List.of(ItemValue.of(required)), 2, true, policy, CapabilityPolicy.MATCH)
                    .compile();
            var permissive = new ItemIngredient(List.of(ItemValue.of(required)), 2, true, policy,
                    CapabilityPolicy.IGNORE).compile();
            for (int count : new int[] { 1, 2, 64 }) {
                required.setCount(count);
                different.setCount(count);
                assertTrue(strict.acceptsStack(required));
                assertFalse(strict.acceptsStack(different));
                assertTrue(permissive.acceptsStack(different));
                assertFalse(strict.copyWithAmount(4).acceptsStack(different));
            }
        }
    }

    @Test
    void invalidDeclarationsReportAllSemanticErrorsWithSourceWithoutMutatingTheMap() {
        RecipeMap<?> map = map();
        RecipeDefinition invalid = new RecipeDefinition(ORIGIN, map.unlocalizedName,
                List.of(new ItemIngredient(List.of(), 0, true, NbtPolicy.any(), CapabilityPolicy.MATCH)),
                List.of(ItemValue.of(ItemStack.EMPTY)), 0, 0, false);
        long revision = map.getRecipeRevision();
        var result = RecipeDefinitionCompiler.compile(invalid);
        assertTrue(result.value().isEmpty());
        assertEquals(List.of("recipe.duration", "recipe.energy", "ingredient.amount", "ingredient.alternatives",
                "recipe.output.empty"), result.diagnostics().stream().map(d -> d.code()).toList());
        result.diagnostics().forEach(d -> {
            assertEquals(ORIGIN, d.origin());
            assertTrue(d.format().startsWith("recipes/metals.groovy:12:4 ["));
        });
        assertEquals(revision, map.getRecipeRevision());
        assertTrue(map.getRecipeList().isEmpty());
    }

    @Test
    void unsupportedBuildActionsAreRejectedBeforeTheyRun() {
        RecipeMap<?> map = map();
        AtomicInteger calls = new AtomicInteger();
        map.onRecipeBuild(new ResourceLocation("test", "unowned"), builder -> calls.incrementAndGet());
        var result = RecipeDefinitionCompiler
                .compile(definition(map, List.of(ItemIngredient.exact(new ItemStack(Items.IRON_INGOT))),
                        List.of(ItemValue.of(new ItemStack(Items.GOLD_INGOT)))));
        assertTrue(result.value().isEmpty());
        assertEquals("recipe.map.effects", result.diagnostics().getFirst().code());
        assertEquals(0, calls.get());
        assertTrue(map.getRecipeList().isEmpty());
    }

    private static RecipeMap<?> map() {
        return new RecipeMapBuilder<>("definition_test_" + MAP_IDS.incrementAndGet(), new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(2).build();
    }

    private static RecipeDefinition definition(RecipeMap<?> map, List<ItemIngredient> inputs, List<ItemValue> outputs) {
        return new RecipeDefinition(ORIGIN, map.unlocalizedName, inputs, outputs, 40, 8, false);
    }

    private static NBTTagCompound tag(int value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("value", value);
        return tag;
    }

    private static ItemStack withCapability(Item item, int value) {
        NBTTagCompound caps = new NBTTagCompound();
        caps.setTag("Parent", tag(value));
        return new ItemStack(item, 1, 0, caps);
    }

    private static final class StateCapability implements ICapabilitySerializable<NBTTagCompound> {

        private NBTTagCompound state = new NBTTagCompound();

        @Override
        public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
            return false;
        }

        @Override
        public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
            return null;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            return state.copy();
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbt) {
            this.state = nbt.copy();
        }
    }
}
