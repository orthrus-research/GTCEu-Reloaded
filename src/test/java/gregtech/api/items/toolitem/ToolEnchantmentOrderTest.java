package gregtech.api.items.toolitem;

import gregtech.Bootstrap;
import gregtech.api.GregTechAPI;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.MaterialToolProperty;
import gregtech.api.unification.material.registry.IMaterialRegistryManager;
import gregtech.core.unification.material.internal.MaterialRegistryManager;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Enchantments;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolEnchantmentOrderTest {

    private IMaterialRegistryManager previous;

    @BeforeEach
    void setup() throws Exception {
        Bootstrap.perform();
        previous = GregTechAPI.materialManager;
        var constructor = MaterialRegistryManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var manager = constructor.newInstance();
        GregTechAPI.materialManager = manager;
        manager.unfreezeRegistries();
    }

    @AfterEach
    void restore() {
        GregTechAPI.materialManager = previous;
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void constructedToolsSerializeEnchantmentsCanonicallyWithoutChangingLevelsOrEligibility(boolean explicitStats) {
        var material = Material.builder(71, new ResourceLocation("susy", "enchantment_order"))
                .ingot().toolStats(MaterialToolProperty.Builder.of(4, 2, 500, 2)
                        .enchantment(Enchantments.VANISHING_CURSE, 1)
                        .enchantment(Enchantments.UNBREAKING, 2)
                        .enchantment(Enchantments.MENDING, 1)
                        .enchantment(Enchantments.EFFICIENCY, 2).build())
                .build();
        var tool = ItemGTTool.Builder.of("enchantment_test", "tool_" + explicitStats)
                .toolStats(builder -> builder.defaultEnchantment(Enchantments.UNBREAKING, 3)).build();
        var stack = explicitStats ? ToolHelper.getAndSetToolData(tool, material, 499, 2, 4, 2) : tool.get(material);
        var enchantments = stack.getEnchantmentTagList();
        var actual = new ArrayList<String>();
        for (int i = 0; i < enchantments.tagCount(); i++) {
            var enchantment = enchantments.getCompoundTagAt(i);
            actual.add(Enchantment.getEnchantmentByID(enchantment.getShort("id")).getRegistryName() + ":" +
                    enchantment.getShort("lvl"));
        }
        assertEquals(List.of("minecraft:mending:1", "minecraft:unbreaking:" + (explicitStats ? 2 : 3),
                "minecraft:vanishing_curse:1"), actual);
    }
}
