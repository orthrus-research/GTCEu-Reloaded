package gregtech.api.capability.impl;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Which input buses a multiblock in distinct mode may skip. A bus whose contents matched no recipe is invalidated and
 * stays skipped until it changes; a change to any fluid input revives every bus.
 * <p>
 * {@link MultiblockRecipeLogic} owns the list of invalidated buses, which subclasses may read, and decides with this
 * class whether a distinct search is worth running.
 */
final class DistinctBusInvalidation {

    private DistinctBusInvalidation() {}

    /**
     * Consumes the input notifications and revives the buses they name: a notified fluid input revives every bus, and
     * a notified bus revives itself and every invalidated bus list that contains it.
     *
     * @param invalidated    the invalidated buses, shortened in place
     * @param notifiedItems  the notified item inputs, consumed
     * @param notifiedFluids the notified fluid inputs, consumed when one is present
     * @param buses          the controller's input buses, asked for only when some bus is invalidated
     * @return whether a search may find a recipe: nothing is invalidated, a notification revived a bus, or some bus
     *         was never invalidated
     */
    static boolean canSearch(List<IItemHandlerModifiable> invalidated, List<IItemHandlerModifiable> notifiedItems,
                             List<IFluidHandler> notifiedFluids, Supplier<List<IItemHandlerModifiable>> buses) {
        if (invalidated.isEmpty()) return true;

        boolean canWork = false;
        if (!notifiedFluids.isEmpty()) {
            canWork = true;
            invalidated.clear();
            notifiedFluids.clear();
            notifiedItems.clear();
        } else {
            Iterator<IItemHandlerModifiable> notifiedIter = notifiedItems.iterator();
            while (notifiedIter.hasNext()) {
                IItemHandlerModifiable bus = notifiedIter.next();
                Iterator<IItemHandlerModifiable> invalidatedIter = invalidated.iterator();
                while (invalidatedIter.hasNext()) {
                    IItemHandler invalidatedHandler = invalidatedIter.next();
                    if (invalidatedHandler instanceof ItemHandlerList) {
                        for (IItemHandler ih : ((ItemHandlerList) invalidatedHandler).getBackingHandlers()) {
                            if (ih == bus) {
                                canWork = true;
                                invalidatedIter.remove();
                                break;
                            }
                        }
                    } else if (invalidatedHandler == bus) {
                        canWork = true;
                        invalidatedIter.remove();
                    }
                }
                notifiedIter.remove();
            }
        }

        List<IItemHandler> flattenedHandlers = new ArrayList<>();
        for (IItemHandler ih : buses.get()) {
            if (ih instanceof ItemHandlerList) {
                flattenedHandlers.addAll(((ItemHandlerList) ih).getBackingHandlers());
            }
            flattenedHandlers.add(ih);
        }
        if (!invalidated.containsAll(flattenedHandlers)) {
            canWork = true;
        }
        return canWork;
    }
}
