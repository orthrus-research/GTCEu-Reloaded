package gregtech.api.recipes.conditions;

import gregtech.Bootstrap;
import gregtech.api.recipes.properties.impl.ConditionProperty;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConditionPresentationTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void longAdjacentSummaryKeepsGroupCountAndExplicitEllipsisWithoutTruncatingSavedRequirements() {
        String[][] groups = new String[8][8];
        for (int group = 0; group < groups.length; group++) {
            for (int alternative = 0; alternative < groups[group].length; alternative++) {
                groups[group][alternative] = "missing:long_named_block_group_" + group + "_alternative_" + alternative;
            }
        }
        RecipeCondition<?> condition = RecipeConditions.adjacent(groups, false);
        NBTTagCompound saved = condition.serialize();
        ConditionDescription description = condition.description();
        assertEquals("8", description.arguments().get(0));
        assertTrue(description.arguments().get(1).endsWith("..."));
        assertTrue(description.arguments().get(1).length() <= 128);
        assertEquals(ConditionState.UNAVAILABLE, condition.test(new TestRecipeConditionContext()));
        List<String> lines = ConditionProperty.describe(RecipeConditionSet.EMPTY.add(condition),
                ConditionPresentationTest::translate);
        assertTrue(lines.stream().anyMatch(line -> line.contains("adjacent(8,")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("ellipsis")));
        assertEquals(saved, condition.serialize());
        String serialized = RecipeCondition.deserialize(saved).serialize().toString();
        for (String[] group : groups) for (String alternative : group) assertTrue(serialized.contains(alternative));
    }

    @Test
    void previewBoundsVisibleClausesAndNamesWhileLeavingTheCompleteLogicalSetUntouched() {
        RecipeConditionSet set = RecipeConditionSet.EMPTY;
        for (int i = 0; i < 8; i++) set = set.add(RecipeConditions.height(i, i + 100, false));
        set = set.add(RecipeConditions.biome("minecraft:plains", false))
                .add(RecipeConditions.biome("minecraft:forest", true));
        NBTTagCompound saved = set.serialize();
        List<String> lines = ConditionProperty.describe(set, ConditionPresentationTest::translate);
        assertEquals("all()", lines.get(0));
        for (int i = 1; i <= 6; i++) assertTrue(lines.get(i).startsWith(i + ". "));
        assertEquals("more(3)", lines.get(7));
        assertTrue(lines.contains("pause()"));
        assertTrue(lines.contains("loaded()"));
        assertTrue(lines.contains("ellipsis()"));
        assertEquals(9, set.clauseCount());
        assertEquals(saved, set.serialize());

        RecipeConditionSet alternatives = RecipeConditionSet.EMPTY
                .add(RecipeConditions.biome("minecraft:plains", false))
                .add(RecipeConditions.biome("minecraft:forest", true));
        String clause = ConditionProperty.describe(alternatives, ConditionPresentationTest::translate).get(1);
        assertTrue(clause.contains("biome(minecraft:plains) or() not(biome(minecraft:forest))"));
    }

    private static String translate(String key, Object[] arguments) {
        StringBuilder result = new StringBuilder(key.substring(key.lastIndexOf('.') + 1)).append('(');
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0) result.append(',');
            result.append(arguments[i]);
        }
        return result.append(')').toString();
    }
}
