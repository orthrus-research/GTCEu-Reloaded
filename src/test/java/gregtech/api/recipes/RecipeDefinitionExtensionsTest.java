package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.definition.SourceOrigin;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.definition.*;
import gregtech.api.recipes.fixtures.AuthoringTestBuilder;
import gregtech.api.recipes.fixtures.InvalidAuthoringCopyBuilder;
import gregtech.api.recipes.properties.impl.DimensionProperty;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeDefinitionExtensionsTest {

    private static final AtomicInteger IDS = new AtomicInteger();
    private static final AtomicInteger DECODES = new AtomicInteger();
    private static final SourceOrigin SOURCE = new SourceOrigin("java", "AddonRecipes.java", 42, 3, "custom",
            "postInit");
    private static final ResourceLocation POWER_ID = new ResourceLocation("extension_test", "power");
    private static RecipeAttributeType<AuthoringTestBuilder, Integer> power;
    private static RecipeAttributeType<AuthoringTestBuilder, Integer> pressure;
    private static RecipeAttributeCodec<Integer> codec;

    @BeforeAll
    static void setup() {
        Bootstrap.perform();
        RecipeDefinitionExtensions.registerBuilder(AuthoringTestBuilder.class);
        codec = new RecipeAttributeCodec<>() {

            @Override
            public NBTBase encode(Integer value) {
                return payload(1, value);
            }

            @Override
            public Integer decode(NBTBase payload) {
                DECODES.incrementAndGet();
                if (!(payload instanceof NBTTagCompound tag) || !tag.getKeySet().equals(Set.of("version", "value")) ||
                        !tag.hasKey("version", 3) || tag.getInteger("version") != 1 || !tag.hasKey("value", 3)) {
                    throw new IllegalArgumentException("Expected version 1 integer payload");
                }
                int result = tag.getInteger("value");
                tag.setInteger("value", 999);
                return result;
            }
        };
        power = RecipeDefinitionExtensions.registerAttribute(POWER_ID, AuthoringTestBuilder.POWER.getKey(),
                AuthoringTestBuilder.class, codec, AuthoringTestBuilder::power);
        pressure = RecipeDefinitionExtensions.registerAttribute(new ResourceLocation("extension_test", "pressure"),
                AuthoringTestBuilder.PRESSURE.getKey(), AuthoringTestBuilder.class, codec,
                AuthoringTestBuilder::pressure);
    }

    @Test
    void payloadSnapshotsAndSeparateAttributeKeysCompileToNativeValues() {
        var map = map();
        NBTTagCompound input = payload(1, 12);
        var custom = new RecipeAttribute.Custom(POWER_ID, input);
        int hash = custom.hashCode();
        input.setInteger("value", -1);
        ((NBTTagCompound) custom.payload()).setInteger("value", -2);
        var definition = definition(map).withAttributes(custom, pressure.descriptor(7));
        for (int i = 0; i < 2; i++) {
            Recipe recipe = RecipeDefinitionCompiler.compile(definition).value().orElseThrow();
            assertEquals(12, recipe.getProperty(AuthoringTestBuilder.POWER, 0));
            assertEquals(7, recipe.getProperty(AuthoringTestBuilder.PRESSURE, 0));
        }
        assertEquals(hash, custom.hashCode());
        assertEquals(12, ((NBTTagCompound) custom.payload()).getInteger("value"));
        assertTrue(map.getRecipeList().isEmpty());
    }

    @Test
    void invalidVersionsTypesAndDomainValuesHaveSourceDiagnostics() {
        var map = map();
        for (NBTBase invalid : List.of(payload(2, 1), payload(1, 0), payload(1, -1), new NBTTagCompound())) {
            var result = RecipeDefinitionCompiler.compile(definition(map)
                    .withAttributes(new RecipeAttribute.Custom(POWER_ID, invalid)));
            assertTrue(result.value().isEmpty());
            var diagnostic = result.diagnostics().getFirst();
            assertEquals("recipe.attribute.invalid", diagnostic.code());
            assertEquals("attributes[0].payload", diagnostic.field());
            assertEquals(SOURCE, diagnostic.origin());
            assertTrue(map.getRecipeList().isEmpty());
        }
    }

    @Test
    void addonEnergyNarrowingFailureKeepsSourceAndPublishedGroup() {
        var map = map();
        var registrar = new RecipeDefinitionRegistrar();
        try {
            var original = definition(map).withAttributes(power.descriptor(3));
            assertTrue(registrar.replace("bounded", List.of(original)).value().isPresent());
            Recipe published = map.getRecipeList().iterator().next();
            var overflow = new RecipeDefinition(SOURCE, map.unlocalizedName, original.inputs(), original.outputs(),
                    20, Long.MAX_VALUE, false);
            var result = registrar.replace("bounded", List.of(overflow));
            assertTrue(result.value().isEmpty());
            assertEquals("recipe.builder.energy", result.diagnostics().getFirst().code());
            assertEquals("eut", result.diagnostics().getFirst().field());
            assertEquals(SOURCE, result.diagnostics().getFirst().origin());
            assertSame(published, map.getRecipeList().iterator().next());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
    }

    @Test
    void unknownDuplicateAndWrongBuilderAttributesCannotPublish() {
        var map = map();
        assertEquals("recipe.attribute.unknown", RecipeDefinitionCompiler.compile(definition(map).withAttributes(
                new RecipeAttribute.Custom(new ResourceLocation("extension_test", "unknown"), payload(1, 2))))
                .diagnostics().getFirst().code());
        assertEquals("recipe.attribute.duplicate", RecipeDefinitionCompiler.compile(definition(map)
                .withAttributes(power.descriptor(2), power.descriptor(3))).diagnostics().getFirst().code());
        var simple = new RecipeMapBuilder<>(name(), new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        assertEquals("recipe.attribute.builder", RecipeDefinitionCompiler.compile(definition(simple)
                .withAttributes(power.descriptor(2))).diagnostics().getFirst().code());
        assertTrue(map.getRecipeList().isEmpty());
        assertTrue(simple.getRecipeList().isEmpty());
    }

    @Test
    void unregisteredSubclassIsRejectedBeforeItsCopyRuns() {
        var builder = new UnregisteredBuilder();
        var map = new RecipeMapBuilder<>(name(), builder).itemInputs(1).itemOutputs(1).build();
        int before = builder.copies;
        assertEquals("recipe.builder.effects", RecipeDefinitionCompiler.compile(definition(map))
                .diagnostics().getFirst().code());
        assertEquals(before, builder.copies);
    }

    @Test
    void brokenRegisteredCopiesCannotMutateTheirTemplates() {
        RecipeDefinitionExtensions.registerBuilder(InvalidAuthoringCopyBuilder.class);
        for (boolean returnsSelf : List.of(true, false)) {
            var builder = new InvalidAuthoringCopyBuilder(returnsSelf);
            var map = new RecipeMapBuilder<>(name(), builder).itemInputs(1).itemOutputs(1).build();
            assertEquals("recipe.builder.copy", RecipeDefinitionCompiler.compile(definition(map))
                    .diagnostics().getFirst().code());
            assertTrue(builder.getInputs().isEmpty());
            assertTrue(map.getRecipeList().isEmpty());
        }
    }

    @Test
    void customAndBuiltInDeclarationsCannotClaimTheSamePropertyInEitherOrder() {
        var type = RecipeDefinitionExtensions.registerAttribute(new ResourceLocation("extension_test", "dimension"),
                DimensionProperty.KEY, AuthoringTestBuilder.class, codec, (builder, id) -> builder.dimension(id));
        var custom = type.descriptor(0);
        var builtin = new RecipeAttribute.Dimensions(List.of(1), List.of());
        var map = map();
        for (RecipeAttribute[] attributes : List.of(new RecipeAttribute[] { custom, builtin },
                new RecipeAttribute[] { builtin, custom })) {
            var failed = RecipeDefinitionCompiler.compile(definition(map).withAttributes(attributes));
            assertTrue(failed.value().isEmpty());
            assertEquals("recipe.attribute.duplicate", failed.diagnostics().getFirst().code());
            assertEquals("attributes[1]", failed.diagnostics().getFirst().field());
        }
        assertTrue(map.getRecipeList().isEmpty());
    }

    @Test
    void incompleteAdapterAndArbitraryBuildActionsFailBeforePublication() {
        var type = RecipeDefinitionExtensions.registerAttribute(new ResourceLocation("extension_test", "incomplete"),
                "extension_test_missing", AuthoringTestBuilder.class, codec, (builder, value) -> {});
        var map = map();
        assertEquals("recipe.attribute.adapter", RecipeDefinitionCompiler.compile(definition(map)
                .withAttributes(type.descriptor(1))).diagnostics().getFirst().code());
        var calls = new AtomicInteger();
        map.onRecipeBuild(new ResourceLocation("extension_test", "unsafe"), ignored -> calls.incrementAndGet());
        assertEquals("recipe.map.effects", RecipeDefinitionCompiler.compile(definition(map)
                .withAttributes(power.descriptor(1))).diagnostics().getFirst().code());
        assertEquals(0, calls.get());
        assertTrue(map.getRecipeList().isEmpty());
    }

    @Test
    void registrationsCannotReplaceContractsOrAliasPropertyKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> RecipeDefinitionExtensions.registerBuilder(AuthoringTestBuilder.class));
        assertThrows(IllegalArgumentException.class,
                () -> RecipeDefinitionExtensions.registerBuilder(SimpleRecipeBuilder.class));
        assertThrows(IllegalArgumentException.class, () -> RecipeDefinitionExtensions.registerAttribute(POWER_ID,
                "other_key", AuthoringTestBuilder.class, codec, AuthoringTestBuilder::power));
        assertThrows(IllegalArgumentException.class, () -> RecipeDefinitionExtensions.registerAttribute(
                new ResourceLocation("extension_test", "alias"), AuthoringTestBuilder.POWER.getKey(),
                AuthoringTestBuilder.class, codec, AuthoringTestBuilder::power));
        assertEquals(4, RecipeDefinitionCompiler.compile(definition(map()).withAttributes(power.descriptor(4)))
                .value().orElseThrow().getProperty(AuthoringTestBuilder.POWER, 0));
    }

    @Test
    void crossMapReplacementIsAtomicAndMatchingDoesNotDecodeAgain() {
        var child = map();
        var parent = new RecipeMapBuilder<>(name(), new AuthoringTestBuilder()).itemInputs(1).itemOutputs(1)
                .onExpand(new ResourceLocation("extension_test", "child"), (builder, derived) -> {
                    var copy = child.recipeBuilder().inputs(new ItemStack(Items.APPLE))
                            .outputs(new ItemStack(Items.COAL))
                            .duration(20).EUt(8);
                    copy.power(5);
                    derived.accept(copy);
                }).build();
        var registrar = new RecipeDefinitionRegistrar();
        try {
            assertEquals(2, registrar.replace("addon", List.of(definition(parent).withAttributes(power.descriptor(2))))
                    .value().orElseThrow().recipes());
            Recipe oldParent = parent.getRecipeList().iterator().next();
            Recipe oldChild = child.getRecipeList().iterator().next();
            int calls = DECODES.get();
            for (int i = 0; i < 100; i++) {
                assertSame(oldParent, parent.findRecipe(8, List.of(new ItemStack(Items.APPLE)), List.of()));
                assertSame(oldChild, child.findRecipe(8, List.of(new ItemStack(Items.APPLE)), List.of()));
            }
            assertEquals(calls, DECODES.get());
            assertTrue(registrar.replace("addon", List.of(definition(parent).withAttributes(power.descriptor(0))))
                    .value().isEmpty());
            assertSame(oldParent, parent.getRecipeList().iterator().next());
            assertSame(oldChild, child.getRecipeList().iterator().next());
        } finally {
            assertTrue(registrar.clear().value().isPresent());
        }
        assertTrue(parent.getRecipeList().isEmpty());
        assertTrue(child.getRecipeList().isEmpty());
    }

    @Test
    void staticallyCompiledGroovyUsesTheSameTypedHandle() {
        var map = map();
        var binding = new Binding();
        binding.setVariable("type", power);
        var authored = (RecipeAttribute) new GroovyShell(getClass().getClassLoader(), binding).evaluate("""
                import groovy.transform.CompileStatic
                import gregtech.api.recipes.RecipeAttributeType
                import gregtech.api.recipes.definition.RecipeAttribute
                import gregtech.api.recipes.fixtures.AuthoringTestBuilder
                @CompileStatic
                class AddonAuthoring {
                    static RecipeAttribute make(RecipeAttributeType<AuthoringTestBuilder, Integer> type) {
                        type.descriptor(17)
                    }
                }
                AddonAuthoring.make(type)
                """);
        assertEquals(power.descriptor(17), authored);
        assertEquals(17, RecipeDefinitionCompiler.compile(definition(map).withAttributes(authored))
                .value().orElseThrow().getProperty(AuthoringTestBuilder.POWER, 0));
    }

    private static RecipeDefinition definition(RecipeMap<?> map) {
        return new RecipeDefinition(SOURCE, map.unlocalizedName,
                List.of(ItemIngredient.exact(new ItemStack(Items.APPLE))),
                List.of(ItemValue.of(new ItemStack(Items.DIAMOND))), 20, 8, false);
    }

    private static RecipeMap<AuthoringTestBuilder> map() {
        return new RecipeMapBuilder<>(name(), new AuthoringTestBuilder()).itemInputs(1).itemOutputs(1).build();
    }

    private static String name() {
        return "extension_test_" + IDS.incrementAndGet();
    }

    private static NBTTagCompound payload(int version, int value) {
        var tag = new NBTTagCompound();
        tag.setInteger("version", version);
        tag.setInteger("value", value);
        return tag;
    }

    private static final class UnregisteredBuilder extends AuthoringTestBuilder {

        private int copies;

        @Override
        public AuthoringTestBuilder copy() {
            copies++;
            return super.copy();
        }
    }
}
