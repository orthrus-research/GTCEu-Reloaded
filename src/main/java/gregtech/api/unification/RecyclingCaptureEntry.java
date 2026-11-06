package gregtech.api.unification;

import gregtech.api.recipes.RecyclingContribution;

sealed interface RecyclingCaptureEntry permits CapturedMachineRecycling, CapturedCraftingRecycling {

    RecyclingContribution contribution();
}
