package gregtech.api.capability.impl;

import gregtech.Bootstrap;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class DistinctBusInvalidationTest {

    private final IItemHandlerModifiable first = new ItemStackHandler();
    private final IItemHandlerModifiable second = new ItemStackHandler();
    private final List<IItemHandlerModifiable> buses = Arrays.asList(first, second);
    private final List<IItemHandlerModifiable> notifiedItems = new ArrayList<>();
    private final List<IFluidHandler> notifiedFluids = new ArrayList<>();

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static List<IItemHandlerModifiable> invalidated(IItemHandlerModifiable... handlers) {
        return new ArrayList<>(Arrays.asList(handlers));
    }

    private boolean canSearch(List<IItemHandlerModifiable> invalidated) {
        return DistinctBusInvalidation.canSearch(invalidated, notifiedItems, notifiedFluids, () -> buses);
    }

    @Test
    void withNothingInvalidatedTheBusesAndNotificationsAreLeftAlone() {
        notifiedItems.add(first);
        notifiedFluids.add(new FluidTankList(false));
        Supplier<List<IItemHandlerModifiable>> unasked = () -> {
            throw new AssertionError("The buses are asked for only when some bus is invalidated");
        };
        assertTrue(DistinctBusInvalidation.canSearch(new ArrayList<>(), notifiedItems, notifiedFluids, unasked));
        assertEquals(1, notifiedItems.size());
        assertEquals(1, notifiedFluids.size());
    }

    @Test
    void everyBusInvalidatedAndNothingNotifiedSkipsTheSearch() {
        List<IItemHandlerModifiable> invalidated = invalidated(first, second);
        assertFalse(canSearch(invalidated));
        assertEquals(2, invalidated.size());
    }

    @Test
    void aBusThatWasNeverInvalidatedIsWorthASearch() {
        assertTrue(canSearch(invalidated(first)));
    }

    @Test
    void aFluidChangeRevivesEveryBusAndConsumesAllNotifications() {
        List<IItemHandlerModifiable> invalidated = invalidated(first, second);
        notifiedFluids.add(new FluidTankList(false));
        notifiedItems.add(first);
        assertTrue(canSearch(invalidated));
        assertTrue(invalidated.isEmpty());
        assertTrue(notifiedFluids.isEmpty());
        assertTrue(notifiedItems.isEmpty());
    }

    @Test
    void aNotifiedBusRevivesOnlyItself() {
        List<IItemHandlerModifiable> invalidated = invalidated(first, second);
        notifiedItems.add(second);
        assertTrue(canSearch(invalidated));
        assertEquals(Collections.singletonList(first), invalidated);
        assertTrue(notifiedItems.isEmpty(), "Item notifications are consumed");
    }

    @Test
    void aNotifiedBusRevivesTheInvalidatedBusListThatContainsIt() {
        ItemHandlerList combined = new ItemHandlerList(Arrays.asList(first, second));
        List<IItemHandlerModifiable> invalidated = invalidated(combined, first, second);
        notifiedItems.add(second);
        DistinctBusInvalidation.canSearch(invalidated, notifiedItems, notifiedFluids,
                () -> Collections.singletonList(combined));
        assertEquals(Collections.singletonList(first), invalidated,
                "The list holding the bus and the bus itself are revived; the untouched bus is not");
    }

    @Test
    void aNotificationForABusThatIsNotInvalidatedIsConsumedWithoutReviving() {
        List<IItemHandlerModifiable> invalidated = invalidated(first, second);
        notifiedItems.add(new ItemStackHandler());
        assertFalse(canSearch(invalidated));
        assertEquals(2, invalidated.size());
        assertTrue(notifiedItems.isEmpty());
    }
}
