package gregtech.loaders.recipe;

import gregtech.api.unification.material.MarkerMaterials.Tier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.stack.UnificationEntry;

import java.util.Locale;

import static gregtech.api.GTValues.VN;

/**
 * Tier lookups several recipe loaders share, by the voltage tiers of {@link gregtech.api.GTValues}. A table only one
 * loader reads stays in that loader.
 */
final class RecipeTiers {

    /** The circuit marker material of each tier, ULV to MAX. */
    private static final Material[] CIRCUITS = { Tier.ULV, Tier.LV, Tier.MV, Tier.HV, Tier.EV, Tier.IV, Tier.LuV,
            Tier.ZPM, Tier.UV, Tier.UHV, Tier.UEV, Tier.UIV, Tier.UXV, Tier.OpV, Tier.MAX };

    private RecipeTiers() {}

    /** @return the marker material of a tier's circuits, as {@code circuitLv} names it for LV */
    static Material circuitTier(int tier) {
        return CIRCUITS[tier];
    }

    /** @return the material of a crafting component's ore dictionary entry at a tier, such as the tier's plate */
    static Material material(CraftingComponent.Component component, int tier) {
        return ((UnificationEntry) component.getIngredient(tier)).material;
    }

    /** @return the tier's name in lower case, as recipe names hold it: {@code lv}, {@code luv} */
    static String tierName(int tier) {
        return VN[tier].toLowerCase(Locale.ROOT);
    }
}
