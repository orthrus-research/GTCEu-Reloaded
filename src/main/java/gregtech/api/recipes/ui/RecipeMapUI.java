package gregtech.api.recipes.ui;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.resources.TextureArea;
import gregtech.api.gui.widgets.ProgressWidget.MoveType;
import gregtech.api.gui.widgets.RecipeProgressWidget;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.gui.widgets.TankWidget;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.function.impl.TimedProgressSupplier;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectMap;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.function.DoubleSupplier;

/**
 * The screen of a recipe map: how its machines and its recipe viewer category lay out their slots, tanks, progress
 * bar and image, with the textures the map sets.
 */
@ApiStatus.Experimental
public class RecipeMapUI<R extends RecipeMap<?>> {

    /** The width of a recipe map's screen. */
    public static final int WIDTH = 176;
    /** The height of a recipe map's screen whose slots are not moved down. */
    public static final int HEIGHT = 166;
    /** How many ticks a recipe viewer's progress bars take to fill. */
    public static final int VIEWER_PROGRESS_TICKS = 200;

    private final Byte2ObjectMap<UITexture> slotOverlays = new Byte2ObjectOpenHashMap<>();

    private final R recipeMap;
    private final boolean modifyItemInputs;
    private final boolean modifyItemOutputs;
    private final boolean modifyFluidInputs;
    private final boolean modifyFluidOutputs;

    private final boolean isGenerator;

    private UITexture progressBarTexture = GTGuiTextures.PROGRESS_BAR_ARROW;
    private ProgressWidget.Direction progressBarDirection = ProgressWidget.Direction.RIGHT;
    private @Nullable UITexture specialTexture;
    private int @Nullable [] specialTexturePosition;

    private boolean isJEIVisible = true;

    /**
     * @param recipeMap          the recipemap corresponding to this ui
     * @param modifyItemInputs   if item input amounts can be modified
     * @param modifyItemOutputs  if item output amounts can be modified
     * @param modifyFluidInputs  if fluid input amounts can be modified
     * @param modifyFluidOutputs if fluid output amounts can be modified
     */
    public RecipeMapUI(@NotNull R recipeMap, boolean modifyItemInputs, boolean modifyItemOutputs,
                       boolean modifyFluidInputs, boolean modifyFluidOutputs, boolean isGenerator) {
        this.recipeMap = recipeMap;
        this.modifyItemInputs = modifyItemInputs;
        this.modifyItemOutputs = modifyItemOutputs;
        this.modifyFluidInputs = modifyFluidInputs;
        this.modifyFluidOutputs = modifyFluidOutputs;
        this.isGenerator = isGenerator;
    }

    /**
     * Compute the storage key for slot overlays.
     *
     * @param isOutput if the slot is an output slot
     * @param isFluid  if the slot is a fluid slot
     * @param isLast   if the slot is the last slot of its type
     * @return the key
     */
    @ApiStatus.Internal
    public static byte computeOverlayKey(boolean isOutput, boolean isFluid, boolean isLast) {
        return (byte) ((isOutput ? 2 : 0) + (isFluid ? 1 : 0) + (isLast ? 4 : 0));
    }

    /**
     * Determines the slot grid for an item input amount
     *
     * @param itemInputsCount the item input amount
     * @return [slots to the left, slots downwards]
     */
    @Contract("_ -> new")
    public static int @NotNull [] determineSlotsGrid(int itemInputsCount) {
        int itemSlotsToLeft;
        int itemSlotsToDown;
        double sqrt = Math.sqrt(itemInputsCount);
        // if the number of input has an integer root
        // return it.
        if (sqrt % 1 == 0) {
            itemSlotsToLeft = (int) sqrt;
            itemSlotsToDown = itemSlotsToLeft;
        } else if (itemInputsCount == 3) {
            itemSlotsToLeft = 3;
            itemSlotsToDown = 1;
        } else {
            // if we couldn't fit all into a perfect square,
            // increase the amount of slots to the left
            itemSlotsToLeft = (int) Math.ceil(sqrt);
            itemSlotsToDown = itemSlotsToLeft - 1;
            // if we still can't fit all the slots in a grid,
            // increase the amount of slots on the bottom
            if (itemInputsCount > itemSlotsToLeft * itemSlotsToDown) {
                itemSlotsToDown = itemSlotsToLeft;
            }
        }
        return new int[] { itemSlotsToLeft, itemSlotsToDown };
    }

