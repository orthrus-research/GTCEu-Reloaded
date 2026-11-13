package gregtech.api.recipes.ingredients;

import gregtech.Bootstrap;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.common.items.MetaItems;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntCircuitQueryTest {

    private static MetaItem<?>.MetaValueItem previousCircuit;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
        previousCircuit = MetaItems.INTEGRATED_CIRCUIT;
        if (previousCircuit == null) {
            MetaItem<?> metaItem = MetaItems.ITEMS.get(0);
            MetaItems.INTEGRATED_CIRCUIT = metaItem.getItem("circuit.integrated");
            if (MetaItems.INTEGRATED_CIRCUIT == null) {
                MetaItems.INTEGRATED_CIRCUIT = metaItem.addItem(461, "circuit.integrated");
            }
        }
    }

    @AfterAll
    static void restoreCircuit() {
        MetaItems.INTEGRATED_CIRCUIT = previousCircuit;
    }

    @Test
    void askingAboutAnUntaggedCircuitLeavesItUntagged() {
        ItemStack circuit = MetaItems.INTEGRATED_CIRCUIT.getStackForm();
        circuit.setTagCompound(null);

        assertTrue(IntCircuitIngredient.isIntegratedCircuit(circuit));
        assertEquals(0, IntCircuitIngredient.getCircuitConfiguration(circuit));
        assertFalse(circuit.hasTagCompound());
    }
}
