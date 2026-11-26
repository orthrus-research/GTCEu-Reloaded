package gregtech.common.metatileentities.steam;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.SteamTexture;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ui.RecipeMapLayout;
import gregtech.api.recipes.ui.RecipeMapUI;
import gregtech.client.particle.IMachineParticleEffect;
import gregtech.client.renderer.ICubeRenderer;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widgets.ProgressWidget.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * What sets one {@link SimpleSteamMachine} apart from another: its recipe map, front overlay, casing, screen and
 * particle effects.
 * <p>
 * The screen shows the input slots from right to left, the first at (53, 25) and each further one a slot to the left,
 * then the progress bar, an optional image and the output slot at (107, 25). Each texture is drawn in the machine's
 * bronze or steel variant.
 */
public final class SteamMachineDefinition {

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_Y = 25;
    private static final int FIRST_INPUT_X = 53;
    private static final int OUTPUT_X = 107;

    final RecipeMap<?> recipeMap;
    final ICubeRenderer overlay;
    final boolean brickedCasing;
    final SteamTexture[] inputOverlays;
    final @Nullable SteamTexture outputOverlay;
    final SteamTexture progressBar;
    final Direction progressDirection;
    final int progressX;
    final int progressY;
    final int progressWidth;
    final int progressHeight;
    final @Nullable SteamTexture image;
    final int imageX;
    final int imageY;
    final int imageWidth;
    final int imageHeight;
    final int itemOutputLimit;
    final @Nullable IMachineParticleEffect tickingEffect;
    final @Nullable IMachineParticleEffect randomEffect;

    private SteamMachineDefinition(Builder builder) {
        this.recipeMap = builder.recipeMap;
        this.overlay = builder.overlay;
        this.brickedCasing = builder.brickedCasing;
        this.inputOverlays = builder.inputOverlays;
        this.outputOverlay = builder.outputOverlay;
        this.progressBar = builder.progressBar;
        this.progressDirection = builder.progressDirection;
        this.progressX = builder.progressX;
        this.progressY = builder.progressY;
        this.progressWidth = builder.progressWidth;
        this.progressHeight = builder.progressHeight;
        this.image = builder.image;
        this.imageX = builder.imageX;
        this.imageY = builder.imageY;
        this.imageWidth = builder.imageWidth;
        this.imageHeight = builder.imageHeight;
        this.itemOutputLimit = builder.itemOutputLimit;
        this.tickingEffect = builder.tickingEffect;
        this.randomEffect = builder.randomEffect;
    }

    /**
     * Lay out the screen of a machine of this kind: its input slots, progress bar, image and output slot.
     *
     * @param highPressure whether the machine is a high pressure, steel one, whose textures it draws
     * @param progress     the machine's progress, from 0 to 1
     * @return the layout
     */
    public @NotNull RecipeMapLayout createLayout(boolean highPressure, @NotNull DoubleSupplier progress) {
        UITexture slot = GTGuiTextures.SLOT_STEAM.get(highPressure);
        RecipeMapLayout layout = new RecipeMapLayout(RecipeMapUI.WIDTH, RecipeMapUI.HEIGHT);
        for (int i = 0; i < inputOverlays.length; i++) {
            layout.slot(false, false, i, FIRST_INPUT_X - i * SLOT_SIZE, SLOT_Y, slot,
                    inputOverlays[i].get(highPressure));
        }

        layout.progressBar(progressX, progressY, progressWidth, progressHeight, progressBar.get(highPressure),
                progressDirection, progress, recipeMap);
        if (image != null) {
            layout.image(imageX, imageY, imageWidth, imageHeight, image.get(highPressure));
        }

        if (outputOverlay == null) {
            layout.slot(true, false, 0, OUTPUT_X, SLOT_Y, slot);
        } else {
            layout.slot(true, false, 0, OUTPUT_X, SLOT_Y, slot, outputOverlay.get(highPressure));
        }
        return layout;
    }

    /**
     * @param recipeMap the recipes the machine runs
     * @param overlay   the overlay on the machine's front
     */
    public static @NotNull Builder builder(@NotNull RecipeMap<?> recipeMap, @NotNull ICubeRenderer overlay) {
        return new Builder(recipeMap, overlay);
    }

    public static final class Builder {

        private final RecipeMap<?> recipeMap;
        private final ICubeRenderer overlay;
        private boolean brickedCasing;
        private SteamTexture[] inputOverlays;
        private SteamTexture outputOverlay;
        private SteamTexture progressBar;
        private Direction progressDirection;
        private int progressX;
        private int progressY;
        private int progressWidth;
        private int progressHeight;
        private SteamTexture image;
        private int imageX;
        private int imageY;
        private int imageWidth;
        private int imageHeight;
        private int itemOutputLimit = -1;
        private IMachineParticleEffect tickingEffect;
        private IMachineParticleEffect randomEffect;

        private Builder(RecipeMap<?> recipeMap, ICubeRenderer overlay) {
            this.recipeMap = Objects.requireNonNull(recipeMap, "recipeMap");
            this.overlay = Objects.requireNonNull(overlay, "overlay");
        }

        /** Build the machine in bricked casing instead of plain casing. */
        public @NotNull Builder brickedCasing() {
            this.brickedCasing = true;
            return this;
        }

        /** One input slot for each overlay, which is drawn over the slot while it is empty. */
        public @NotNull Builder inputs(@NotNull SteamTexture... overlays) {
            this.inputOverlays = overlays.clone();
            return this;
        }

        /** The overlay drawn over the output slot while it is empty; by default there is none. */
        public @NotNull Builder outputOverlay(@NotNull SteamTexture overlay) {
            this.outputOverlay = overlay;
            return this;
        }

        /** The progress bar, whose texture holds the empty bar above the full one, filling in its direction. */
        public @NotNull Builder progressBar(@NotNull SteamTexture texture, @NotNull Direction direction, int x, int y,
                                            int width, int height) {
            this.progressBar = texture;
            this.progressDirection = direction;
            this.progressX = x;
            this.progressY = y;
            this.progressWidth = width;
            this.progressHeight = height;
            return this;
        }

        /** An image drawn after the progress bar, such as the anvil under the forge hammer's. */
        public @NotNull Builder image(@NotNull SteamTexture texture, int x, int y, int width, int height) {
            this.image = texture;
            this.imageX = x;
            this.imageY = y;
            this.imageWidth = width;
            this.imageHeight = height;
            return this;
        }

        /** The most item outputs a recipe gives; the rest are dropped. By default, -1: every output. */
        public @NotNull Builder itemOutputLimit(int limit) {
            this.itemOutputLimit = limit;
            return this;
        }

        /** The effect run every client tick while the machine works. */
        public @NotNull Builder tickingEffect(@NotNull IMachineParticleEffect effect) {
            this.tickingEffect = effect;
            return this;
        }

        /** The effect run on random display ticks while the machine works. */
        public @NotNull Builder randomEffect(@NotNull IMachineParticleEffect effect) {
            this.randomEffect = effect;
            return this;
        }

        public @NotNull SteamMachineDefinition build() {
            if (inputOverlays == null || inputOverlays.length == 0) {
                throw new IllegalStateException("A steam machine needs at least one input slot");
            }
            if (progressBar == null) throw new IllegalStateException("A steam machine needs a progress bar");
            return new SteamMachineDefinition(this);
        }
    }
}
