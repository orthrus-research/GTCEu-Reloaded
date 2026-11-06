package gregtech.api.recipes;

import gregtech.api.recipes.definition.CraftingMutation;

import net.minecraft.item.crafting.IRecipe;

/** Retains a successful declaration by identity; a fresh declaration requests preparation again. */
record PreparedCraftingDefinition(CraftingMutation declaration, IRecipe recipe) {}
