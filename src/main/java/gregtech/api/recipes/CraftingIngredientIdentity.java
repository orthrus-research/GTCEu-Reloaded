package gregtech.api.recipes;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

/** Read-only native ingredient identity; null fields indicate no corresponding ore or material constraint. */
public record CraftingIngredientIdentity(@Nullable String oreName, @Nullable ResourceLocation material) {}
