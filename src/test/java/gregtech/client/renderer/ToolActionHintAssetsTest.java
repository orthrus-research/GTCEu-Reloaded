package gregtech.client.renderer;

import gregtech.Bootstrap;
import gregtech.api.items.toolitem.ToolAction;

import com.cleanroommc.modularui.drawable.UITexture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

public class ToolActionHintAssetsTest {

    @BeforeAll
    public static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    public void everySelectedActionAndMuteOverlayHasVisiblePixelsInItsActualRegion() throws IOException {
        for (ToolAction action : ToolAction.values()) {
            assertVisible(ToolActionHintRenderer.textureFor(action), action.name());
        }
        assertVisible(ToolActionHintRenderer.CROSS, "MUFFLE overlay");
    }

    private static void assertVisible(UITexture texture, String action) throws IOException {
        String path = "/assets/" + texture.location.getNamespace() + "/" + texture.location.getPath();
        try (InputStream input = ToolActionHintAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(input, action + " missing " + path);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, action + " unreadable " + path);
            int x0 = Math.round(texture.u0 * image.getWidth());
            int y0 = Math.round(texture.v0 * image.getHeight());
            int x1 = Math.round(texture.u1 * image.getWidth());
            int y1 = Math.round(texture.v1 * image.getHeight());
            assertTrue(x0 >= 0 && y0 >= 0 && x1 <= image.getWidth() && y1 <= image.getHeight() && x0 < x1 && y0 < y1,
                    action + " invalid subarea " + path);
            int visible = 0;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    if ((image.getRGB(x, y) >>> 24) >= 128) visible++;
                }
            }
            assertTrue(visible >= 16, action + " needs visible artwork in its selected subarea: " + path);
        }
    }
}
