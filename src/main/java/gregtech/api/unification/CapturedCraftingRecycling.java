package gregtech.api.unification;

import gregtech.api.recipes.RecyclingContribution;

import net.minecraft.item.crafting.IRecipe;

record CapturedCraftingRecycling(IRecipe recipe, RecyclingContribution contribution) implements RecyclingCaptureEntry {}