    /**
     * Lay out the recipe viewer category of this map. Its progress bars fill every {@link #VIEWER_PROGRESS_TICKS}
     * ticks.
     *
     * @param importItems  the item inputs shown
     * @param exportItems  the item outputs shown
     * @param importFluids the fluid inputs shown
     * @param exportFluids the fluid outputs shown
     * @return the layout
     */
    public @NotNull RecipeMapLayout createJeiLayout(int importItems, int exportItems, int importFluids,
                                                    int exportFluids) {
        return createLayout(new TimedProgressSupplier(VIEWER_PROGRESS_TICKS, 20, false), importItems, exportItems,
                importFluids, exportFluids, 0);
    }

    /**
     * Lay out a machine screen of this map: its progress bar, its slots and tanks and the map's image. This does not
     * include the machine's own widgets or the player's inventory.
     *
     * @param progress     the machine's progress, from 0 to 1
     * @param importItems  the machine's item input slots
     * @param exportItems  the machine's item output slots, or 0 when none are shown
     * @param importFluids the machine's input tanks
     * @param exportFluids the machine's output tanks, or 0 when none are shown
     * @param yOffset      how far the slots are moved down
     * @return the layout
     */
    public @NotNull RecipeMapLayout createLayout(@NotNull DoubleSupplier progress, int importItems, int exportItems,
                                                 int importFluids, int exportFluids, int yOffset) {
        RecipeMapLayout layout = new RecipeMapLayout(WIDTH, HEIGHT + yOffset);
        layout.progressBar(78, 23 + yOffset, 20, 20, progressBarTexture, progressBarDirection, progress, recipeMap);
        addInventorySlotGroup(layout, importItems, importFluids, false, yOffset);
        addInventorySlotGroup(layout, exportItems, exportFluids, true, yOffset);
        addSpecialTexture(layout);
        return layout;
    }

    /**
     * @param layout     the layout to add to
     * @param itemCount  the number of item slots
     * @param fluidCount the number of tanks
     * @param isOutputs  if slots should be output slots
     * @param yOffset    the y offset for the gui
     */
    protected void addInventorySlotGroup(@NotNull RecipeMapLayout layout, int itemCount, int fluidCount,
                                         boolean isOutputs, int yOffset) {
        int itemInputsCount = itemCount;
        int fluidInputsCount = fluidCount;
        boolean invertFluids = false;
        if (itemInputsCount == 0) {
            int tmp = itemInputsCount;
            itemInputsCount = fluidInputsCount;
            fluidInputsCount = tmp;
            invertFluids = true;
        }
        int[] inputSlotGrid = determineSlotsGrid(itemInputsCount);
        int itemSlotsToLeft = inputSlotGrid[0];
        int itemSlotsToDown = inputSlotGrid[1];
        int startInputsX = isOutputs ? 106 : 70 - itemSlotsToLeft * 18;
        int startInputsY = 33 - (int) (itemSlotsToDown / 2.0 * 18) + yOffset;
        boolean wasGroup = itemCount + fluidCount == 12;
        if (wasGroup) startInputsY -= 9;
        else if (itemCount >= 6 && fluidCount >= 2 && !isOutputs) startInputsY -= 9;
        for (int i = 0; i < itemSlotsToDown; i++) {
            for (int j = 0; j < itemSlotsToLeft; j++) {
                int slotIndex = i * itemSlotsToLeft + j;
                if (slotIndex >= itemInputsCount) break;
                int x = startInputsX + 18 * j;
                int y = startInputsY + 18 * i;
                addSlot(layout, x, y, slotIndex, itemCount, fluidCount, invertFluids, isOutputs);
            }
        }
        if (wasGroup) startInputsY += 2;
        if (fluidInputsCount > 0 || invertFluids) {
            if (itemSlotsToDown >= fluidInputsCount && itemSlotsToLeft < 3) {
                int startSpecX = isOutputs ? startInputsX + itemSlotsToLeft * 18 : startInputsX - 18;
                for (int i = 0; i < fluidInputsCount; i++) {
                    int y = startInputsY + 18 * i;
                    addSlot(layout, startSpecX, y, i, itemCount, fluidCount, !invertFluids, isOutputs);
                }
            } else {
                int startSpecY = startInputsY + itemSlotsToDown * 18;
                for (int i = 0; i < fluidInputsCount; i++) {
                    int x = isOutputs ? startInputsX + 18 * (i % 3) :
                            startInputsX + itemSlotsToLeft * 18 - 18 - 18 * (i % 3);
                    int y = startSpecY + (i / 3) * 18;
                    addSlot(layout, x, y, i, itemCount, fluidCount, !invertFluids, isOutputs);
                }
            }
        }
    }

