package gregtech.integration.jei;

import gregtech.Bootstrap;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.common.items.MetaItems;

import net.minecraft.item.Item;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.ISubtypeRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeiSubtypeRegistrationTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    @Test
    void heiCallbackRegistersEveryNativeMetaItemInterpreter() {
        Map<Item, ISubtypeRegistry.ISubtypeInterpreter> interpreters = new IdentityHashMap<>();
        ISubtypeRegistry registry = (ISubtypeRegistry) Proxy.newProxyInstance(
                ISubtypeRegistry.class.getClassLoader(), new Class<?>[] { ISubtypeRegistry.class },
                (proxy, method, arguments) -> {
                    assertEquals("registerSubtypeInterpreter", method.getName());
                    interpreters.put((Item) arguments[0],
                            (ISubtypeRegistry.ISubtypeInterpreter) arguments[1]);
                    return null;
                });
        IModPlugin plugin = new JustEnoughItemsModule();
        plugin.registerSubtypes(registry);
        assertFalse(MetaItems.ITEMS.isEmpty());
        for (MetaItem<?> item : MetaItems.ITEMS) {
            assertNotNull(interpreters.get(item));
        }
    }
}
