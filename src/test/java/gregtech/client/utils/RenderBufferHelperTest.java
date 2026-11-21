package gregtech.client.utils;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/** The fusion reactor's ring as quads is the ring of the quad strip it was drawn as, quad for quad. */
class RenderBufferHelperTest {

    private static final int GL_QUADS = 7;
    private static final int GL_QUAD_STRIP = 8;
    private static final int SIDES = 10;
    private static final int SEGMENTS = 20;

    @Test
    void ringQuadsAreTheStripsQuads() {
        for (EnumFacing.Axis axis : EnumFacing.Axis.values()) {
            BufferBuilder strip = ring(GL_QUAD_STRIP, axis);
            BufferBuilder quads = ring(GL_QUADS, axis);
            int stride = DefaultVertexFormats.POSITION_COLOR.getSize();
            assertEquals(SEGMENTS * (SIDES + 1) * 2, strip.getVertexCount());
            assertEquals(SEGMENTS * SIDES * 4, quads.getVertexCount());

            ByteBuffer stripData = strip.getByteBuffer();
            ByteBuffer quadData = quads.getByteBuffer();
            int quad = 0;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                int first = segment * (SIDES + 1) * 2;
                for (int side = 0; side < SIDES; side++) {
                    // A strip's quad joins the pair of vertices at one side and the pair at the next, in this order
                    int pair = first + side * 2;
                    int[] stripVertices = { pair, pair + 1, pair + 3, pair + 2 };
                    String where = axis + " quad " + segment + ":" + side;
                    for (int corner = 0; corner < 4; corner++) {
                        int expected = stripVertices[corner] * stride;
                        int actual = (quad * 4 + corner) * stride;
                        for (int offset = 0; offset < stride; offset++) {
                            assertEquals(stripData.get(expected + offset), quadData.get(actual + offset), where);
                        }
                    }
                    quad++;
                }
            }
        }
    }

    private static BufferBuilder ring(int mode, EnumFacing.Axis axis) {
        BufferBuilder buffer = new BufferBuilder(4096);
        buffer.begin(mode, DefaultVertexFormats.POSITION_COLOR);
        if (mode == GL_QUAD_STRIP) {
            RenderBufferHelper.renderRing(buffer, 10.5, 64.5, -3.5, 6, 0.2, SIDES, SEGMENTS, 0.9f, 0.4f, 0.1f, 1, axis);
        } else {
            RenderBufferHelper.renderRingQuads(buffer, 10.5, 64.5, -3.5, 6, 0.2, SIDES, SEGMENTS, 0.9f, 0.4f, 0.1f, 1,
                    axis);
        }
        buffer.finishDrawing();
        return buffer;
    }
}
