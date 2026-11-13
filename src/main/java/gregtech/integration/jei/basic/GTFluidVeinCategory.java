package gregtech.integration.jei.basic;

import gregtech.api.GTValues;
import gregtech.api.gui.GuiTextures;
import gregtech.api.util.GTStringUtils;
import gregtech.api.worldgen.config.WorldGenRegistry;
import gregtech.integration.jei.utils.JEIResourceDepositCategoryUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class GTFluidVeinCategory extends BasicRecipeCategory<GTFluidVeinInfo, GTFluidVeinInfo> {

    public static final String UID = String.format("%s.fluid_spawn_location", GTValues.MODID);

    private static final int SLOT_CENTER = 79;
    private static final int TEXT_START_X = 5;
    private static final int START_POS_Y = 40;

    protected final IDrawable slot;

    public GTFluidVeinCategory(IGuiHelper guiHelper) {
        super("fluid_spawn_location",
                "fluid.spawnlocation.name",
                guiHelper.createBlankDrawable(176, 166),
                guiHelper);

        this.slot = guiHelper.drawableBuilder(GuiTextures.SLOT.imageLocation, 0, 0, 18, 18).setTextureSize(18, 18)
                .build();
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayout recipeLayout, GTFluidVeinInfo gtFluidVeinInfo,
                          @NotNull IIngredients ingredients) {
        IGuiFluidStackGroup fluidStackGroup = recipeLayout.getFluidStacks();

        fluidStackGroup.init(0, true, SLOT_CENTER, 19, 16, 16, 1, false, null);

        fluidStackGroup.addTooltipCallback(gtFluidVeinInfo::addTooltip);
        fluidStackGroup.set(ingredients);
        // JEI lays out every recipe of a page before drawing any: the text is drawn per recipe from its wrapper
        gtFluidVeinInfo.setCategory(this);
    }

    @NotNull
    @Override
    public IRecipeWrapper getRecipeWrapper(@NotNull GTFluidVeinInfo gtFluidVeinInfo) {
        return gtFluidVeinInfo;
    }

    /**
     * Draw the text of one recipe and record its line widths for the tooltips; called from
     * {@link GTFluidVeinInfo#drawInfo}.
     */
    void drawRecipeExtras(@NotNull Minecraft minecraft, @NotNull GTFluidVeinInfo info) {
        String veinName = info.getName();
        int weight = info.getWeight();
        int[] yields = info.getYields();
        int depletionAmount = info.getDepletionAmount();
        int depletionChance = info.getDepletionChance();
        int depletedYield = info.getDepletedYield();
        int[] dimensions = info.getDimensions();
        int[] widths = info.textWidths;
        GTStringUtils.drawCenteredStringWithCutoff(veinName, minecraft.fontRenderer, 176);

        this.slot.draw(minecraft, SLOT_CENTER - 1, 18);

        // Vein Weight information
        String veinWeight = I18n.format("gregtech.jei.fluid.vein_weight", weight);
        widths[0] = minecraft.fontRenderer.getStringWidth(veinWeight);
        minecraft.fontRenderer.drawString(veinWeight, TEXT_START_X, START_POS_Y, 0x111111);

        // Vein Minimum Yield information
        String veinMinYield = I18n.format("gregtech.jei.fluid.min_yield", yields[0]);
        widths[1] = minecraft.fontRenderer.getStringWidth(veinMinYield);
        minecraft.fontRenderer.drawString(veinMinYield, TEXT_START_X, START_POS_Y + FONT_HEIGHT + 1, 0x111111);

        // Vein Maximum Yield information
        String veinMaxYield = I18n.format("gregtech.jei.fluid.max_yield", yields[1]);
        widths[2] = minecraft.fontRenderer.getStringWidth(veinMaxYield);
        minecraft.fontRenderer.drawString(veinMaxYield, TEXT_START_X, START_POS_Y + 2 * FONT_HEIGHT + 1, 0x111111);

        // Vein Depletion Chance information
        String veinDepletionChance = I18n.format("gregtech.jei.fluid.depletion_chance", depletionChance);
        widths[3] = minecraft.fontRenderer.getStringWidth(veinDepletionChance);
        minecraft.fontRenderer.drawString(veinDepletionChance, TEXT_START_X, START_POS_Y + 3 * FONT_HEIGHT + 1,
                0x111111);

        // Vein Depletion Amount information
        String veinDepletionAmount = I18n.format("gregtech.jei.fluid.depletion_amount", depletionAmount);
        widths[4] = minecraft.fontRenderer.getStringWidth(veinDepletionAmount);
        minecraft.fontRenderer.drawString(veinDepletionAmount, TEXT_START_X, START_POS_Y + 4 * FONT_HEIGHT + 1,
                0x111111);

        // Vein Depleted Yield information
        String veinDepletedYield = I18n.format("gregtech.jei.fluid.depleted_rate", depletedYield);
        widths[5] = minecraft.fontRenderer.getStringWidth(veinDepletedYield);
        minecraft.fontRenderer.drawString(veinDepletedYield, TEXT_START_X, START_POS_Y + 5 * FONT_HEIGHT + 1, 0x111111);

        // Vein Dimensions information
        String veinDimension = I18n.format("gregtech.jei.fluid.dimension") + " ";
        int dimensionLength = minecraft.fontRenderer.getStringWidth(veinDimension);
        minecraft.fontRenderer.drawString(veinDimension, TEXT_START_X, START_POS_Y + 6 * FONT_HEIGHT + 1, 0x111111);

        JEIResourceDepositCategoryUtils.drawMultiLineCommaSeparatedDimensionList(WorldGenRegistry.getNamedDimensions(),
                dimensions,
                minecraft.fontRenderer,
                TEXT_START_X,
                START_POS_Y + 6 * FONT_HEIGHT + 1,
                TEXT_START_X + dimensionLength);
    }

    /**
     * @return the hover text for one recipe's lines; called from {@link GTFluidVeinInfo#getTooltipStrings}
     */
    @NotNull
    List<String> getRecipeTooltip(@NotNull GTFluidVeinInfo info, int mouseX, int mouseY) {
        int[] widths = info.textWidths;
        int weightLength = widths[0], minYieldLength = widths[1], maxYieldLength = widths[2];
        int depletionChanceLength = widths[3], depletionAmountLength = widths[4], depletedYieldLength = widths[5];
        if (isPointWithinRange(TEXT_START_X, START_POS_Y, weightLength, FONT_HEIGHT, mouseX, mouseY)) {
            return Collections.singletonList(I18n.format("gregtech.jei.fluid.weight_hover"));
        } else if (isPointWithinRange(TEXT_START_X, START_POS_Y + FONT_HEIGHT + 1, minYieldLength, FONT_HEIGHT + 1,
                mouseX, mouseY)) {
                    return Collections.singletonList(I18n.format("gregtech.jei.fluid.min_hover"));
                } else
            if (isPointWithinRange(TEXT_START_X, START_POS_Y + 2 * FONT_HEIGHT + 1, maxYieldLength, FONT_HEIGHT + 1,
                    mouseX, mouseY)) {
                        return Collections.singletonList(I18n.format("gregtech.jei.fluid.max_hover"));
                    } else
                if (isPointWithinRange(TEXT_START_X, START_POS_Y + 3 * FONT_HEIGHT + 1, depletionChanceLength,
                        FONT_HEIGHT + 1, mouseX, mouseY)) {
                            return Collections.singletonList(I18n.format("gregtech.jei.fluid.dep_chance_hover"));
                        } else
                    if (isPointWithinRange(TEXT_START_X, START_POS_Y + 4 * FONT_HEIGHT + 1, depletionAmountLength,
                            FONT_HEIGHT + 1, mouseX, mouseY)) {
                                return Collections.singletonList(I18n.format("gregtech.jei.fluid.dep_amount_hover"));
                            } else
                        if (isPointWithinRange(TEXT_START_X, START_POS_Y + 5 * FONT_HEIGHT + 1, depletedYieldLength,
                                FONT_HEIGHT + 1, mouseX, mouseY)) {
                                    return Collections.singletonList(I18n.format("gregtech.jei.fluid.dep_yield_hover"));
                                }

        return Collections.emptyList();
    }

    /**
     * Checks if an (X,Y) point is within a defined box range
     *
     * @param initialX The initial X point of the box
     * @param initialY The initial Y point of the box
     * @param width    The width of the box
     * @param height   The height of the box
     * @param pointX   The X value of the point to check
     * @param pointY   The Y value of the point to check
     * @return True if the provided (X,Y) point is within the described box, else false
     */
    private static boolean isPointWithinRange(int initialX, int initialY, int width, int height, int pointX,
                                              int pointY) {
        return initialX <= pointX && pointX <= initialX + width && initialY <= pointY && pointY <= initialY + height;
    }
}
