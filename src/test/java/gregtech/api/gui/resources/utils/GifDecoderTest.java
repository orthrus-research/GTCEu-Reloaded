package gregtech.api.gui.resources.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GifDecoderTest {

    private static final int[] HEADER = { 'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, 0x80, 0, 0 };
    private static final int[] COLOR_TABLE = { 0, 0, 0, 0xFF, 0xFF, 0xFF };
    // a graphic control extension: transparency on, 10/100 s delay, transparent index 0
    private static final int[] CONTROL = { 0x21, 0xF9, 4, 0x01, 10, 0, 0, 0 };
    // a 1x1 image with the standard one-pixel LZW stream
    private static final int[] IMAGE = { 0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 0x01, 0 };

    @Test
    void aFrameWithoutControlDataDoesNotInheritTheLastOne() {
        GifDecoder decoder = new GifDecoder();
        int status = decoder.read(stream(HEADER, COLOR_TABLE, CONTROL, IMAGE, IMAGE, new int[] { 0x3B }));

        assertEquals(GifDecoder.STATUS_OK, status);
        assertEquals(2, decoder.getFrameCount());
        assertEquals(100, decoder.getDelay(0));
        assertEquals(0, decoder.getDelay(1));
    }

    @Test
    void aTruncatedColorTableIsAFormatError() {
        GifDecoder decoder = new GifDecoder();
        int status = decoder.read(stream(HEADER, new int[] { 0, 0 }));

        assertEquals(GifDecoder.STATUS_FORMAT_ERROR, status);
    }

    private static ByteArrayInputStream stream(int[]... parts) {
        int length = 0;
        for (int[] part : parts) length += part.length;
        byte[] bytes = new byte[length];
        int i = 0;
        for (int[] part : parts) {
            for (int b : part) bytes[i++] = (byte) b;
        }
        return new ByteArrayInputStream(bytes);
    }
}
