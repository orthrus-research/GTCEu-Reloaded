package gregtech.api.gui.resources.utils;

import gregtech.GregTechMod;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

public class ImageUtils {

    public static final Logger LOGGER = LogManager.getLogger(GregTechMod.class);

    /**
     * Whether an address is on the public internet: not this machine, nor a private, link-local, carrier-grade NAT,
     * unique local or multicast address.
     */
    public static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // 0.0.0.0/8 and the shared 100.64.0.0/10
            return first != 0 && !(first == 100 && (second & 0xC0) == 64);
        }
        if (address instanceof Inet6Address) {
            // unique local fc00::/7, and IPv4 addresses mapped into IPv6
            if ((bytes[0] & 0xFE) == 0xFC) return false;
            boolean mapped = bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF;
            for (int i = 0; i < 10 && mapped; i++) mapped = bytes[i] == 0;
            if (mapped) {
                try {
                    return isPublicAddress(InetAddress.getByAddress(new byte[] { bytes[12], bytes[13], bytes[14],
                            bytes[15] }));
                } catch (IOException e) {
                    return false;
                }
            }
        }
        return true;
    }

    public static String readType(byte[] input) throws IOException {
        InputStream in = null;
        try {
            in = new ByteArrayInputStream(input);
            return readType(in);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    public static String readType(InputStream input) throws IOException {
        ImageInputStream stream = ImageIO.createImageInputStream(input);
        Iterator<ImageReader> iter = ImageIO.getImageReaders(stream);
        if (!iter.hasNext()) {
            return "";
        }
        ImageReader reader = iter.next();

        if (reader.getFormatName().equalsIgnoreCase("gif"))
            return "gif";

        ImageReadParam param = reader.getDefaultReadParam();
        reader.setInput(stream, true, true);
        try {
            reader.read(0, param);
        } catch (IOException e) {
            LOGGER.error("Failed to parse input format", e);
        } finally {
            reader.dispose();
            IOUtils.closeQuietly(stream);
        }
        if (!(input instanceof FileInputStream)) {
            input.reset();
        }
        return reader.getFormatName();
    }
}
