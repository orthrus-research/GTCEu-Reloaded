package gregtech.api.recipes;

/** Ownership identity shared across all typed crafting registrars. */
record CraftingClaim(RecipeCraftingOwnership owner, String group) {}
