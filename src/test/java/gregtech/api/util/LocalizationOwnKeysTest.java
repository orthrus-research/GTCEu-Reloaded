package gregtech.api.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationOwnKeysTest {

    @Test
    void onlyGregTechKeysCountAsOwn() {
        assertTrue(LocalizationUtils.isOwnKey("cover.ore_dictionary_filter.preview.match"));
        assertFalse(LocalizationUtils.isOwnKey("item.stick.name"));
        assertFalse(LocalizationUtils.isOwnKey("othermod.unit.meters_per_n"));
        assertFalse(LocalizationUtils.isOwnKey(null));
    }
}
