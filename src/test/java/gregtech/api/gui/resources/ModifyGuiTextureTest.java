package gregtech.api.gui.resources;

import gregtech.Bootstrap;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ModifyGuiTextureTest {

    @BeforeAll
    public static void prepare() {
        Bootstrap.perform();
    }

    private static IGuiTexture load(JsonObject config) {
        ModifyGuiTexture texture = new ModifyGuiTexture(null);
        texture.loadConfig(config);
        return texture.getTexture();
    }

    @Test
    public void textEntryLoadsAsText() {
        JsonObject config = new JsonObject();
        config.addProperty("type", "text");
        config.addProperty("text", "label");
        config.addProperty("color", 0xFF00FF00);

        IGuiTexture texture = load(config);
        assertInstanceOf(TextTexture.class, texture);
        assertEquals(0xFF00FF00, ((TextTexture) texture).color);
    }

    @Test
    public void colorEntryLoadsAsColor() {
        JsonObject config = new JsonObject();
        config.addProperty("type", "color");
        config.addProperty("color", 0xFF123456);

        IGuiTexture texture = load(config);
        assertInstanceOf(ColorRectTexture.class, texture);
        assertEquals(0xFF123456, ((ColorRectTexture) texture).color);
    }

    @Test
    public void resourceEntryLoadsAsResource() {
        JsonObject config = new JsonObject();
        config.addProperty("type", "resource");
        config.addProperty("resource", "gregtech:textures/gui/base/background.png");

        assertInstanceOf(TextureArea.class, load(config));
    }
}
