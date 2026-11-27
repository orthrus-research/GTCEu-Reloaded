package gregtech.api.util;

import gregtech.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlledRegistryAdmissionTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void frozenRegistryRejectsRegistration() {
        var registry = new GTControlledRegistry<String, Object>(8);
        assertTrue(registry.isFrozen());
        assertThrows(IllegalStateException.class, () -> registry.register(1, "early", new Object()));
        assertNull(registry.getObject("early"));
        assertNull(registry.getObjectById(1));

        registry.unfreeze();
        var accepted = new Object();
        registry.register(1, "accepted", accepted);
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> registry.register(2, "late", new Object()));
        assertSame(accepted, registry.getObjectById(1));
        assertNull(registry.getObject("late"));
        assertEquals(1, registry.getKeys().size());
    }

    @Test
    void registryCreatedOpenAcceptsRegistrationUntilFrozen() {
        var registry = new GTControlledRegistry<String, Object>(8, false);
        assertFalse(registry.isFrozen());
        registry.register(1, "first", new Object());
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> registry.register(2, "second", new Object()));
    }

    @Test
    void occupiedIdDoesNotPublishTheRejectedName() {
        var registry = new GTControlledRegistry<String, Object>(8, false);
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
        var registry = new GTControlledRegistry<String, Object>(8, false);
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
