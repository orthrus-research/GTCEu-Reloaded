package gregtech.api.configuration;

import gregtech.Bootstrap;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.common.covers.filter.IFilter;
import gregtech.common.covers.filter.SimpleFluidFilter;
import gregtech.common.covers.filter.SimpleItemFilter;
import gregtech.common.items.MetaItems;
import gregtech.common.items.behaviors.ConfigurationCardBehavior;
import gregtech.common.items.behaviors.DataItemBehavior;
import gregtech.common.items.behaviors.IntCircuitBehaviour;
import gregtech.common.items.behaviors.filter.SimpleFilterUIManager;
import gregtech.common.items.behaviors.filter.SimpleFluidFilterUIManager;

/** Register only the production components exercised here; no full item registration or Forge event phase. */
final class ConfigurationTestItems {

    private final MetaItem<?>.MetaValueItem previousCard = MetaItems.CONFIGURATION_CARD;
    private final MetaItem<?>.MetaValueItem previousStick = MetaItems.TOOL_DATA_STICK;
    private final MetaItem<?>.MetaValueItem previousCircuit = MetaItems.INTEGRATED_CIRCUIT;
    private final MetaItem<?>.MetaValueItem previousItemFilter = MetaItems.ITEM_FILTER;
    private final MetaItem<?>.MetaValueItem previousFluidFilter = MetaItems.FLUID_FILTER;

    private ConfigurationTestItems() {
        MetaItem<?> meta = MetaItems.ITEMS.get(0);
        MetaItems.CONFIGURATION_CARD = existingOrNew(meta, 1011, "tool.configuration_card");
        if (MetaItems.CONFIGURATION_CARD.getBehaviours().stream()
                .noneMatch(behavior -> behavior instanceof ConfigurationCardBehavior)) {
            MetaItems.CONFIGURATION_CARD.addComponents(new ConfigurationCardBehavior());
        }
        MetaItems.CONFIGURATION_CARD.setMaxStackSize(1);
        MetaItems.TOOL_DATA_STICK = existingOrNew(meta, 261, "tool.datastick");
        if (MetaItems.TOOL_DATA_STICK.getBehaviours().stream()
                .noneMatch(behavior -> behavior instanceof DataItemBehavior)) {
            MetaItems.TOOL_DATA_STICK.addComponents(new DataItemBehavior());
        }
        MetaItems.INTEGRATED_CIRCUIT = existingOrNew(meta, 461, "circuit.integrated");
        if (MetaItems.INTEGRATED_CIRCUIT.getBehaviours().stream()
                .noneMatch(behavior -> behavior instanceof IntCircuitBehaviour)) {
            MetaItems.INTEGRATED_CIRCUIT.addComponents(new IntCircuitBehaviour());
        }
        MetaItems.ITEM_FILTER = existingOrNew(meta, 291, "item_filter");
        if (MetaItems.ITEM_FILTER.getFilterFactory() == null) {
            MetaItems.ITEM_FILTER.addComponents(new SimpleFilterUIManager(), IFilter.factory(SimpleItemFilter::new));
        }
        MetaItems.FLUID_FILTER = existingOrNew(meta, 290, "fluid_filter");
        if (MetaItems.FLUID_FILTER.getFilterFactory() == null) {
            MetaItems.FLUID_FILTER.addComponents(new SimpleFluidFilterUIManager(),
                    IFilter.factory(SimpleFluidFilter::new));
        }
    }

    static ConfigurationTestItems setup() {
        Bootstrap.perform();
        return new ConfigurationTestItems();
    }

    void restore() {
        MetaItems.CONFIGURATION_CARD = previousCard;
        MetaItems.TOOL_DATA_STICK = previousStick;
        MetaItems.INTEGRATED_CIRCUIT = previousCircuit;
        MetaItems.ITEM_FILTER = previousItemFilter;
        MetaItems.FLUID_FILTER = previousFluidFilter;
    }

    private static MetaItem<?>.MetaValueItem existingOrNew(MetaItem<?> meta, int id, String name) {
        MetaItem<?>.MetaValueItem existing = meta.getItem(name);
        return existing == null ? meta.addItem(id, name) : existing;
    }
}
