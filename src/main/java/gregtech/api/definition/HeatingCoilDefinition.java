package gregtech.api.definition;

import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.unification.material.Material;

import java.util.Objects;

/** Immutable native coil statistics, shared by Java and Groovy authoring. */
public record HeatingCoilDefinition(SourceOrigin origin, String name, int temperature, int level,
                                    int energyDiscount, int tier, Material material)
        implements IHeatingCoilBlockStats {

    public HeatingCoilDefinition {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getCoilTemperature() {
        return temperature;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public int getEnergyDiscount() {
        return energyDiscount;
    }

    @Override
    public int getTier() {
        return tier;
    }

    @Override
    public Material getMaterial() {
        return material;
    }
}
