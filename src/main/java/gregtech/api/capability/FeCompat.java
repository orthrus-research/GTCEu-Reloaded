package gregtech.api.capability;

import gregtech.common.ConfigHolder;

import net.minecraftforge.energy.IEnergyStorage;

public class FeCompat {

    /**
     * Conversion ratio used by energy converters
     */
    public static int ratio(boolean feToEu) {
        return feToEu ? ConfigHolder.compat.energy.feToEuRatio : ConfigHolder.compat.energy.euToFeRatio;
    }

    /**
     * Converts eu to fe, using specified ratio. The result saturates at {@link Integer#MAX_VALUE}, and a negative
     * amount converts to 0, so the top voltage tiers do not wrap to a negative or zero amount.
     *
     * @return fe, from 0 to {@link Integer#MAX_VALUE}
     */
    public static int toFe(long eu, int ratio) {
        if (eu <= 0 || ratio <= 0) return 0;
        // with eu at most Integer.MAX_VALUE the product fits in a long
        if (eu >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.min(Integer.MAX_VALUE, eu * ratio);
    }

    /**
     * Converts eu to fe, using specified ratio, and returns as a long.
     * Can be used for overflow protection.
     * 
     * @return fe
     */
    public static long toFeLong(long eu, int ratio) {
        return eu * ratio;
    }

    /**
     * Converts eu to fe, using a specified ratio, and with a specified upper bound.
     * This can be useful for dealing with int-overflows when converting from a long to an int.
     * 
     * @return fe
     */
    public static int toFeBounded(long eu, int ratio, int max) {
        return (int) Math.min(max, toFeLong(eu, ratio));
    }

    /**
     * Converts fe to eu, using specified ratio
     * 
     * @return eu
     */
    public static long toEu(long fe, int ratio) {
        return fe / ratio;
    }

    /**
     * Inserts energy to the storage. EU -> FE conversion is performed.
     * 
     * @return amount of EU inserted
     */
    public static long insertEu(IEnergyStorage storage, long amountEU) {
        int euToFeRatio = ratio(false);
        int feSent = storage.receiveEnergy(toFe(amountEU, euToFeRatio), true);
        return toEu(storage.receiveEnergy(feSent - (feSent % euToFeRatio), false), euToFeRatio);
    }

    /**
     * Extracts energy from the storage. EU -> FE conversion is performed.
     * 
     * @return amount of EU extracted
     */
    public static long extractEu(IEnergyStorage storage, long amountEU) {
        int euToFeRatio = ratio(false);
        int extract = storage.extractEnergy(toFe(amountEU, euToFeRatio), true);
        return toEu(storage.extractEnergy(extract - (extract % euToFeRatio), false), euToFeRatio);
    }
}
