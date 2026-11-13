package gregtech.api.capability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilteredFluidContainerComparatorTest {

    private static final IFilteredFluidContainer UNFILTERED = () -> null;
    private static final IFilteredFluidContainer FILTERED = () -> fluid -> true;

    @Test
    public void containersWithoutFilterSortLast() {
        assertEquals(0, IFilteredFluidContainer.COMPARATOR.compare(UNFILTERED, UNFILTERED));
        assertTrue(IFilteredFluidContainer.COMPARATOR.compare(FILTERED, UNFILTERED) < 0);
        assertTrue(IFilteredFluidContainer.COMPARATOR.compare(UNFILTERED, FILTERED) > 0);
    }

    @Test
    public void nullContainersSortLast() {
        assertTrue(IFilteredFluidContainer.COMPARATOR.compare(UNFILTERED, null) < 0);
    }
}
