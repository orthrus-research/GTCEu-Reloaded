package gregtech.integration.hwyla;

import gregtech.api.util.Mods;

import net.minecraftforge.fml.common.Mod;

import mcp.mobius.waila.Waila;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HWYLAModIdTest {

    @Test
    void theHwylaModIdIsTheOneHwylaRegisters() {
        // read from the annotation so that HWYLA's main class is not initialized
        assertEquals(Waila.class.getAnnotation(Mod.class).modid(), Mods.Names.HWYLA);
    }
}
