package gregtech.api.recipes.properties.impl;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.conditions.ConditionDescription;
import gregtech.api.recipes.conditions.RecipeConditionSet;
import gregtech.api.recipes.properties.RecipeProperty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class ConditionProperty extends RecipeProperty<RecipeConditionSet> {

    public static final String KEY = "conditions";
    private static ConditionProperty instance;

    private ConditionProperty() {
        super(KEY, RecipeConditionSet.class);
    }

    public static ConditionProperty getInstance() {
        if (instance == null) {
            instance = new ConditionProperty();
            GregTechAPI.RECIPE_PROPERTIES.register(KEY, instance);
        }
        return instance;
    }

    @Override
    public @NotNull NBTBase serialize(@NotNull Object value) {
        return castValue(value).serialize();
    }

    @Override
    public @NotNull Object deserialize(@NotNull NBTBase nbt) {
        return RecipeConditionSet.deserialize(nbt);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInfo(Minecraft minecraft, int x, int y, int color, Object value) {
        minecraft.fontRenderer.drawString(
                I18n.format("gregtech.recipe.condition.summary", castValue(value).clauseCount()),
                x, y, color);
    }

    /** Bounded presentation only. The complete saved requirement data is never shortened. */
    public static List<String> describe(RecipeConditionSet conditions,
                                        BiFunction<String, Object[], String> translator) {
        List<String> lines = new ArrayList<>();
        lines.add(translator.apply("gregtech.recipe.condition.all", new Object[0]));
        int shown = Math.min(6, conditions.clauseCount());
        for (int i = 0; i < shown; i++) {
            List<String> alternatives = new ArrayList<>();
            for (ConditionDescription description : conditions.descriptions().get(i)) {
                alternatives.add(description.format(translator));
            }
            String clause = String.join(" " + translator.apply("gregtech.recipe.condition.or", new Object[0]) + " ",
                    alternatives);
            if (clause.length() > 160) clause = clause.substring(0, 157) + "...";
            lines.add((i + 1) + ". " + clause);
        }
        if (shown < conditions.clauseCount()) {
            lines.add(translator.apply("gregtech.recipe.condition.more",
                    new Object[] { conditions.clauseCount() - shown }));
        }
        lines.add(translator.apply("gregtech.recipe.condition.pause", new Object[0]));
        lines.add(translator.apply("gregtech.recipe.condition.loaded", new Object[0]));
        lines.add(translator.apply("gregtech.recipe.condition.ellipsis", new Object[0]));
        return lines;
    }
}
