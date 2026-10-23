package gregtech.api.recipes.modifiers;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputList;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.api.recipes.chance.output.impl.ChancedItemOutput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;
import gregtech.api.recipes.ingredients.nbtmatch.NBTTagType;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import groovy.lang.GroovyShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeModifierAttemptTest {

    private static RecipeMap<SimpleRecipeBuilder> map;
    private static final RecipeModifierContext CONTEXT = new RecipeModifierContext(
            new ResourceLocation("test:attempt"), 0, BlockPos.ORIGIN, 32, 4);

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        map = new RecipeMapBuilder<>("d2_attempt", new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(3).build();
    }

    @Test
    void documentedGroovyProviderCompilesAndAppliesOrderedDescriptors() throws Exception {
        String documentation = new String(Files.readAllBytes(Paths.get("docs/recipe-modifiers.md")),
                StandardCharsets.UTF_8);
        String example = documentation.split("```groovy\\R", 2)[1].split("```", 2)[0];
        int bodyStart = example.indexOf("private static");
        String script = example.substring(0, bodyStart) +
                "class DocumentedModifierProvider implements IRecipeModifierProvider {\n" +
                example.substring(bodyStart) + "\n}\nreturn new DocumentedModifierProvider()";
        Object result = new GroovyShell(getClass().getClassLoader()).evaluate(script);
        assertTrue(result instanceof IRecipeModifierProvider);
        Recipe source = recipe();
        RecipeModifierResult applied = ((IRecipeModifierProvider) result).getRecipeModifiers().apply(CONTEXT, source);
        assertTrue(applied.isSuccess());
        assertEquals(14, applied.recipe().getDuration(), "8 * 1.5 + 2; reversing the descriptors would give 15");
        assertEquals(2, applied.recipe().getInputs().get(0).getAmount());
        assertEquals(2, applied.recipe().getOutputs().get(0).getCount());
        assertEquals(8, source.getDuration());
        assertEquals(1, source.getInputs().get(0).getAmount());
    }

    @Test
    void oneAttemptTrimsBeforeCallbackAndNeverReappliesToItsOwnResult() {
        Recipe source = recipe();
        AtomicInteger calls = new AtomicInteger();
        RecipeModifierList list = RecipeModifierList.of(RecipeModifier.named("test:once", (context, view) -> {
            calls.incrementAndGet();
            assertEquals(1, view.itemOutputs().size());
            return RecipeModification.builder().duration(1, 1).build();
        }));
        RecipeModifierAttempt attempt = new RecipeModifierAttempt(list, CONTEXT, 1, -1);
        RecipeModifierResult first = attempt.adjust(source);
        assertTrue(first.isSuccess());
        assertSame(first, attempt.adjust(source));
        assertSame(first, attempt.adjust(first.recipe()));
        assertEquals(1, calls.get());
        assertEquals(9, first.recipe().getDuration());
        assertEquals(2, source.getOutputs().size());
        assertEquals(8, source.getDuration());
        RecipeModifierResult another = new RecipeModifierAttempt(list, CONTEXT, 1, -1).adjust(source);
        assertEquals(2, calls.get());
        assertNotSame(first.recipe(), another.recipe());
        assertEquals(9, another.recipe().getDuration());
    }

    @Test
    void reentrantAdjustmentRejectsOuterAttemptEvenIfCallbackIgnoresTheNestedFailure() {
        Recipe source = recipe();
        RecipeModifierAttempt[] holder = new RecipeModifierAttempt[1];
        AtomicInteger calls = new AtomicInteger();
        RecipeModifierList list = RecipeModifierList.of(RecipeModifier.named("test:recursive", (context, view) -> {
            calls.incrementAndGet();
            assertEquals(RecipeModifierResult.Status.UNSUPPORTED, holder[0].adjust(source).status());
            return RecipeModification.IDENTITY;
        }));
        holder[0] = new RecipeModifierAttempt(list, CONTEXT, -1, -1);
        RecipeModifierResult result = holder[0].adjust(source);
        assertEquals(RecipeModifierResult.Status.UNSUPPORTED, result.status());
        assertEquals("gregtech.recipe.modifier.reentrant", result.reasonKey());
        assertFalse(holder[0].isEvaluating());
        assertSame(result, holder[0].adjust(source));
        assertEquals(1, calls.get());
    }

    @Test
    void oversizedChanceSourceRejectsBeforeAnyCallbackAndIntegerLimitIsNotAnAllocationLimit() {
        Recipe base = recipe();
        ChancedItemOutput entry = new ChancedItemOutput(new ItemStack(Items.APPLE), 5000, 0);
        Recipe oversized = base.copyWithPreservedMetadata(base.getInputs(), base.getOutputs(),
                new ChancedOutputList<>(ChancedOutputLogic.OR,
                        Collections.nCopies(RecipeModifierCopies.MAX_PREPARED_CHANCE_ENTRIES + 1, entry)),
                base.getFluidInputs(), base.getFluidOutputs(), base.getChancedFluidOutputs(), 8, 1,
                base.propertyStorage());
        AtomicInteger calls = new AtomicInteger();
        RecipeModifierList list = RecipeModifierList.of(RecipeModifier.named("test:never", (context, view) -> {
            calls.incrementAndGet();
            return RecipeModification.IDENTITY;
        }));
        assertEquals(RecipeModifierResult.Status.INVALID, list.apply(CONTEXT, oversized).status());
        assertEquals(0, calls.get());
        assertEquals(0, RecipeModifierCopies.safeParallelLimit(oversized, Integer.MAX_VALUE, false));
        assertSame(oversized, RecipeModifierList.EMPTY.apply(CONTEXT, oversized).recipe());
    }

    @Test
    void mutableNbtRequirementsAreOwnedAndIdentityBasedArraysExplicitlyReject() {
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("owner", "source");
        var input = new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT)).setNBTMatchingCondition(NBTMatcher.EQUAL_TO,
                NBTCondition.create(NBTTagType.COMPOUND, "value", payload));
        Recipe base = recipe();
        Recipe source = base.copyWithPreservedMetadata(Collections.singletonList(input), base.getOutputs(),
                base.getChancedOutputs(), base.getFluidInputs(), base.getFluidOutputs(), base.getChancedFluidOutputs(),
                8, 1, base.propertyStorage());
        RecipeModifierList list = RecipeModifierList
                .of(RecipeModifier.named("test:copy", (context, view) -> RecipeModification.IDENTITY));
        RecipeModifierResult copied = list.apply(CONTEXT, source);
        assertTrue(copied.isSuccess());
        ((NBTTagCompound) copied.recipe().getInputs().get(0).getNBTMatchingCondition().value).setString("owner",
                "private");
        assertEquals("source", payload.getString("owner"));
        var identityInput = new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT)).setNBTMatchingCondition(
                NBTMatcher.EQUAL_TO, NBTCondition.create(NBTTagType.BYTE_ARRAY, "identity", new byte[] { 1, 2 }));
        Recipe unsupported = base.copyWithPreservedMetadata(Collections.singletonList(identityInput), base.getOutputs(),
                base.getChancedOutputs(), base.getFluidInputs(), base.getFluidOutputs(), base.getChancedFluidOutputs(),
                8, 1, base.propertyStorage());
        assertEquals(RecipeModifierResult.Status.UNSUPPORTED, list.apply(CONTEXT, unsupported).status());
    }

    private static Recipe recipe() {
        return map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT))
                .outputs(new ItemStack(Items.APPLE), new ItemStack(Items.BREAD)).duration(8).EUt(1).build().getResult();
    }
}
