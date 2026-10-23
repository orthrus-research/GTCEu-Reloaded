package gregtech.api.recipes.modifiers;

import gregtech.Bootstrap;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.recipes.GTRecipeInputCache;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputList;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.api.recipes.chance.output.impl.ChancedFluidOutput;
import gregtech.api.recipes.chance.output.impl.ChancedItemOutput;
import gregtech.api.recipes.conditions.RecipeConditionSet;
import gregtech.api.recipes.conditions.RecipeConditions;
import gregtech.api.recipes.ingredients.GTRecipeFluidInput;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.ingredients.GTRecipeOreInput;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;
import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.api.recipes.properties.impl.ConditionProperty;
import gregtech.api.recipes.properties.impl.DimensionProperty;
import gregtech.common.items.MetaItems;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeModifierListTest {

    private static final RecipeModifierContext CONTEXT = new RecipeModifierContext(
            new ResourceLocation("gregtech", "modifier_test"), 0, new BlockPos(12, 64, -19), 128, 4);
    private static RecipeMap<SimpleRecipeBuilder> map;
    private static MetaItem<?>.MetaValueItem previousCircuit;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        previousCircuit = MetaItems.INTEGRATED_CIRCUIT;
        MetaItem<?> meta = MetaItems.ITEMS.get(0);
        MetaItems.INTEGRATED_CIRCUIT = meta.getItem("circuit.integrated");
        if (MetaItems.INTEGRATED_CIRCUIT == null)
            MetaItems.INTEGRATED_CIRCUIT = meta.addItem(461, "circuit.integrated");
        map = new RecipeMapBuilder<>("d2_modifier_api", new SimpleRecipeBuilder()).itemInputs(4).itemOutputs(4)
                .fluidInputs(2).fluidOutputs(4).build();
    }

    @AfterAll
    static void restoreCircuit() {
        MetaItems.INTEGRATED_CIRCUIT = previousCircuit;
    }

    @Test
    void dimensionCodecReturnsAnOwnedTypedListWithBothAllowAndDenyEntries() {
        DimensionProperty property = DimensionProperty.getInstance();
        DimensionProperty.DimensionPropertyList source = new DimensionProperty.DimensionPropertyList();
        source.add(0, false);
        source.add(1, false);
        source.add(-1, true);
        NBTBase encoded = property.serialize(source);
        Object decoded = property.deserialize(encoded.copy());
        assertTrue(decoded instanceof DimensionProperty.DimensionPropertyList);
        DimensionProperty.DimensionPropertyList copy = (DimensionProperty.DimensionPropertyList) decoded;
        assertEquals(encoded, property.serialize(copy));
        assertTrue(copy.checkDimension(0));
        assertFalse(copy.checkDimension(-1));
        copy.add(0, true);
        assertTrue(source.checkDimension(0));
        assertEquals(encoded, property.serialize(source));
    }

    @Test
    void sequentialCallbacksObserveAlreadyAdjustedValuesAndOrderMatters() {
        Recipe source = recipe(4, 11);
        RecipeModifier add = RecipeModifier.named("test:add", (context, view) -> {
            assertEquals(4, view.eut());
            assertEquals(new BlockPos(12, 64, -19), context.position());
            return RecipeModification.builder().energy(1, 3).duration(1, 1).build();
        });
        RecipeModifier multiply = RecipeModifier.named("test:multiply", (context, view) -> {
            assertEquals(7, view.eut());
            assertEquals(12, view.duration());
            return RecipeModification.builder().energy(2, 0).duration(2, 0).build();
        });
        Recipe adjusted = successful(RecipeModifierList.of(add, multiply).apply(CONTEXT, source));
        assertEquals(14, adjusted.getEUt());
        assertEquals(24, adjusted.getDuration());
        Recipe reversed = successful(pipeline(RecipeModification.builder().energy(2, 0).build(),
                RecipeModification.builder().energy(1, 3).build()).apply(CONTEXT, source));
        assertEquals(11, reversed.getEUt());
        assertEquals(4, source.getEUt());
        assertEquals(11, source.getDuration());
    }

    @Test
    void roundingIsConservativeAndCatalystsKeepTheirAmounts() {
        Recipe source = recipe(-3, 11);
        Recipe adjusted = successful(pipeline(RecipeModification.builder().energy(.5, 0).duration(.5, 0)
                .itemInputs(.5, 0).fluidInputs(.5, 0).itemOutputs(.5, 0).fluidOutputs(.5, 0).build())
                        .apply(CONTEXT, source));
        assertEquals(-2, adjusted.getEUt());
        assertEquals(6, adjusted.getDuration());
        assertEquals(2, adjusted.getInputs().get(0).getAmount());
        assertEquals(2, adjusted.getInputs().get(1).getAmount());
        assertTrue(adjusted.getInputs().get(1).isNonConsumable());
        assertEquals(501, adjusted.getFluidInputs().get(0).getAmount());
        assertEquals(1, adjusted.getOutputs().get(0).getCount());
        assertEquals(500, adjusted.getFluidOutputs().get(0).amount);
        assertEquals(1, adjusted.getChancedOutputs().getChancedEntries().get(0).getIngredient().getCount());
        assertEquals(500, adjusted.getChancedFluidOutputs().getChancedEntries().get(0).getIngredient().amount);
        assertEquals(3210, adjusted.getChancedOutputs().getChancedEntries().get(0).getChance());
        assertEquals(17, adjusted.getChancedOutputs().getChancedEntries().get(0).getChanceBoost());
        assertSame(ChancedOutputLogic.AND, adjusted.getChancedOutputs().getChancedOutputLogic());
        assertSame(ChancedOutputLogic.XOR, adjusted.getChancedFluidOutputs().getChancedOutputLogic());
    }

    @Test
    void zeroOutputsDisappearButZeroInputsDurationEnergyAndNegativeResultsReject() {
        Recipe source = recipe(4, 11);
        Recipe adjusted = successful(pipeline(RecipeModification.builder().itemOutputs(0, 0).fluidOutputs(0, 0).build())
                .apply(CONTEXT, source));
        assertTrue(adjusted.getOutputs().isEmpty());
        assertTrue(adjusted.getFluidOutputs().isEmpty());
        assertTrue(adjusted.getChancedOutputs().getChancedEntries().isEmpty());
        assertTrue(adjusted.getChancedFluidOutputs().getChancedEntries().isEmpty());
        for (RecipeModification modification : Arrays.asList(
                RecipeModification.builder().itemInputs(0, 0).build(),
                RecipeModification.builder().fluidInputs(0, 0).build(),
                RecipeModification.builder().duration(0, 0).build(),
                RecipeModification.builder().energy(0, 0).build(),
                RecipeModification.builder().itemOutputs(0, -.1).build())) {
            assertEquals(RecipeModifierResult.Status.INVALID, pipeline(modification).apply(CONTEXT, source).status());
        }
        assertEquals(3, source.getOutputs().get(0).getCount());
    }

    @Test
    void nonfiniteAndOverflowArithmeticFailBeforeReturningARecipe() {
        for (double invalid : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> RecipeModification.builder().energy(invalid, 0));
            assertThrows(IllegalArgumentException.class, () -> RecipeModification.builder().duration(1, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> RecipeModification.builder().itemInputs(-1, 0));
        for (RecipeModification modification : Arrays.asList(
                RecipeModification.builder().energy(Double.MAX_VALUE, 0).build(),
                RecipeModification.builder().duration(Integer.MAX_VALUE, 0).build(),
                RecipeModification.builder().itemInputs(Integer.MAX_VALUE, 0).build(),
                RecipeModification.builder().fluidOutputs(Integer.MAX_VALUE, 0).build())) {
            assertNull(pipeline(modification).apply(CONTEXT, recipe(4, 11)).recipe());
        }
        assertEquals(Long.MAX_VALUE, successful(pipeline(RecipeModification.IDENTITY)
                .apply(CONTEXT, recipe(Long.MAX_VALUE, 11))).getEUt());
        assertFalse(pipeline(RecipeModification.IDENTITY).apply(CONTEXT, recipe(Long.MIN_VALUE, 11)).isSuccess());
    }

    @Test
    void cancellationAndRuntimeExceptionStopLaterCallbacksWithoutLeakingExceptionText() {
        AtomicInteger later = new AtomicInteger();
        RecipeModifier tail = RecipeModifier.named("test:tail", (context, view) -> {
            later.incrementAndGet();
            return RecipeModification.IDENTITY;
        });
        RecipeModifierResult cancelled = RecipeModifierList
                .of(RecipeModifier.named("test:cancel", (context, view) -> RecipeModification.cancel("test.reason")),
                        tail)
                .apply(CONTEXT, recipe(4, 11));
        assertEquals(RecipeModifierResult.Status.CANCELLED, cancelled.status());
        assertEquals("test:cancel", cancelled.modifierId());
        assertEquals(0, cancelled.index());
        assertEquals("test.reason", cancelled.reasonKey());
        RecipeModifierResult failed = RecipeModifierList.of(RecipeModifier.named("test:throw", (context, view) -> {
            throw new IllegalStateException("private callback details");
        }), tail).apply(CONTEXT, recipe(4, 11));
        assertEquals(RecipeModifierResult.Status.INVALID, failed.status());
        assertEquals("gregtech.recipe.modifier.invalid_result", failed.reasonKey());
        assertEquals(0, later.get());
        assertThrows(OutOfMemoryError.class, () -> RecipeModifierList.of(
                RecipeModifier.named("test:fatal", (context, view) -> { throw new OutOfMemoryError("fixture"); }))
                .apply(CONTEXT, recipe(4, 11)));
    }

    @Test
    void buildersAndListsAreDefensiveAndEmptyPipelineKeepsLegacyIdentity() {
        RecipeModification.Builder builder = RecipeModification.builder().duration(2, 0);
        RecipeModification built = builder.build();
        builder.duration(9, 0);
        RecipeModifier[] entries = { RecipeModifier.named("test:first", (context, view) -> built) };
        RecipeModifierList list = RecipeModifierList.of(entries);
        entries[0] = RecipeModifier.named("test:replacement",
                (context, view) -> RecipeModification.cancel("test.cancel"));
        assertEquals(22, successful(list.apply(CONTEXT, recipe(4, 11))).getDuration());
        assertThrows(UnsupportedOperationException.class, () -> list.modifiers().clear());
        assertThrows(IllegalArgumentException.class, () -> RecipeModifierList.of(list.modifiers().get(0),
                list.modifiers().get(0)));
        assertThrows(IllegalArgumentException.class, () -> RecipeModifierList.of(new RecipeModifier[33]));
        assertThrows(IllegalArgumentException.class,
                () -> RecipeModifier.named("not namespaced", (context, view) -> built));
        Recipe source = recipe(4, 11);
        assertSame(source, RecipeModifierList.EMPTY.apply(CONTEXT, source).recipe());
        assertNotSame(source, pipeline(RecipeModification.IDENTITY).apply(CONTEXT, source).recipe());
    }

    @Test
    void callbackCopiesCannotMutateEitherSourceOrRunningRecipeAndTwoMachinesStayIsolated() {
        Recipe source = recipe(4, 11);
        RecipeModifier scribble = RecipeModifier.named("test:scribble", (context, view) -> {
            view.itemInputs().get(0).items()[0].setCount(999);
            view.itemOutputs().get(0).getTagCompound().setString("owner", "changed");
            view.fluidInputs().get(0).fluid().tag.setString("owner", "changed");
            view.fluidOutputs().get(0).tag.setString("owner", "changed");
            view.chancedItemOutputs().get(0).getIngredient().setCount(999);
            view.chancedFluidOutputs().get(0).getIngredient().amount = 999;
            ((NBTTagCompound) view.properties().get(DimensionProperty.KEY)).setIntArray("whiteListDimensions",
                    new int[] { 99 });
            return RecipeModification.IDENTITY;
        });
        Recipe first = successful(RecipeModifierList.of(scribble).apply(CONTEXT, source));
        Recipe second = successful(
                pipeline(RecipeModification.builder().itemOutputs(2, 0).build()).apply(CONTEXT, source));
        first.getOutputs().get(0).getTagCompound().setString("owner", "first result");
        assertEquals("source", source.getOutputs().get(0).getTagCompound().getString("owner"));
        assertEquals("source", second.getOutputs().get(0).getTagCompound().getString("owner"));
        assertEquals("source", first.getFluidOutputs().get(0).tag.getString("owner"));
        assertEquals(3, first.getChancedOutputs().getChancedEntries().get(0).getIngredient().getCount());
        assertEquals(1001, first.getChancedFluidOutputs().getChancedEntries().get(0).getIngredient().amount);
        first.getProperty(DimensionProperty.getInstance(), null).add(99, false);
        assertFalse(source.getProperty(DimensionProperty.getInstance(), null).whiteListDimensions.contains(99));
        assertFalse(second.getProperty(DimensionProperty.getInstance(), null).whiteListDimensions.contains(99));
    }

    @Test
    void sourceFlagsCategoryAndBothChanceLogicsSurviveOutsideScriptExecution() throws Exception {
        Recipe source = recipe(4, 11);
        // Emulate a recipe previously registered during Groovy loading; no script engine is needed for this copy test.
        Field groovy = Recipe.class.getDeclaredField("groovyRecipe");
        groovy.setAccessible(true);
        groovy.setBoolean(source, true);
        Recipe adjusted = successful(
                pipeline(RecipeModification.builder().duration(2, 0).build()).apply(CONTEXT, source));
        assertTrue(adjusted.isGroovyRecipe());
        assertTrue(adjusted.getIsCTRecipe());
        assertTrue(adjusted.isHidden());
        assertSame(source.getRecipeCategory(), adjusted.getRecipeCategory());
        assertSame(ChancedOutputLogic.AND, adjusted.getChancedOutputs().getChancedOutputLogic());
        assertSame(ChancedOutputLogic.XOR, adjusted.getChancedFluidOutputs().getChancedOutputLogic());
    }

    @Test
    void addedRequirementsAndOriginalAlternativesRemainSeparateAndClausesAreBounded() {
        Recipe source = recipe(4, 11);
        RecipeConditionSet added = RecipeConditionSet.EMPTY.add(RecipeConditions.biome("minecraft:desert", false));
        Recipe adjusted = successful(
                pipeline(RecipeModification.builder().conditions(added).build()).apply(CONTEXT, source));
        RecipeConditionSet requirements = adjusted.getProperty(ConditionProperty.getInstance(),
                RecipeConditionSet.EMPTY);
        assertEquals(2, requirements.clauseCount());
        assertEquals(1, source.getProperty(ConditionProperty.getInstance(), RecipeConditionSet.EMPTY).clauseCount());
        assertEquals(requirements.serialize(), RecipeConditionSet.deserialize(requirements.serialize()).serialize());
        RecipeConditionSet full = RecipeConditionSet.EMPTY;
        for (int i = 0; i < 16; i++) full = full.add(RecipeConditions.height(i, 100, false));
        assertEquals(RecipeModifierResult.Status.INVALID,
                pipeline(RecipeModification.builder().conditions(full).build()).apply(CONTEXT, source).status());
    }

    @Test
    void oreNbtMatchersAndCircuitCatalystsUseTheirExistingCopyContracts() {
        OreDictionary.registerOre("d2ModifierOre", new ItemStack(Items.IRON_INGOT));
        GTRecipeInput ore = new GTRecipeOreInput("d2ModifierOre", 3).setNBTMatchingCondition(NBTMatcher.ANY,
                NBTCondition.ANY);
        GTRecipeInput circuit = IntCircuitIngredient.circuitInput(7);
        Recipe base = recipe(4, 11);
        Recipe source = base.copyWithPreservedMetadata(Arrays.asList(ore, circuit), base.getOutputs(),
                base.getChancedOutputs(), base.getFluidInputs(), base.getFluidOutputs(), base.getChancedFluidOutputs(),
                11, 4, base.propertyStorage());
        Recipe adjusted = successful(
                pipeline(RecipeModification.builder().itemInputs(2, 0).build()).apply(CONTEXT, source));
        assertEquals(6, adjusted.getInputs().get(0).getAmount());
        assertSame(NBTMatcher.ANY, adjusted.getInputs().get(0).getNBTMatcher());
        assertEquals(ore.getOreDict(), adjusted.getInputs().get(0).getOreDict());
        assertEquals(1, adjusted.getInputs().get(1).getAmount());
        assertTrue(adjusted.getInputs().get(1).isNonConsumable());
        assertEquals(3, ore.getAmount());
    }

    @Test
    void addonPropertiesCopyThroughTheirOwnCodecAndCopyFailureStopsBeforeCallbacks() {
        Recipe source = recipe(4, 11);
        CopyTestProperty property = new CopyTestProperty(false);
        NBTTagCompound value = new NBTTagCompound();
        value.setString("owner", "source");
        source.propertyStorage().store(property, value);
        Recipe adjusted = successful(pipeline(RecipeModification.IDENTITY).apply(CONTEXT, source));
        adjusted.getProperty(property, null).setString("owner", "private");
        assertEquals("source", value.getString("owner"));
        AtomicInteger callbacks = new AtomicInteger();
        Recipe broken = recipe(4, 11);
        broken.propertyStorage().store(new CopyTestProperty(true), value);
        RecipeModifierResult result = RecipeModifierList.of(RecipeModifier.named("test:never", (context, view) -> {
            callbacks.incrementAndGet();
            return RecipeModification.IDENTITY;
        })).apply(CONTEXT, broken);
        assertEquals(RecipeModifierResult.Status.INVALID, result.status());
        assertEquals("gregtech:recipe_copy", result.modifierId());
        assertEquals(0, callbacks.get());
    }

    @Test
    void ownedInputsNeverReenterRegistrationCacheAndBuiltinStackNbtIsIsolated() throws Exception {
        Field instances = GTRecipeInputCache.class.getDeclaredField("instances");
        instances.setAccessible(true);
        Object previous = instances.get(null);
        // Scope only the real cache contents; avoid unrelated persistence/config initialization in enableCache().
        instances.set(null, new ObjectOpenHashSet<GTRecipeInput>());
        try {
            assertTrue(GTRecipeInputCache.isCacheEnabled());
            ItemStack tagged = new ItemStack(Items.IRON_INGOT, 3);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("owner", "source");
            tagged.setTagCompound(tag);
            Recipe base = recipe(4, 11);
            Recipe source = new Recipe(Collections.singletonList(new GTRecipeItemInput(tagged)), base.getOutputs(),
                    base.getChancedOutputs(), base.getFluidInputs(), base.getFluidOutputs(),
                    base.getChancedFluidOutputs(),
                    11, 4, true, true, base.propertyStorage(), base.getRecipeCategory());
            assertTrue(source.getInputs().get(0).isCached());
            assertTrue(source.getFluidInputs().get(0).isCached());
            Recipe first = successful(pipeline(RecipeModification.IDENTITY).apply(CONTEXT, source));
            Recipe second = successful(pipeline(RecipeModification.IDENTITY).apply(CONTEXT, source));
            assertNotSame(source.getInputs().get(0), first.getInputs().get(0));
            assertNotSame(source.getFluidInputs().get(0), first.getFluidInputs().get(0));
            assertNotSame(first.getInputs().get(0), second.getInputs().get(0));
            first.getInputs().get(0).getInputStacks()[0].getTagCompound().setString("owner", "first");
            first.getFluidInputs().get(0).getInputFluidStack().tag.setString("owner", "first");
            assertEquals("source", source.getInputs().get(0).getInputStacks()[0].getTagCompound().getString("owner"));
            assertEquals("source", source.getFluidInputs().get(0).getInputFluidStack().tag.getString("owner"));
            assertEquals("source", second.getInputs().get(0).getInputStacks()[0].getTagCompound().getString("owner"));
            assertEquals("source", second.getFluidInputs().get(0).getInputFluidStack().tag.getString("owner"));
            source.getInputs().get(0).getInputStacks()[0].getTagCompound().setString("owner", "source changed later");
            assertTrue(second.getInputs().get(0).acceptsStack(tagged),
                    "Copied acceptance table must not retain the registered inputStacks NBT");
            assertFalse(second.getInputs().get(0).acceptsStack(source.getInputs().get(0).getInputStacks()[0]));
        } finally {
            instances.set(null, previous);
        }
    }

    private static RecipeModifierList pipeline(RecipeModification... changes) {
        RecipeModifier[] modifiers = new RecipeModifier[changes.length];
        for (int i = 0; i < changes.length; i++) {
            RecipeModification change = changes[i];
            modifiers[i] = RecipeModifier.named("test:modifier_" + i, (context, view) -> change);
        }
        return RecipeModifierList.of(modifiers);
    }

    private static Recipe successful(RecipeModifierResult result) {
        assertEquals(RecipeModifierResult.Status.SUCCESS, result.status(),
                result.modifierId() + ":" + result.reasonKey());
        assertNotNull(result.recipe());
        return result.recipe();
    }

    private static Recipe recipe(long eut, int duration) {
        ItemStack output = new ItemStack(Items.GOLD_NUGGET, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("owner", "source");
        output.setTagCompound(tag);
        FluidStack water = new FluidStack(FluidRegistry.WATER, 1001, tag.copy());
        RecipePropertyStorage properties = new RecipePropertyStorageImpl();
        DimensionProperty.DimensionPropertyList dimensions = new DimensionProperty.DimensionPropertyList();
        dimensions.add(0, false);
        properties.store(DimensionProperty.getInstance(), dimensions);
        properties.store(ConditionProperty.getInstance(),
                RecipeConditionSet.EMPTY.add(RecipeConditions.biome("minecraft:plains", false)));
        return new Recipe(Arrays.asList(new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 3)),
                new GTRecipeItemInput(new ItemStack(Items.STICK, 2)).setNonConsumable()),
                Collections.singletonList(output), new ChancedOutputList<>(ChancedOutputLogic.AND,
                        Collections.singletonList(new ChancedItemOutput(output.copy(), 3210, 17))),
                Collections.singletonList(new GTRecipeFluidInput(water)), Collections.singletonList(water.copy()),
                new ChancedOutputList<>(ChancedOutputLogic.XOR,
                        Collections.singletonList(new ChancedFluidOutput(water.copy(), 4321, 19))),
                duration, eut, true, true, properties, map.getPrimaryRecipeCategory());
    }

    private static final class CopyTestProperty extends RecipeProperty<NBTTagCompound> {

        private final boolean broken;

        private CopyTestProperty(boolean broken) {
            super("d2_copy_test", NBTTagCompound.class);
            this.broken = broken;
        }

        @Override
        public NBTBase serialize(Object value) {
            return (NBTTagCompound) value;
        }

        @Override
        public Object deserialize(NBTBase nbt) {
            if (broken) throw new IllegalArgumentException("fixture codec cannot copy");
            return nbt;
        }

        @Override
        public void drawInfo(Minecraft minecraft, int x, int y, int color, Object value) {}
    }
}
