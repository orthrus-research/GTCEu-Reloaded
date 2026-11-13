package gregtech.integration.jei.basic;

import gregtech.api.util.FileUtility;
import gregtech.api.worldgen.config.BedrockFluidDepositDefinition;
import gregtech.integration.jei.utils.JEIResourceDepositCategoryUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class GTFluidVeinInfo implements IRecipeWrapper {

    private final BedrockFluidDepositDefinition definition;
    private String name;
    private final String description;
    private final int weight;
    private final int[] yields; // the [minimum, maximum) yields
    private final int depletionAmount; // amount of fluid the vein gets drained by
    private final int depletionChance; // the chance [0, 100] that the vein will deplete by 1
    private final int depletedYield; // yield after the vein is depleted
    private final FluidStack fluid;
    private final Function<Biome, Integer> biomeFunction;

    private final List<List<FluidStack>> fluidList = new ArrayList<>();
    private final List<List<ItemStack>> bucketList = new ArrayList<>();

    public GTFluidVeinInfo(BedrockFluidDepositDefinition definition) {
        this.definition = definition;

        // Get the Name and trim unneeded information
        this.name = definition.getAssignedName();
        if (this.name == null) {
            this.name = FileUtility.trimFileName(definition.getDepositName());
        }

        this.description = definition.getDescription();

        this.weight = definition.getWeight();

        this.yields = definition.getYields();

        this.depletionAmount = definition.getDepletionAmount();

        this.depletionChance = definition.getDepletionChance();

        this.depletedYield = definition.getDepletedYield();

        this.fluid = new FluidStack(definition.getStoredFluid(), 1);

        List<FluidStack> fluidList2 = new ArrayList<>();
        fluidList2.add(fluid);
        fluidList.add(fluidList2);

        // built once: JEI asks for the ingredients on every layout and lookup
        ItemStack bucket = FluidUtil.getFilledBucket(fluid);
        if (!bucket.isEmpty()) {
            bucketList.add(Collections.singletonList(bucket));
        }

        this.biomeFunction = definition.getBiomeWeightModifier();
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputLists(VanillaTypes.FLUID, fluidList);
        ingredients.setOutputLists(VanillaTypes.FLUID, fluidList);

        if (!bucketList.isEmpty()) {
            ingredients.setInputLists(VanillaTypes.ITEM, bucketList);
            ingredients.setOutputLists(VanillaTypes.ITEM, bucketList);
        }
    }

    public void addTooltip(int slotIndex, boolean input, Object ingredient, List<String> tooltip) {
        if (description != null) {
            tooltip.add(description);
        }
        tooltip.addAll(JEIResourceDepositCategoryUtils.createSpawnPageBiomeTooltip(biomeFunction, weight));
    }

    public BedrockFluidDepositDefinition getDefinition() {
        return definition;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getWeight() {
        return weight;
    }

    public int[] getYields() {
        return yields;
    }

    public int getDepletionAmount() {
        return depletionAmount;
    }

    public int getDepletionChance() {
        return depletionChance;
    }

    public int getDepletedYield() {
        return depletedYield;
    }

    public FluidStack getFluid() {
        return fluid;
    }

    // per-recipe drawing; the category is shared by every recipe on a page
    private @Nullable GTFluidVeinCategory category;
    private int @Nullable [] dimensions;
    /** Widths of the six hoverable lines, as last drawn for this recipe. */
    final int[] textWidths = new int[6];

    void setCategory(@NotNull GTFluidVeinCategory category) {
        this.category = category;
    }

    int @NotNull [] getDimensions() {
        if (dimensions == null) {
            dimensions = JEIResourceDepositCategoryUtils.getAllRegisteredDimensions(
                    getDefinition().getDimensionFilter());
        }
        return dimensions;
    }

    @Override
    public void drawInfo(@NotNull Minecraft minecraft, int recipeWidth, int recipeHeight, int mouseX, int mouseY) {
        if (category != null) category.drawRecipeExtras(minecraft, this);
    }

    @NotNull
    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        return category == null ? Collections.emptyList() : category.getRecipeTooltip(this, mouseX, mouseY);
    }
}
