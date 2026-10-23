package gregtech.api.recipes.modifiers;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Checked decimal arithmetic for one integer recipe channel. */
public final class RecipeAmountModifier {

    public static final RecipeAmountModifier IDENTITY = new RecipeAmountModifier(BigDecimal.ONE, BigDecimal.ZERO);
    private final BigDecimal multiplier;
    private final BigDecimal addition;

    private RecipeAmountModifier(BigDecimal multiplier, BigDecimal addition) {
        this.multiplier = multiplier;
        this.addition = addition;
    }

    public static RecipeAmountModifier of(double multiplier, double addition) {
        if (!Double.isFinite(multiplier) || multiplier < 0 || !Double.isFinite(addition))
            throw new IllegalArgumentException("Recipe amount modifiers must be finite with a nonnegative multiplier");
        return multiplier == 1 && addition == 0 ? IDENTITY :
                new RecipeAmountModifier(BigDecimal.valueOf(multiplier), BigDecimal.valueOf(addition));
    }

    public boolean isIdentity() {
        return this == IDENTITY;
    }

    long apply(long original, long maximum, boolean roundUp, boolean allowZero) {
        if (original < 0) throw new IllegalArgumentException("Negative recipe amount");
        BigDecimal value = BigDecimal.valueOf(original).multiply(multiplier).add(addition);
        if (value.signum() < 0) throw new IllegalArgumentException("Negative adjusted recipe amount");
        long result = value.setScale(0, roundUp ? RoundingMode.CEILING : RoundingMode.FLOOR).longValueExact();
        if (result > maximum || result < (allowZero ? 0 : 1))
            throw new IllegalArgumentException("Adjusted recipe amount is outside its integer bounds");
        return result;
    }
}
