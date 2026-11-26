package gregtech.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;

/**
 * The configuration values GTCEu's API reads, read-only. {@link GTConfig#get()} answers it.
 * <p>
 * Each method answers the entry of the same name in GTCEu's config file as it is now, also after the entry was
 * changed in game, so read it where the value is used rather than keeping it. The entries keep their names and places
 * in the file; {@code gregtech.common.ConfigHolder} still holds them for code that reads it directly.
 */
public interface GTConfigView {

    // Machine Options

    /** Whether insufficient energy resets recipe progress to zero, rather than winding it back at twice the speed. */
    boolean recipeProgressLowEnergy();

    /** Whether machines, casings, pipes and cables need GregTech tools to break. */
    boolean requireGTToolsForBlocks();

    /** Whether multiblocks need a maintenance hatch and have maintenance problems. */
    boolean enableMaintenance();

    /** Whether pipes and cables connect only when placed directly onto another pipe or cable. */
    boolean gt6StylePipesCables();

    /** The EU a steam multiblock makes from one litre of steam. */
    double multiblockSteamToEU();

    /** Whether exploding machines and boilers damage the terrain. */
    boolean doesExplosionDamagesTerrain();

    /** Whether machines explode in the rain or next to fire, lava and similar terrain. */
    boolean doTerrainExplosion();

    /** The energy use multiplier of electric items, in percent. */
    int energyUsageMultiplier();

    /** Whether active machines play their sounds. */
    boolean machineSounds();

    /** The names of the fluids boilers accept besides water and distilled water. */
    @NotNull
    @UnmodifiableView
    List<String> boilerFluids();

    /** Whether recipes can require a cleanroom. */
    boolean enableCleanroom();

    /** Whether multiblocks ignore cleanroom requirements. */
    boolean cleanMultiblocks();

    /** The block miners leave in place of a mined ore, as {@code modid:name} or {@code modid:name:meta}. */
    @NotNull
    String replaceMinedBlocksWith();

    /** Whether assembly line recipes can require research. */
    boolean enableResearch();

    /**
     * Whether the config file asks for content above UV. Addons ask {@code GregTechAPI.isHighTier()}, which also
     * counts the other mods that ask for it.
     */
    boolean highTierContent();

    /** Whether outside tick acceleration effects may affect GregTech machines. */
    boolean allowTickAcceleration();

    // Worldgen Options

    /** The least number of ore veins in a grid section. */
    int minVeinsInSection();

    /** The greatest number of random ore veins a grid section gets on top of {@link #minVeinsInSection()}. */
    int additionalVeinsInSection();

    /** Whether ore veins are generated in the centre of chunks. */
    boolean generateVeinsInCenterOfChunk();

    /** Whether every stone type drops ore blocks of its own, rather than only stone, netherrack and end stone. */
    boolean allUniqueStoneTypes();

    // Recipe Options

    /** Whether flawed and chipped gems, and the recipes with them, are generated. */
    boolean generateLowQualityGems();

    // Compatibility Options

    /** Whether filled cells are hidden in JEI and the creative search. */
    boolean hideFilledCellsInJEI();

    /** The mod ids whose items the ore dictionary unification prefers, highest priority first. */
    @NotNull
    @UnmodifiableView
    List<String> modPriorities();

    /** Whether GregTech cables and wires deliver Forge Energy natively. */
    boolean nativeEUToFE();

    /** How many Forge Energy units make one EU. */
    int feToEuRatio();

    /** How many Forge Energy units one EU makes. */
    int euToFeRatio();

    // Miscellaneous Options

    /** Whether GTCEu logs verbosely. */
    boolean debug();

    /** Whether special event features, such as those at Christmas, are on. */
    boolean specialEvents();

    /** Whether erroneous and invalid recipes are skipped rather than stopping the game. */
    boolean ignoreErrorOrInvalidRecipes();

    // Client Options

    /** The resolution level of fragment shaders. */
    double resolution();

    /** Whether machines and multiblock parts draw their emissive textures. */
    boolean machinesEmissiveTextures();

    /** Whether tools play sounds when used outside of crafting. */
    boolean toolUseSounds();

    /** Whether tools play sounds when used in crafting. */
    boolean toolCraftingSounds();

    /** The colour, as {@code 0xRRGGBB}, laid over unpainted machines. */
    int defaultPaintingColor();

    /** The colour, as {@code 0xRRGGBB}, laid over machine screens. */
    int defaultUIColor();

    /** Whether a painted machine's screen takes the machine's colour. */
    boolean useSprayCanColorInUI();

    /** Whether progress bars move smoothly, rather than in steps like the furnace's. */
    boolean smoothProgressBars();

    /** The corner the armor HUD is drawn in: 1 upper left, 2 upper right, 3 lower left, 4 lower right. */
    int hudLocation();

    /** The armor HUD's horizontal distance from its corner. */
    int hudOffsetX();

    /** The armor HUD's vertical distance from its corner. */
    int hudOffsetY();

    // Tool and Armor Options

    /** The chance, in percent, that an electric tool takes damage besides using energy. */
    int rngDamageElectricTools();
}
