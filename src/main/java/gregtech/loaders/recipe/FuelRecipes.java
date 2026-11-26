package gregtech.loaders.recipe;

import gregtech.api.recipes.RecipeMap;
import gregtech.api.unification.material.Material;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.*;
import static gregtech.api.unification.material.Materials.*;

/**
 * The generator fuels, one row a fluid: the millibuckets a generator burns for the given ticks at LV, and the plasmas
 * a plasma turbine burns, a millibucket at a time at EV, back into their fluid.
 */
public class FuelRecipes {

    public static void registerFuels() {
        // diesel generator fuels
        fuel(COMBUSTION_GENERATOR_FUELS, Naphtha, 1, 10);
        fuel(COMBUSTION_GENERATOR_FUELS, SulfuricLightFuel, 4, 5);
        fuel(COMBUSTION_GENERATOR_FUELS, Methanol, 4, 8);
        fuel(COMBUSTION_GENERATOR_FUELS, Ethanol, 1, 6);
        fuel(COMBUSTION_GENERATOR_FUELS, Octane, 2, 5);
        fuel(COMBUSTION_GENERATOR_FUELS, BioDiesel, 1, 8);
        fuel(COMBUSTION_GENERATOR_FUELS, LightFuel, 1, 10);
        fuel(COMBUSTION_GENERATOR_FUELS, Diesel, 1, 15);
        fuel(COMBUSTION_GENERATOR_FUELS, CetaneBoostedDiesel, 2, 45);
        fuel(COMBUSTION_GENERATOR_FUELS, RocketFuel, 16, 125);
        fuel(COMBUSTION_GENERATOR_FUELS, Gasoline, 1, 50);
        fuel(COMBUSTION_GENERATOR_FUELS, HighOctaneGasoline, 1, 100);
        fuel(COMBUSTION_GENERATOR_FUELS, Toluene, 1, 10);
        fuel(COMBUSTION_GENERATOR_FUELS, OilLight, 32, 5);
        fuel(COMBUSTION_GENERATOR_FUELS, RawOil, 64, 15);

        // steam generator fuels
        STEAM_TURBINE_FUELS.recipeBuilder()
                .fluidInputs(Steam.getFluid(640))
                .fluidOutputs(DistilledWater.getFluid(4))
                .duration(10)
                .EUt(V[LV])
                .buildAndRegister();

        // gas turbine fuels
        fuel(GAS_TURBINE_FUELS, NaturalGas, 8, 5);
        fuel(GAS_TURBINE_FUELS, WoodGas, 8, 6);
        fuel(GAS_TURBINE_FUELS, SulfuricGas, 32, 25);
        fuel(GAS_TURBINE_FUELS, SulfuricNaphtha, 4, 5);
        fuel(GAS_TURBINE_FUELS, CoalGas, 1, 3);
        fuel(GAS_TURBINE_FUELS, Methane, 2, 7);
        fuel(GAS_TURBINE_FUELS, Ethylene, 1, 4);
        fuel(GAS_TURBINE_FUELS, RefineryGas, 1, 5);
        fuel(GAS_TURBINE_FUELS, Ethane, 4, 21);
        fuel(GAS_TURBINE_FUELS, Propene, 1, 6);
        fuel(GAS_TURBINE_FUELS, Butadiene, 16, 102);
        fuel(GAS_TURBINE_FUELS, Propane, 4, 29);
        fuel(GAS_TURBINE_FUELS, Butene, 1, 8);
        fuel(GAS_TURBINE_FUELS, Phenol, 1, 9);
        fuel(GAS_TURBINE_FUELS, Benzene, 1, 11);
        fuel(GAS_TURBINE_FUELS, Butane, 4, 37);
        fuel(GAS_TURBINE_FUELS, LPG, 1, 10);
        // TODO Too OP pls nerf
        fuel(GAS_TURBINE_FUELS, Nitrobenzene, 1, 40);

        // semi-fluid fuels, like creosote
        fuel(SEMI_FLUID_GENERATOR_FUELS, Creosote, 16, 1);
        fuel(SEMI_FLUID_GENERATOR_FUELS, Biomass, 4, 1);
        fuel(SEMI_FLUID_GENERATOR_FUELS, Oil, 2, 1);
        fuel(SEMI_FLUID_GENERATOR_FUELS, OilHeavy, 16, 5);
        fuel(SEMI_FLUID_GENERATOR_FUELS, SulfuricHeavyFuel, 16, 5);
        fuel(SEMI_FLUID_GENERATOR_FUELS, HeavyFuel, 8, 15);
        fuel(SEMI_FLUID_GENERATOR_FUELS, FishOil, 16, 1);

        // plasma turbine
        plasma(Helium, 40);
        plasma(Oxygen, 48);
        plasma(Nitrogen, 64);
        plasma(Argon, 96);
        plasma(Iron, 112);
        plasma(Tin, 128);
        plasma(Nickel, 192);
        plasma(Americium, 320);
    }

    private static void fuel(RecipeMap<?> map, Material fluid, int amount, int duration) {
        map.recipeBuilder()
                .fluidInputs(fluid.getFluid(amount))
                .duration(duration)
                .EUt(V[LV])
                .buildAndRegister();
    }

    private static void plasma(Material plasma, int duration) {
        PLASMA_GENERATOR_FUELS.recipeBuilder()
                .fluidInputs(plasma.getPlasma(1))
                .fluidOutputs(plasma.getFluid(1))
                .duration(duration)
                .EUt(V[EV])
                .buildAndRegister();
    }
}
