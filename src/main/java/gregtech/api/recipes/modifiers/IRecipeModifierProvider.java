package gregtech.api.recipes.modifiers;

/**
 * Explicit custom-machine opt-in. Return an immutable pipeline; callbacks must be pure.
 * Custom preparation overrides must delegate through the standard recipe-logic preparation path. Numeric discounts
 * do not bypass original registered-input/voltage discovery. Explicit recipe-logic eligibility calls evaluate a fresh
 * ephemeral pipeline; passive lookup/getter calls do not. Active jobs never consult this provider again.
 *
 * A Processing Array may implement this interface on its controller. Installed delegate machines implementing it are
 * unsupported: their logic does not prepare the array's actual recipe. This boundary applies even to an empty list.
 */
public interface IRecipeModifierProvider {

    RecipeModifierList getRecipeModifiers();
}