    /**
     * Add a slot to a layout
     *
     * @param layout     the layout to add to
     * @param x          the x coordinate of the slot
     * @param y          the y coordinate of the slot
     * @param slotIndex  the slot index of the slot
     * @param itemCount  the number of item slots on its side, input or output
     * @param fluidCount the number of tanks on its side
     * @param isFluid    if the slot is a fluid slot
     * @param isOutputs  if slots should be output slots
     */
    protected void addSlot(@NotNull RecipeMapLayout layout, int x, int y, int slotIndex, int itemCount,
                           int fluidCount, boolean isFluid, boolean isOutputs) {
        boolean isLast = slotIndex == (isFluid ? fluidCount : itemCount) - 1;
        layout.slot(isOutputs, isFluid, slotIndex, x, y, getOverlaysForSlot(isOutputs, isFluid, isLast));
    }

    /**
     * @param isOutput if the slot is an output slot
     * @param isFluid  if the slot is a fluid slot
     * @param isLast   if the slot is the last slot of its type
     * @return the textures drawn under a slot: the slot, then its overlay if one is set
     */
    protected @NotNull UITexture @NotNull [] getOverlaysForSlot(boolean isOutput, boolean isFluid, boolean isLast) {
        UITexture base = isFluid ? GTGuiTextures.FLUID_SLOT : GTGuiTextures.SLOT;
        UITexture overlay = slotOverlays.get(computeOverlayKey(isOutput, isFluid, isLast));
        return overlay == null ? new UITexture[] { base } : new UITexture[] { base, overlay };
    }

    /**
     * @return the height used to determine size of background texture in JEI
     */
    public int getPropertyHeightShift() {
        int maxPropertyCount = 0;
        if (shouldShiftWidgets()) {
            for (Recipe recipe : recipeMap.getRecipeList()) {
                int count = recipe.propertyStorage().size();
                if (count > maxPropertyCount) {
                    maxPropertyCount = count;
                }
            }
        }
        return maxPropertyCount * 10; // GTRecipeWrapper#LINE_HEIGHT
    }

    /**
     * @return widgets should be shifted
     */
    private boolean shouldShiftWidgets() {
        return recipeMap.getMaxInputs() + recipeMap.getMaxOutputs() >= 6 ||
                recipeMap.getMaxFluidInputs() + recipeMap.getMaxFluidOutputs() >= 6;
    }

    /**
     * @return the direction the progress bar fills in
     */
    public @NotNull ProgressWidget.Direction progressBarDirection() {
        return progressBarDirection;
    }

    /**
     * @param direction the new direction the progress bar fills in
     */
    public void setProgressBarDirection(@NotNull ProgressWidget.Direction direction) {
        this.progressBarDirection = direction;
    }

    /**
     * @return the texture of the progress bar: the empty bar above the full one
     */
    public @NotNull UITexture progressBarTexture() {
        return progressBarTexture;
    }

