package gregtech.api.recipes;

import gregtech.api.definition.SourceOrigin;

record OwnedCraftingGroup(CraftingRegistryRestoration baseline, SourceOrigin origin, int recipes) {}
