package gregtech.api.unification.material;

import gregtech.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MaterialColorAverageTest {

    @BeforeAll
    public static void prepare() {
        Bootstrap.perform();
    }

    @Test
    public void averagesEachChannelSeparately() {
        // Ruridit is two parts Ruthenium (0x50ACCD) to one part Iridium (0xA1E4E4)
        assertEquals(0x6BBED4, Materials.Ruridit.getMaterialRGB());
    }
}