    /**
     * @param progressBarTexture the new progress bar texture: the empty bar above the full one
     */
    public void setProgressBarTexture(@NotNull UITexture progressBarTexture) {
        this.progressBarTexture = progressBarTexture;
    }

    /**
     * @param progressBarTexture the new progress bar texture: the empty bar above the full one
     * @param direction          the new direction the progress bar fills in
     */
    public void setProgressBar(@NotNull UITexture progressBarTexture, @NotNull ProgressWidget.Direction direction) {
        this.progressBarTexture = progressBarTexture;
        this.progressBarDirection = direction;
    }

    /**
     * @param specialTexture the special texture to set
     * @param x              the x coordinate of the texture
     * @param y              the y coordinate of the texture
     * @param width          the width of the texture
     * @param height         the height of the texture
     */
    public void setSpecialTexture(@NotNull UITexture specialTexture, int x, int y, int width, int height) {
        setSpecialTexture(specialTexture, new int[] { x, y, width, height });
    }

    /**
     * @param specialTexture the special texture to set
     * @param position       the position of the texture: [x, y, width, height]
     */
    public void setSpecialTexture(@NotNull UITexture specialTexture, int @NotNull [] position) {
        this.specialTexture = specialTexture;
        this.specialTexturePosition = position;
    }

    /**
     * @return the special texture
     */
    public @Nullable UITexture specialTexture() {
        return this.specialTexture;
    }

    /**
     * @return the special texture's position
     */
    public int @Nullable @UnmodifiableView [] specialTexturePosition() {
        return this.specialTexturePosition;
    }

    /**
     * Add the special texture, if this map has one, to a layout
     *
     * @param layout the layout to add to
     */
    public void addSpecialTexture(@NotNull RecipeMapLayout layout) {
        if (specialTexture != null && specialTexturePosition != null) {
            layout.image(specialTexturePosition[0], specialTexturePosition[1], specialTexturePosition[2],
                    specialTexturePosition[3], specialTexture);
        }
    }

    /**
     * @return if this ui should be visible in JEI
     */
    public boolean isJEIVisible() {
        return isJEIVisible;
    }

    /**
     * @param isJEIVisible if the ui should be visible in JEI
     */
    public void setJEIVisible(boolean isJEIVisible) {
        this.isJEIVisible = isJEIVisible;
    }

    /**
     * @return if item input slot amounts can be modified
     */
    public boolean canModifyItemInputs() {
        return modifyItemInputs;
    }

    /**
     * @return if item output slot amounts can be modified
     */
    public boolean canModifyItemOutputs() {
        return modifyItemOutputs;
    }

    /**
     * @return if fluid input slot amounts can be modified
     */
    public boolean canModifyFluidInputs() {
        return modifyFluidInputs;
    }

    /**
     * @return if fluid output slot amounts can be modified
     */
    public boolean canModifyFluidOutputs() {
        return modifyFluidOutputs;
    }

    /**
     * @return if this UI represents an energy generating recipemap
     */
    public boolean isGenerator() {
        return isGenerator;
    }

    /**
     * @param texture  the texture to set
     * @param isOutput if the slot is an output slot
     */
    public void setItemSlotOverlay(@NotNull UITexture texture, boolean isOutput) {
        this.slotOverlays.put(computeOverlayKey(isOutput, false, false), texture);
        this.slotOverlays.put(computeOverlayKey(isOutput, false, true), texture);
    }

    /**
     * @param texture    the texture to set
     * @param isOutput   if the slot is an output slot
     * @param isLastSlot if the slot is the last slot
     */
    public void setItemSlotOverlay(@NotNull UITexture texture, boolean isOutput, boolean isLastSlot) {
        this.slotOverlays.put(computeOverlayKey(isOutput, false, isLastSlot), texture);
    }

