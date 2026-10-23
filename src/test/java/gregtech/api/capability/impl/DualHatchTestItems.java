package gregtech.api.capability.impl;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.common.covers.filter.CompositeFilterTestSupport;
import gregtech.common.items.MetaItems;

final class DualHatchTestItems implements AutoCloseable {

    private final CompositeFilterTestSupport filters;
    private final MetaItem<?>.MetaValueItem previousCircuit;

    DualHatchTestItems() throws ReflectiveOperationException {
        this.filters = new CompositeFilterTestSupport();
        this.previousCircuit = MetaItems.INTEGRATED_CIRCUIT;
        MetaItem<?> item = MetaItems.ITEMS.get(0);
        MetaItems.INTEGRATED_CIRCUIT = item.getItem("circuit.integrated");
        if (MetaItems.INTEGRATED_CIRCUIT == null)
            MetaItems.INTEGRATED_CIRCUIT = item.addItem(461, "circuit.integrated");
    }

    @Override
    public void close() throws ReflectiveOperationException {
        MetaItems.INTEGRATED_CIRCUIT = previousCircuit;
        filters.close();
    }
}
