package gregtech.common.worldgen;

import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryEmpty;
import net.minecraft.world.storage.loot.conditions.LootCondition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LootTableHelperTest {

    @Test
    void registeredTypesAreReadAndOthersAreLeftToVanilla() {
        LootTableHelper.registerLootEntry("gregtech:test_entry",
                (object, context, weight, quality, conditions) -> {
                    assertEquals(0, conditions.length);
                    return new LootEntryEmpty(weight, quality, new LootCondition[0], "test");
                });

        JsonObject custom = new JsonParser()
                .parse("{\"type\":\"gregtech:test_entry\",\"weight\":7,\"quality\":2}").getAsJsonObject();
        LootEntry entry = LootTableHelper.deserializeCustomEntry(custom, null);
        assertNotNull(entry);
        assertEquals(7, entry.getEffectiveWeight(0));

        assertNull(LootTableHelper.deserializeCustomEntry(
                new JsonParser().parse("{\"type\":\"item\",\"name\":\"minecraft:stick\"}"), null));
        assertNull(LootTableHelper.deserializeCustomEntry(new JsonParser().parse("{\"weight\":1}"), null));
        assertNull(LootTableHelper.serializeCustomEntry(new LootEntryEmpty(1, 0, new LootCondition[0], "e"), null));
    }

    @Test
    void initializeNeedsNoReflection() {
        // it used to reach into Gson internals through Field.modifiers, which fails on current JDKs
        LootTableHelper.initialize();
    }
}
