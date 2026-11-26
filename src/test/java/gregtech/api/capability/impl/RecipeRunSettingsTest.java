package gregtech.api.capability.impl;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecipeRunSettingsTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static Recipe recipe(RecipeMap<SimpleRecipeBuilder> map, long eut, int duration) {
        return map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).outputs(new ItemStack(Items.APPLE))
                .EUt(eut).duration(duration).build().getResult();
    }

    @Test
    void newSettingsAllowOneParallelAndNoBonuses() {
        RecipeRunSettings settings = new RecipeRunSettings();
        assertEquals(1, settings.getParallelLimit());
        assertEquals(-1, settings.getEUDiscount());
        assertEquals(-1, settings.getSpeedBonus());
        assertFalse(settings.hasRecipeBonuses());
        assertTrue(settings.isOverclockingAllowed());
        assertEquals(0, settings.getOverclockVoltage());
    }

    @Test
    void bonusesThatAreNotPositiveAreRefused() {
        RecipeRunSettings settings = new RecipeRunSettings();
        assertFalse(settings.setEUDiscount(0));
        assertFalse(settings.setSpeedBonus(-0.5));
        assertFalse(settings.hasRecipeBonuses());
        assertTrue(settings.setEUDiscount(0.75));
        assertFalse(settings.setEUDiscount(-1));
        assertEquals(0.75, settings.getEUDiscount());
        assertTrue(settings.hasRecipeBonuses());
    }

    @Test
    void bonusesRebuildTheRecipeAndKeepAtLeastOneEUtAndOneTick() {
        RecipeMap<SimpleRecipeBuilder> map = RecipeFailureTest.map();
        Recipe source = recipe(map, 30, 100);
        RecipeRunSettings settings = new RecipeRunSettings();
        settings.setEUDiscount(0.5);
        Recipe discounted = settings.applyRecipeBonuses(source, map);
        assertEquals(15, discounted.getEUt());
        assertEquals(100, discounted.getDuration(), "A discount alone keeps the duration");

        settings.setSpeedBonus(0.2);
        Recipe both = settings.applyRecipeBonuses(source, map);
        assertEquals(15, both.getEUt());
        assertEquals(20, both.getDuration());
        assertEquals(30, source.getEUt(), "The source recipe is not changed");
        assertEquals(100, source.getDuration());

        settings.setEUDiscount(0.01);
        settings.setSpeedBonus(0.001);
        Recipe floor = settings.applyRecipeBonuses(source, map);
        assertEquals(1, floor.getEUt());
        assertEquals(1, floor.getDuration());
    }

    @Test
    void anOverclockLimitAtOrBelowLVDisallowsOverclocking() {
        RecipeRunSettings settings = new RecipeRunSettings();
        settings.limitOverclockVoltage(GTValues.V[GTValues.LV]);
        assertFalse(settings.isOverclockingAllowed());
        assertEquals(GTValues.V[GTValues.LV], settings.getOverclockVoltage());
        settings.limitOverclockVoltage(GTValues.V[GTValues.MV]);
        assertTrue(settings.isOverclockingAllowed());

        settings.setOverclockingAllowed(false);
        assertEquals(GTValues.V[GTValues.MV], settings.getOverclockVoltage(), "Allowing is set apart from the limit");
    }

    @Test
    void onlyTheOverclockLimitIsSavedUnderItsKeys() {
        RecipeRunSettings settings = new RecipeRunSettings();
        settings.setParallelLimit(16);
        settings.setEUDiscount(0.5);
        settings.limitOverclockVoltage(GTValues.V[GTValues.HV]);
        NBTTagCompound tag = new NBTTagCompound();
        settings.writeToNBT(tag);
        assertEquals(2, tag.getKeySet().size());
        assertTrue(tag.getBoolean("AllowOverclocking"));
        assertEquals(GTValues.V[GTValues.HV], tag.getLong("OverclockVoltage"));

        RecipeRunSettings loaded = new RecipeRunSettings();
        loaded.readFromNBT(tag);
        assertTrue(loaded.isOverclockingAllowed());
        assertEquals(GTValues.V[GTValues.HV], loaded.getOverclockVoltage());
        assertEquals(1, loaded.getParallelLimit());
        assertFalse(loaded.hasRecipeBonuses());

        RecipeRunSettings fromEmpty = new RecipeRunSettings();
        fromEmpty.readFromNBT(new NBTTagCompound());
        assertFalse(fromEmpty.isOverclockingAllowed(), "An old save without the keys reads as not allowed, as before");
        assertEquals(0, fromEmpty.getOverclockVoltage());
    }
}
