package gregtech.client.utils.image;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A monitor picture's address comes from another player, so a download never reaches the viewer's own network. */
public class ImageUtilsTest {

    private static boolean isPublic(String literal) throws UnknownHostException {
        return ImageUtils.isPublicAddress(InetAddress.getByName(literal));
    }

    @Test
    public void Test_Local_And_Private_Addresses_Are_Refused() throws UnknownHostException {
        for (String address : new String[] { "127.0.0.1", "127.8.9.10", "0.0.0.0", "0.1.2.3", "10.0.0.1",
                "172.16.4.4", "172.31.255.255", "192.168.1.1", "169.254.169.254", "100.64.0.1", "100.127.255.255",
                "224.0.0.1", "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "::ffff:192.168.0.1" }) {
            assertFalse(isPublic(address), address);
        }
    }

    @Test
    public void Test_Public_Addresses_Are_Allowed() throws UnknownHostException {
        for (String address : new String[] { "93.184.216.34", "8.8.8.8", "100.128.0.1", "172.32.0.1",
                "2606:4700::1111" }) {
            assertTrue(isPublic(address), address);
        }
    }
}
