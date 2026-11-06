package gregtech.api.unification;

import gregtech.api.recipes.NativeRecipeRegistration;
import gregtech.api.recipes.RecyclingContribution;

record CapturedMachineRecycling(NativeRecipeRegistration registration,
                                RecyclingContribution contribution)
        implements RecyclingCaptureEntry {}
