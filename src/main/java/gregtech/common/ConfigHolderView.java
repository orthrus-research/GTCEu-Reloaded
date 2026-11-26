package gregtech.common;

import gregtech.api.config.GTConfigView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The {@link GTConfigView} GTCEu provides to its API, answering from {@link ConfigHolder}'s values as they are at
 * each call. The API finds it as a service, through {@code META-INF/services/gregtech.api.config.GTConfigView}.
 */
public final class ConfigHolderView implements GTConfigView {

    @Override
    public boolean recipeProgressLowEnergy() {
        return ConfigHolder.machines.recipeProgressLowEnergy;
    }

    @Override
    public boolean requireGTToolsForBlocks() {
        return ConfigHolder.machines.requireGTToolsForBlocks;
    }

    @Override
    public boolean enableMaintenance() {
        return ConfigHolder.machines.enableMaintenance;
    }

    @Override
    public boolean gt6StylePipesCables() {
        return ConfigHolder.machines.gt6StylePipesCables;
    }

    @Override
    public double multiblockSteamToEU() {
        return ConfigHolder.machines.multiblockSteamToEU;
    }

    @Override
    public boolean doesExplosionDamagesTerrain() {
        return ConfigHolder.machines.doesExplosionDamagesTerrain;
    }

    @Override
    public boolean doTerrainExplosion() {
        return ConfigHolder.machines.doTerrainExplosion;
    }

    @Override
    public int energyUsageMultiplier() {
        return ConfigHolder.machines.energyUsageMultiplier;
    }

    @Override
    public boolean machineSounds() {
        return ConfigHolder.machines.machineSounds;
    }

    @Override
    public @NotNull @UnmodifiableView List<String> boilerFluids() {
        return view(ConfigHolder.machines.boilerFluids);
    }

    @Override
    public boolean enableCleanroom() {
        return ConfigHolder.machines.enableCleanroom;
    }

    @Override
    public boolean cleanMultiblocks() {
        return ConfigHolder.machines.cleanMultiblocks;
    }

    @Override
    public @NotNull String replaceMinedBlocksWith() {
        return ConfigHolder.machines.replaceMinedBlocksWith;
    }

    @Override
    public boolean enableResearch() {
        return ConfigHolder.machines.enableResearch;
    }

    @Override
    public boolean highTierContent() {
        return ConfigHolder.machines.highTierContent;
    }

    @Override
    public boolean allowTickAcceleration() {
        return ConfigHolder.machines.allowTickAcceleration;
    }

    @Override
    public int minVeinsInSection() {
        return ConfigHolder.worldgen.minVeinsInSection;
    }

    @Override
    public int additionalVeinsInSection() {
        return ConfigHolder.worldgen.additionalVeinsInSection;
    }

    @Override
    public boolean generateVeinsInCenterOfChunk() {
        return ConfigHolder.worldgen.generateVeinsInCenterOfChunk;
    }

    @Override
    public boolean allUniqueStoneTypes() {
        return ConfigHolder.worldgen.allUniqueStoneTypes;
    }

    @Override
    public boolean generateLowQualityGems() {
        return ConfigHolder.recipes.generateLowQualityGems;
    }

    @Override
    public boolean hideFilledCellsInJEI() {
        return ConfigHolder.compat.hideFilledCellsInJEI;
    }

    @Override
    public @NotNull @UnmodifiableView List<String> modPriorities() {
        return view(ConfigHolder.compat.modPriorities);
    }

    @Override
    public boolean nativeEUToFE() {
        return ConfigHolder.compat.energy.nativeEUToFE;
    }

    @Override
    public int feToEuRatio() {
        return ConfigHolder.compat.energy.feToEuRatio;
    }

    @Override
    public int euToFeRatio() {
        return ConfigHolder.compat.energy.euToFeRatio;
    }

    @Override
    public boolean debug() {
        return ConfigHolder.misc.debug;
    }

    @Override
    public boolean specialEvents() {
        return ConfigHolder.misc.specialEvents;
    }

    @Override
    public boolean ignoreErrorOrInvalidRecipes() {
        return ConfigHolder.misc.ignoreErrorOrInvalidRecipes;
    }

    @Override
    public double resolution() {
        return ConfigHolder.client.resolution;
    }

    @Override
    public boolean machinesEmissiveTextures() {
        return ConfigHolder.client.machinesEmissiveTextures;
    }

    @Override
    public boolean toolUseSounds() {
        return ConfigHolder.client.toolUseSounds;
    }

    @Override
    public boolean toolCraftingSounds() {
        return ConfigHolder.client.toolCraftingSounds;
    }

    @Override
    public int defaultPaintingColor() {
        return ConfigHolder.client.defaultPaintingColor;
    }

    @Override
    public int defaultUIColor() {
        return ConfigHolder.client.defaultUIColor;
    }

    @Override
    public boolean useSprayCanColorInUI() {
        return ConfigHolder.client.useSprayCanColorInUI;
    }

    @Override
    public boolean smoothProgressBars() {
        return ConfigHolder.client.guiConfig.smoothProgressBars;
    }

    @Override
    public int hudLocation() {
        return ConfigHolder.client.armorHud.hudLocation;
    }

    @Override
    public int hudOffsetX() {
        return ConfigHolder.client.armorHud.hudOffsetX;
    }

    @Override
    public int hudOffsetY() {
        return ConfigHolder.client.armorHud.hudOffsetY;
    }

    @Override
    public int rngDamageElectricTools() {
        return ConfigHolder.tools.rngDamageElectricTools;
    }

    /** Wraps the config entry's current array rather than copying it: boiler fluids are read for each fluid test. */
    private static @NotNull @UnmodifiableView List<String> view(@NotNull String[] entries) {
        return Collections.unmodifiableList(Arrays.asList(entries));
    }
}
