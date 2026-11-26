package gregtech.api.metatileentity;

import gregtech.Bootstrap;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.mui.SteamTexture;
import gregtech.api.mui.widget.RecipeFailureWidget;
import gregtech.api.mui.widget.RecipeProgressWidget;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.ui.RecipeLayoutListing;
import gregtech.api.recipes.ui.RecipeLayoutProgress;
import gregtech.api.recipes.ui.RecipeMapLayout;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.steam.SteamAlloySmelter;
import gregtech.common.metatileentities.steam.SteamCompressor;
import gregtech.common.metatileentities.steam.SteamExtractor;
import gregtech.common.metatileentities.steam.SteamFurnace;
import gregtech.common.metatileentities.steam.SteamHammer;
import gregtech.common.metatileentities.steam.SteamMacerator;
import gregtech.common.metatileentities.steam.SteamRockBreaker;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static gregtech.api.metatileentity.SimpleMachineScreenTest.collect;
import static gregtech.api.metatileentity.SimpleMachineScreenTest.ofType;
import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Each steam single-block GTCEu registers, bronze and steel, keeps the class, recipe map, casing, overlay, inventory
 * and screen it had, both as registered and as placed. The screens are the old ones on ModularUI 2: the same slots,
 * progress bar and images at the same positions, in the textures and theme of the machine's pressure.
 */
