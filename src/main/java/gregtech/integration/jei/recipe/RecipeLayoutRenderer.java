package gregtech.integration.jei.recipe;

import gregtech.api.recipes.ui.RecipeLayoutElement;
import gregtech.api.recipes.ui.RecipeLayoutImage;
import gregtech.api.recipes.ui.RecipeLayoutProgress;
import gregtech.api.recipes.ui.RecipeLayoutSlot;
import gregtech.api.recipes.ui.RecipeMapLayout;

import net.minecraft.client.renderer.GlStateManager;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.UITexture;
import org.jetbrains.annotations.NotNull;

/**
 * Draws a recipe map's layout behind a recipe viewer's ingredients: slot and tank backgrounds, progress bars filling
 * as their progress goes and images, untinted, as ModularUI draws them on a machine screen. The viewer draws the
 * ingredients itself.
 */
final class RecipeLayoutRenderer {

    private RecipeLayoutRenderer() {}

    /**
     * @param layout the layout to draw
     * @param smooth whether progress bars fill smoothly rather than a whole pixel at a time
     */
    static void draw(@NotNull RecipeMapLayout layout, boolean smooth) {
        GlStateManager.color(1, 1, 1, 1);
        for (RecipeLayoutElement element : layout.getElements()) {
            if (element instanceof RecipeLayoutSlot slot) {
                for (UITexture texture : slot.background()) {
                    texture.draw(slot.x(), slot.y(), slot.width(), slot.height());
                }
            } else if (element instanceof RecipeLayoutProgress progress) {
                drawProgress(progress, smooth);
            } else if (element instanceof RecipeLayoutImage image) {
                image.texture().draw(image.x(), image.y(), image.width(), image.height());
            }
        }
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void drawProgress(@NotNull RecipeLayoutProgress bar, boolean smooth) {
        UITexture texture = bar.texture();
        float x = bar.x();
        float y = bar.y();
        float width = bar.width();
        float height = bar.height();
        drawSubArea(texture, x, y, width, height, 0, 0, 1, 0.5f);

        float progress = (float) Math.max(0, Math.min(1, bar.progress().getAsDouble()));
        if (progress <= 0) return;
        switch (bar.direction()) {
            case RIGHT -> {
                float drawn = step(width * progress, smooth);
                drawFull(texture, x, y, drawn, height, 0, 0, drawn / width, 1);
            }
            case LEFT -> {
                float drawn = step(width * progress, smooth);
                drawFull(texture, x + width - drawn, y, drawn, height, 1 - drawn / width, 0, 1, 1);
            }
            case DOWN -> {
                float drawn = step(height * progress, smooth);
                drawFull(texture, x, y, width, drawn, 0, 0, 1, drawn / height);
            }
            case UP -> {
                float drawn = step(height * progress, smooth);
                drawFull(texture, x, y + height - drawn, width, drawn, 0, 1 - drawn / height, 1, 1);
            }
            case CIRCULAR_CW -> drawCircular(texture, x, y, width, height, progress, smooth);
        }
    }

    /** Fills the four quarters in turn: the lower left upwards, the upper left rightwards, then on clockwise. */
    private static void drawCircular(@NotNull UITexture texture, float x, float y, float width, float height,
                                     float progress, boolean smooth) {
        float halfWidth = width / 2;
        float halfHeight = height / 2;
        float lowerLeft = step(quarter(progress, 0) * halfHeight, smooth);
        float upperLeft = step(quarter(progress, 1) * halfWidth, smooth);
        float upperRight = step(quarter(progress, 2) * halfHeight, smooth);
        float lowerRight = step(quarter(progress, 3) * halfWidth, smooth);
        drawFull(texture, x, y + height - lowerLeft, halfWidth, lowerLeft, 0, 1 - lowerLeft / height, 0.5f, 1);
        drawFull(texture, x, y, upperLeft, halfHeight, 0, 0, upperLeft / width, 0.5f);
        drawFull(texture, x + halfWidth, y, halfWidth, upperRight, 0.5f, 0, 1, upperRight / height);
        drawFull(texture, x + width - lowerRight, y + halfHeight, lowerRight, halfHeight, 1 - lowerRight / width,
                0.5f, 1, 1);
    }

    private static float quarter(float progress, int quarter) {
        return Math.max(0, Math.min(1, (progress - quarter * 0.25f) / 0.25f));
    }

    private static float step(float length, boolean smooth) {
        return smooth ? length : (float) Math.floor(length);
    }

    /** Draws part of the full bar, the texture's lower half, with coordinates relative to that half. */
    private static void drawFull(@NotNull UITexture texture, float x, float y, float width, float height, float u0,
                                 float v0, float u1, float v1) {
        if (width <= 0 || height <= 0) return;
        drawSubArea(texture, x, y, width, height, u0, 0.5f + v0 / 2, u1, 0.5f + v1 / 2);
    }

    private static void drawSubArea(@NotNull UITexture texture, float x, float y, float width, float height,
                                    float u0, float v0, float u1, float v1) {
        float textureWidth = texture.u1 - texture.u0;
        float textureHeight = texture.v1 - texture.v0;
        GuiDraw.drawTexture(texture.location, x, y, x + width, y + height, texture.u0 + u0 * textureWidth,
                texture.v0 + v0 * textureHeight, texture.u0 + u1 * textureWidth, texture.v0 + v1 * textureHeight,
                texture.nonOpaque);
    }
}
