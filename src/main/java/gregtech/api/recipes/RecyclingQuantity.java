package gregtech.api.recipes;

import java.math.BigInteger;

/** Exact nonnegative registration arithmetic; rounded only when publishing native material quantities. */
record RecyclingQuantity(BigInteger numerator, BigInteger denominator) implements Comparable<RecyclingQuantity> {

    static final RecyclingQuantity ZERO = of(0, 1);

    RecyclingQuantity {
        if (numerator.signum() < 0 || denominator.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Recycling quantities need a nonnegative numerator and positive divisor");
        }
        BigInteger gcd = numerator.gcd(denominator);
        numerator = numerator.divide(gcd);
        denominator = denominator.divide(gcd);
    }

    static RecyclingQuantity of(long numerator, long denominator) {
        return new RecyclingQuantity(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    RecyclingQuantity add(RecyclingQuantity other) {
        return new RecyclingQuantity(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    RecyclingQuantity multiply(BigInteger amount) {
        return new RecyclingQuantity(numerator.multiply(amount), denominator);
    }

    long floorLongExact() {
        return numerator.divide(denominator).longValueExact();
    }

    @Override
    public int compareTo(RecyclingQuantity other) {
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }
}
