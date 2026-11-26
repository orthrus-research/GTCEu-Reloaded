package gregtech.common.covers.filter;

import gregtech.common.items.MetaItems;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleItemFilterReaderTest {

    private static CompositeFilterTestSupport support;

    @BeforeAll
    static void setup() throws ReflectiveOperationException {
        support = new CompositeFilterTestSupport();
    }

    @AfterAll
    static void restore() throws ReflectiveOperationException {
        support.close();
    }

    @ParameterizedTest
    @CsvSource({ "true, true", "true, false", "false, true", "false, false" })
    void aFilterReadsBackTheDamageAndNbtSettingsItSaved(boolean ignoreDamage, boolean ignoreNBT) {
        SimpleItemFilter saved = new SimpleItemFilter(MetaItems.ITEM_FILTER.getStackForm());
        saved.getFilterReader().setIgnoreDamage(ignoreDamage);
        saved.getFilterReader().setIgnoreNBT(ignoreNBT);
        NBTTagCompound tag = saved.getFilterReader().serializeNBT().copy();

        SimpleItemFilter loaded = new SimpleItemFilter(MetaItems.ITEM_FILTER.getStackForm());
        loaded.readFromNBT(tag);

        assertEquals(ignoreDamage, loaded.getFilterReader().isIgnoreDamage());
        assertEquals(ignoreNBT, loaded.getFilterReader().isIgnoreNBT());
    }
}