public class SteamMachineFamilyTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    static Stream<Machine> machines() {
        return Stream.of(false, true).flatMap(SteamMachineFamilyTest::machinesIn);
    }

    private static Stream<Machine> machinesIn(boolean steel) {
        return Stream.of(
                new Machine("steam_extractor", SteamExtractor.class, SteamExtractor::new, steel,
                        RecipeMaps.EXTRACTOR_RECIPES, Textures.EXTRACTOR_OVERLAY, false, 1, -1, List.of(
                                input(0, 53, GTGuiTextures.EXTRACTOR_OVERLAY_STEAM, steel),
                                progress(79, 25, 20, 18, GTGuiTextures.PROGRESS_BAR_EXTRACT_STEAM, Direction.RIGHT,
                                        steel),
                                output(null, steel))),
                new Machine("steam_macerator", SteamMacerator.class, SteamMacerator::new, steel,
                        RecipeMaps.MACERATOR_RECIPES, Textures.MACERATOR_OVERLAY, false, 1, 1, List.of(
                                input(0, 53, GTGuiTextures.CRUSHED_ORE_OVERLAY_STEAM, steel),
                                progress(79, 26, 21, 18, GTGuiTextures.PROGRESS_BAR_MACERATE_STEAM, Direction.RIGHT,
                                        steel),
                                output(GTGuiTextures.DUST_OVERLAY_STEAM, steel))),
                new Machine("steam_compressor", SteamCompressor.class, SteamCompressor::new, steel,
                        RecipeMaps.COMPRESSOR_RECIPES, Textures.COMPRESSOR_OVERLAY, false, 1, -1, List.of(
                                input(0, 53, GTGuiTextures.COMPRESSOR_OVERLAY_STEAM, steel),
                                progress(78, 25, 20, 18, GTGuiTextures.PROGRESS_BAR_COMPRESS_STEAM, Direction.RIGHT,
                                        steel),
                                output(null, steel))),
                new Machine("steam_hammer", SteamHammer.class, SteamHammer::new, steel,
                        RecipeMaps.FORGE_HAMMER_RECIPES, Textures.FORGE_HAMMER_OVERLAY, false, 1, -1, List.of(
                                input(0, 53, GTGuiTextures.HAMMER_OVERLAY_STEAM, steel),
                                progress(79, 25, 20, 18, GTGuiTextures.PROGRESS_BAR_HAMMER_STEAM, Direction.DOWN,
                                        steel),
                                image(79, 41, 20, 18, GTGuiTextures.PROGRESS_BAR_HAMMER_BASE_STEAM, steel),
                                output(null, steel))),
                new Machine("steam_furnace", SteamFurnace.class, SteamFurnace::new, steel,
                        RecipeMaps.FURNACE_RECIPES, Textures.FURNACE_OVERLAY, true, 1, -1, List.of(
                                input(0, 53, GTGuiTextures.FURNACE_OVERLAY_STEAM, steel),
                                progress(79, 26, 20, 16, GTGuiTextures.PROGRESS_BAR_ARROW_STEAM, Direction.RIGHT,
                                        steel),
                                output(null, steel))),
                new Machine("steam_alloy_smelter", SteamAlloySmelter.class, SteamAlloySmelter::new, steel,
                        RecipeMaps.ALLOY_SMELTER_RECIPES, Textures.ALLOY_SMELTER_OVERLAY, true, 2, -1, List.of(
                                input(0, 53, GTGuiTextures.FURNACE_OVERLAY_STEAM, steel),
                                input(1, 35, GTGuiTextures.FURNACE_OVERLAY_STEAM, steel),
                                progress(79, 26, 20, 16, GTGuiTextures.PROGRESS_BAR_ARROW_STEAM, Direction.RIGHT,
                                        steel),
                                output(null, steel))));
    }

    @ParameterizedTest
    @MethodSource("machines")
    public void keepsItsRecipeMapPressureCasingAndInventory(Machine expected) {
        SteamMetaTileEntity registered = expected.create();
        for (SteamMetaTileEntity machine : List.of(registered, place(registered))) {
            assertSame(expected.type(), machine.getClass());
            assertEquals(expected.id(), machine.metaTileEntityId);
            assertEquals(expected.steel(), machine.isHighPressure);
            assertSame(expected.recipeMap(), machine.workableHandler.getRecipeMap());
            assertSame(expected.overlay(), machine.renderer);
            assertSame(expected.casing(), machine.getBaseRenderer());
            assertEquals(expected.inputs(), machine.getImportItems().getSlots());
            assertEquals(1, machine.getExportItems().getSlots());
            assertEquals(expected.itemOutputLimit(), machine.getItemOutputLimit());
            assertEquals(1, machine.getImportFluids().getTanks());
            assertEquals(16000, machine.getImportFluids().getTankAt(0).getCapacity());
        }
    }

    @ParameterizedTest
    @MethodSource("machines")
    public void keepsItsScreen(Machine expected) {
        SteamMetaTileEntity machine = place(expected.create());
        RecipeMapLayout layout = machine.createScreenLayout(() -> 0);
        assertEquals(expected.screen(), RecipeLayoutListing.describe(layout));
        assertSame(expected.recipeMap(), layout.getElements(RecipeLayoutProgress.class).get(0).recipeMap());
        checkScreen(machine, expected.inputs(), 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    public void rockBreakerKeepsItsScreen(boolean steel) {
        SteamMetaTileEntity machine = place(new SteamRockBreaker(
                gregtechId("steam_rock_breaker" + (steel ? "_steel" : "_bronze")), steel));
        RecipeMapLayout layout = machine.createScreenLayout(() -> 0);
        UITexture slot = GTGuiTextures.SLOT_STEAM.get(steel);
        UITexture output = GTGuiTextures.CRUSHED_ORE_OVERLAY_STEAM.get(steel);
        assertEquals(List.of(
                slot("in", 0, 53, 34, slot, GTGuiTextures.DUST_OVERLAY_STEAM.get(steel)),
                progress(79, 35, 21, 18, GTGuiTextures.PROGRESS_BAR_MACERATE_STEAM, Direction.RIGHT, steel),
                slot("out", 0, 107, 25, slot, output),
                slot("out", 1, 125, 25, slot, output),
                slot("out", 2, 107, 43, slot, output),
                slot("out", 3, 125, 43, slot, output)), RecipeLayoutListing.describe(layout));
        assertSame(RecipeMaps.ROCK_BREAKER_RECIPES,
                layout.getElements(RecipeLayoutProgress.class).get(0).recipeMap());
        checkScreen(machine, 1, 4);
    }

    /**
     * The screen every steam machine shares: the theme of its pressure, the missing steam indicator, the progress bar
     * that shows its recipes, its slots with the old rules, and the player's inventory.
     */
    private static void checkScreen(SteamMetaTileEntity machine, int inputs, int outputs) {
        assertSame(machine.isHighPressure ? GTGuiTheme.STEEL : GTGuiTheme.BRONZE, machine.getUITheme());
        ModularPanel panel = machine.buildUI(null, new PanelSyncManager(new ModularSyncManager(false), true),
                new UISettings());
        List<IWidget> widgets = new ArrayList<>();
        collect(panel, widgets);
        assertEquals(1, ofType(widgets, RecipeFailureWidget.class).size());
        List<RecipeProgressWidget> bars = ofType(widgets, RecipeProgressWidget.class);
        assertEquals(1, bars.size());
        assertSame(machine.workableHandler.getRecipeMap(), bars.get(0).getRecipeMap());

        int player = 0;
        List<ModularSlot> shownInputs = new ArrayList<>();
        List<ModularSlot> shownOutputs = new ArrayList<>();
        for (ItemSlot widget : ofType(widgets, ItemSlot.class)) {
            if (!widget.isSynced()) {
                player++;
            } else if (widget.getSlot().getItemHandler() == machine.importItems) {
                shownInputs.add(widget.getSlot());
            } else {
                assertSame(machine.exportItems, widget.getSlot().getItemHandler());
                shownOutputs.add(widget.getSlot());
            }
        }
        assertEquals(36, player);
        assertEquals(inputs, shownInputs.size());
        assertEquals(outputs, shownOutputs.size());
        for (ModularSlot slot : shownInputs) {
            assertTrue(slot.isCanPut() && slot.isCanTake() && slot.getSlotGroup() != null);
        }
        for (ModularSlot slot : shownOutputs) {
            assertTrue(!slot.isCanPut() && slot.isCanTake() && slot.getSlotGroup() == null);
        }
    }

    /** As the machine block does when it is placed: a copy of the registered machine in a holder. */
    private static SteamMetaTileEntity place(SteamMetaTileEntity registered) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        SteamMetaTileEntity placed = (SteamMetaTileEntity) holder.setMetaTileEntity(registered);
        holder.setWorld(DummyWorld.INSTANCE);
        return placed;
    }

    private static String input(int slot, int x, SteamTexture overlay, boolean steel) {
        return slot("in", slot, x, 25, GTGuiTextures.SLOT_STEAM.get(steel), overlay.get(steel));
    }

    private static String output(SteamTexture overlay, boolean steel) {
        UITexture slot = GTGuiTextures.SLOT_STEAM.get(steel);
        return overlay == null ? slot("out", 0, 107, 25, slot) : slot("out", 0, 107, 25, slot, overlay.get(steel));
    }

    private static String slot(String side, int index, int x, int y, UITexture... background) {
        List<String> textures = new ArrayList<>();
        for (UITexture texture : background) textures.add(texture.location.toString());
        return String.format("item %s %d at %d,%d 18x18 %s", side, index, x, y, textures);
    }

    private static String progress(int x, int y, int width, int height, SteamTexture bar, Direction direction,
                                   boolean steel) {
        return String.format("progress at %d,%d %dx%d %s %s recipes", x, y, width, height, bar.get(steel).location,
                direction);
    }

    private static String image(int x, int y, int width, int height, SteamTexture texture, boolean steel) {
        return String.format("image at %d,%d %dx%d %s", x, y, width, height, texture.get(steel).location);
    }

    /** One registered steam machine as it was when each had its own class. */
    record Machine(String name, Class<? extends SteamMetaTileEntity> type,
                   BiFunction<ResourceLocation, Boolean, ? extends SteamMetaTileEntity> factory, boolean steel,
                   RecipeMap<?> recipeMap, ICubeRenderer overlay, boolean bricked, int inputs, int itemOutputLimit,
                   List<String> screen) {

        ResourceLocation id() {
            return gregtechId(name + (steel ? "_steel" : "_bronze"));
        }

        SteamMetaTileEntity create() {
            return factory.apply(id(), steel);
        }

        ICubeRenderer casing() {
            if (steel) return bricked ? Textures.STEAM_BRICKED_CASING_STEEL : Textures.STEAM_CASING_STEEL;
            return bricked ? Textures.STEAM_BRICKED_CASING_BRONZE : Textures.STEAM_CASING_BRONZE;
        }

        @Override
        public String toString() {
            return id().getPath();
        }
    }
}
