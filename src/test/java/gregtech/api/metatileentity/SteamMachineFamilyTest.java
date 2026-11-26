package gregtech.api.metatileentity;

import gregtech.Bootstrap;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.Widget;
import gregtech.api.gui.resources.IGuiTexture;
import gregtech.api.gui.resources.SteamTexture;
import gregtech.api.gui.resources.TextureArea;
import gregtech.api.gui.widgets.ImageWidget;
import gregtech.api.gui.widgets.ProgressWidget;
import gregtech.api.gui.widgets.ProgressWidget.MoveType;
import gregtech.api.gui.widgets.RecipeFailureWidget;
import gregtech.api.gui.widgets.RecipeProgressWidget;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.Position;
import gregtech.api.util.Size;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.steam.SteamAlloySmelter;
import gregtech.common.metatileentities.steam.SteamCompressor;
import gregtech.common.metatileentities.steam.SteamExtractor;
import gregtech.common.metatileentities.steam.SteamFurnace;
import gregtech.common.metatileentities.steam.SteamHammer;
import gregtech.common.metatileentities.steam.SteamMacerator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.SlotItemHandler;

import com.mojang.authlib.GameProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static gregtech.api.util.GTUtility.gregtechId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Each steam single-block GTCEu registers, bronze and steel, keeps the class, recipe map, casing, overlay, inventory
 * and screen it had, both as registered and as placed.
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
                                input(0, 53, GuiTextures.EXTRACTOR_OVERLAY_STEAM, steel),
                                progress(79, 25, 20, 18, GuiTextures.PROGRESS_BAR_EXTRACT_STEAM, MoveType.HORIZONTAL,
                                        RecipeMaps.EXTRACTOR_RECIPES, steel),
                                output(null, steel))),
                new Machine("steam_macerator", SteamMacerator.class, SteamMacerator::new, steel,
                        RecipeMaps.MACERATOR_RECIPES, Textures.MACERATOR_OVERLAY, false, 1, 1, List.of(
                                input(0, 53, GuiTextures.CRUSHED_ORE_OVERLAY_STEAM, steel),
                                progress(79, 26, 21, 18, GuiTextures.PROGRESS_BAR_MACERATE_STEAM, MoveType.HORIZONTAL,
                                        RecipeMaps.MACERATOR_RECIPES, steel),
                                output(GuiTextures.DUST_OVERLAY_STEAM, steel))),
                new Machine("steam_compressor", SteamCompressor.class, SteamCompressor::new, steel,
                        RecipeMaps.COMPRESSOR_RECIPES, Textures.COMPRESSOR_OVERLAY, false, 1, -1, List.of(
                                input(0, 53, GuiTextures.COMPRESSOR_OVERLAY_STEAM, steel),
                                progress(78, 25, 20, 18, GuiTextures.PROGRESS_BAR_COMPRESS_STEAM, MoveType.HORIZONTAL,
                                        RecipeMaps.COMPRESSOR_RECIPES, steel),
                                output(null, steel))),
                new Machine("steam_hammer", SteamHammer.class, SteamHammer::new, steel,
                        RecipeMaps.FORGE_HAMMER_RECIPES, Textures.FORGE_HAMMER_OVERLAY, false, 1, -1, List.of(
                                input(0, 53, GuiTextures.HAMMER_OVERLAY_STEAM, steel),
                                progress(79, 25, 20, 18, GuiTextures.PROGRESS_BAR_HAMMER_STEAM,
                                        MoveType.VERTICAL_DOWNWARDS, RecipeMaps.FORGE_HAMMER_RECIPES, steel),
                                image(79, 41, 20, 18, GuiTextures.PROGRESS_BAR_HAMMER_BASE_STEAM, steel),
                                output(null, steel))),
                new Machine("steam_furnace", SteamFurnace.class, SteamFurnace::new, steel,
                        RecipeMaps.FURNACE_RECIPES, Textures.FURNACE_OVERLAY, true, 1, -1, List.of(
                                input(0, 53, GuiTextures.FURNACE_OVERLAY_STEAM, steel),
                                progress(79, 26, 20, 16, GuiTextures.PROGRESS_BAR_ARROW_STEAM, MoveType.HORIZONTAL,
                                        RecipeMaps.FURNACE_RECIPES, steel),
                                output(null, steel))),
                new Machine("steam_alloy_smelter", SteamAlloySmelter.class, SteamAlloySmelter::new, steel,
                        RecipeMaps.ALLOY_SMELTER_RECIPES, Textures.ALLOY_SMELTER_OVERLAY, true, 2, -1, List.of(
                                input(0, 53, GuiTextures.FURNACE_OVERLAY_STEAM, steel),
                                input(1, 35, GuiTextures.FURNACE_OVERLAY_STEAM, steel),
                                progress(79, 26, 20, 16, GuiTextures.PROGRESS_BAR_ARROW_STEAM, MoveType.HORIZONTAL,
                                        RecipeMaps.ALLOY_SMELTER_RECIPES, steel),
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
    public void keepsItsScreen(Machine expected) throws ReflectiveOperationException {
        SteamMetaTileEntity machine = place(expected.create());
        ModularUI ui = machine.createUI(player());
        List<Widget> widgets = ui.guiWidgets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        List<String> screen = new ArrayList<>();
        for (Widget widget : widgets) {
            String description = describe(widget, machine);
            if (description != null) screen.add(description);
        }
        assertEquals(expected.screen(), screen);
        // the template every steam machine shares: title, missing steam indicator and the player's inventory
        assertEquals(1, widgets.stream().filter(RecipeFailureWidget.class::isInstance).count());
        assertEquals(36, widgets.stream().filter(SlotWidget.class::isInstance).count() - expected.inputs() - 1);
    }

    /** As the machine block does when it is placed: a copy of the registered machine in a holder. */
    private static SteamMetaTileEntity place(SteamMetaTileEntity registered) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        SteamMetaTileEntity placed = (SteamMetaTileEntity) holder.setMetaTileEntity(registered);
        holder.setWorld(DummyWorld.INSTANCE);
        return placed;
    }

    private static EntityPlayer player() {
        return new EntityPlayer(DummyWorld.INSTANCE,
                new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000007"), "steam_machine_test")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
    }

    /** One line for each of the machine's own widgets; null for the shared template and the player's inventory. */
    private static String describe(Widget widget, MetaTileEntity machine) throws ReflectiveOperationException {
        Position position = widget.getSelfPosition();
        Size size = widget.getSize();
        if (widget instanceof SlotWidget slot) {
            if (!(slot.getHandle() instanceof SlotItemHandler handle)) return null;
            String inventory;
            if (handle.getItemHandler() == machine.getImportItems()) inventory = "input";
            else if (handle.getItemHandler() == machine.getExportItems()) inventory = "output";
            else return null;
            boolean take = read(SlotWidget.class, "canTakeItems", slot);
            boolean put = read(SlotWidget.class, "canPutItems", slot);
            IGuiTexture[] background = read(SlotWidget.class, "backgroundTexture", slot);
            return slot(inventory, handle.getSlotIndex(), position.x, position.y, take, put, background);
        }
        if (widget instanceof RecipeProgressWidget progress) {
            TextureArea bar = read(ProgressWidget.class, "emptyBarArea", progress);
            MoveType moveType = read(ProgressWidget.class, "moveType", progress);
            RecipeMap<?> recipeMap = read(RecipeProgressWidget.class, "recipeMap", progress);
            return progress(position.x, position.y, size.width, size.height, bar, moveType, recipeMap);
        }
        if (widget instanceof ImageWidget image && !(widget instanceof RecipeFailureWidget)) {
            IGuiTexture area = read(ImageWidget.class, "area", image);
            return image(position.x, position.y, size.width, size.height, area);
        }
        return null;
    }

    private static String input(int slot, int x, SteamTexture overlay, boolean steel) {
        return slot("input", slot, x, 25, true, true,
                new IGuiTexture[] { GuiTextures.SLOT_STEAM.get(steel), overlay.get(steel) });
    }

    private static String output(SteamTexture overlay, boolean steel) {
        IGuiTexture[] background = overlay == null ? new IGuiTexture[] { GuiTextures.SLOT_STEAM.get(steel) } :
                new IGuiTexture[] { GuiTextures.SLOT_STEAM.get(steel), overlay.get(steel) };
        return slot("output", 0, 107, 25, true, false, background);
    }

    private static String progress(int x, int y, int width, int height, SteamTexture bar, MoveType moveType,
                                   RecipeMap<?> recipeMap, boolean steel) {
        return progress(x, y, width, height, bar.get(steel), moveType, recipeMap);
    }

    private static String image(int x, int y, int width, int height, SteamTexture texture, boolean steel) {
        return image(x, y, width, height, texture.get(steel));
    }

    private static String slot(String inventory, int slot, int x, int y, boolean take, boolean put,
                               IGuiTexture[] background) {
        List<String> textures = new ArrayList<>();
        for (IGuiTexture texture : background) textures.add(texture(texture));
        return String.format("%s slot %d at %d,%d%s%s: %s", inventory, slot, x, y, take ? " take" : "",
                put ? " put" : "", textures);
    }

    private static String progress(int x, int y, int width, int height, TextureArea bar, MoveType moveType,
                                   RecipeMap<?> recipeMap) {
        return String.format("progress bar at %d,%d %dx%d: %s %s for %s", x, y, width, height, texture(bar),
                moveType, recipeMap.getUnlocalizedName());
    }

    private static String image(int x, int y, int width, int height, IGuiTexture texture) {
        return String.format("image at %d,%d %dx%d: %s", x, y, width, height, texture(texture));
    }

    private static String texture(IGuiTexture texture) {
        return texture instanceof TextureArea area ? area.imageLocation.toString() : String.valueOf(texture);
    }

    @SuppressWarnings("unchecked")
    private static <T> T read(Class<?> owner, String name, Object instance) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(instance);
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
