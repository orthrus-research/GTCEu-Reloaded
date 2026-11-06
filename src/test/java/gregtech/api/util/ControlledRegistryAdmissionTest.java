package gregtech.api.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlledRegistryAdmissionTest {

    @Test
    void occupiedIdDoesNotPublishTheRejectedName() {
        var registry = new GTControlledRegistry<String, Object>(8);
        var accepted = new Object();
        var rejected = new Object();
        registry.register(2, "accepted", accepted);
        assertThrows(IllegalArgumentException.class, () -> registry.register(2, "rejected", rejected));
        assertSame(accepted, registry.getObjectById(2));
        assertSame(accepted, registry.getObject("accepted"));
        assertNull(registry.getObject("rejected"));
        assertNull(registry.getNameForObject(rejected));
        assertEquals(-1, registry.getIDForObject(rejected));
        assertEquals(1, registry.getKeys().size());
    }

    @Test
    void occupiedIdDoesNotReplaceAnExistingName() {
        var registry = new GTControlledRegistry<String, Object>(8);
        var first = new Object();
        var second = new Object();
        registry.register(1, "first", first);
        registry.register(2, "second", second);
        assertThrows(IllegalArgumentException.class, () -> registry.register(1, "second", new Object()));
        assertSame(first, registry.getObject("first"));
        assertSame(second, registry.getObject("second"));
        assertSame(second, registry.getObjectById(2));
        assertEquals("second", registry.getNameForObject(second));
    }
}
