package gregtech.api.gui.widgets;

import gregtech.api.gui.resources.TextureArea;
import gregtech.api.recipes.RecipeMap;

import net.minecraft.util.text.TextComponentTranslation;

import java.util.function.DoubleSupplier;

public class RecipeProgressWidget extends ProgressWidget {

    private final RecipeMap<?> recipeMap;

    public RecipeProgressWidget(DoubleSupplier progressSupplier, int x, int y, int width, int height,
                                RecipeMap<?> recipeMap) {
        super(progressSupplier, x, y, width, height);
        this.recipeMap = recipeMap;
        setHoverTextConsumer(
                list -> list.add(new TextComponentTranslation("gui.widget.recipeProgressWidget.default_tooltip")));
    }

    public RecipeProgressWidget(DoubleSupplier progressSupplier, int x, int y, int width, int height,
                                TextureArea fullImage, MoveType moveType, RecipeMap<?> recipeMap) {
        super(progressSupplier, x, y, width, height, fullImage, moveType);
        this.recipeMap = recipeMap;
        setHoverTextConsumer(
                list -> list.add(new TextComponentTranslation("gui.widget.recipeProgressWidget.default_tooltip")));
    }

    public RecipeProgressWidget(int ticksPerCycle, int x, int y, int width, int height, TextureArea fullImage,
                                MoveType moveType, RecipeMap<?> recipeMap) {
        super(ticksPerCycle, x, y, width, height, fullImage, moveType);
        this.recipeMap = recipeMap;
        setHoverTextConsumer(
                list -> list.add(new TextComponentTranslation("gui.widget.recipeProgressWidget.default_tooltip")));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        return isMouseOverElement(mouseX, mouseY) &&
                gregtech.api.mui.widget.RecipeProgressWidget.showRecipes(recipeMap);
    }
}