    /**
     * @param texture  the texture to set
     * @param isOutput if the slot is an output slot
     */
    public void setFluidSlotOverlay(@NotNull UITexture texture, boolean isOutput) {
        this.slotOverlays.put(computeOverlayKey(isOutput, true, false), texture);
        this.slotOverlays.put(computeOverlayKey(isOutput, true, true), texture);
    }

    /**
     * @param texture    the texture to set
     * @param isOutput   if the slot is an output slot
     * @param isLastSlot if the slot is the last slot
     */
    public void setFluidSlotOverlay(@NotNull UITexture texture, boolean isOutput, boolean isLastSlot) {
        this.slotOverlays.put(computeOverlayKey(isOutput, true, isLastSlot), texture);
    }

    /**
     * @param key     the key to store the slot's texture with
     * @param texture the texture to store
     */
    @ApiStatus.Internal
    public void setSlotOverlay(byte key, @NotNull UITexture texture) {
        this.slotOverlays.put(key, texture);
    }

    /**
     * @return the UI's recipemap
     */
    public @NotNull R recipeMap() {
        return recipeMap;
    }

    /**
     * Build a machine's layout as a screen of the old GUI system, for the machine screens not yet on ModularUI 2.
     *
     * @param layout       the machine's layout
     * @param importItems  the input item inventory
     * @param exportItems  the output item inventory
     * @param importFluids the input fluid inventory
     * @param exportFluids the output fluid inventory
     * @return the populated builder
     */
    @ApiStatus.Internal
    @Deprecated
    public static @NotNull ModularUI.Builder createLegacyUITemplate(@NotNull RecipeMapLayout layout,
                                                                    @NotNull IItemHandlerModifiable importItems,
                                                                    @NotNull IItemHandlerModifiable exportItems,
                                                                    @NotNull FluidTankList importFluids,
                                                                    @NotNull FluidTankList exportFluids) {
        ModularUI.Builder builder = ModularUI.builder(GuiTextures.BACKGROUND, layout.getWidth(), layout.getHeight());
        for (RecipeLayoutElement element : layout.getElements()) {
            if (element instanceof RecipeLayoutSlot slot) {
                TextureArea[] background = slot.background().stream()
                        .map(RecipeMapUI::legacyTexture)
                        .toArray(TextureArea[]::new);
                if (slot.isFluid()) {
                    FluidTankList tanks = slot.isOutput() ? exportFluids : importFluids;
                    builder.widget(new TankWidget(tanks.getTankAt(slot.index()), slot.x(), slot.y(), 18, 18)
                            .setAlwaysShowFull(true)
                            .setBackgroundTexture(background)
                            .setContainerClicking(true, !slot.isOutput()));
                } else {
                    builder.widget(new SlotWidget(slot.isOutput() ? exportItems : importItems, slot.index(),
                            slot.x(), slot.y(), true, !slot.isOutput()).setBackgroundTexture(background));
                }
            } else if (element instanceof RecipeLayoutProgress progress) {
                builder.widget(new RecipeProgressWidget(progress.progress(), progress.x(), progress.y(),
                        progress.width(), progress.height(), legacyTexture(progress.texture()),
                        legacyMoveType(progress.direction()), progress.recipeMap()));
            } else if (element instanceof RecipeLayoutImage image) {
                builder.image(image.x(), image.y(), image.width(), image.height(), legacyTexture(image.texture()));
            }
        }
        return builder;
    }

    private static @NotNull TextureArea legacyTexture(@NotNull UITexture texture) {
        return new TextureArea(texture.location, texture.u0, texture.v0, texture.u1 - texture.u0,
                texture.v1 - texture.v0);
    }

    private static @NotNull MoveType legacyMoveType(@NotNull ProgressWidget.Direction direction) {
        return switch (direction) {
            case LEFT -> MoveType.HORIZONTAL_BACKWARDS;
            case RIGHT -> MoveType.HORIZONTAL;
            case UP -> MoveType.VERTICAL;
            case DOWN -> MoveType.VERTICAL_DOWNWARDS;
            case CIRCULAR_CW -> MoveType.CIRCULAR;
        };
    }
}
