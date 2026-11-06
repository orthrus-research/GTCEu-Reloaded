package gregtech.api.recipes.definition;

import java.util.Objects;

/** Research identity and immutable item snapshots. Generated recipes are owned with their assembly declaration. */
public sealed interface ResearchRequirement {

    String id();

    ItemValue dataItem();

    record Existing(String id, ItemValue dataItem) implements ResearchRequirement {

        public Existing {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(dataItem, "dataItem");
        }
    }

    record Scanner(String id, ItemValue dataItem, ItemValue researchItem, boolean ignoreNbt,
                   int duration, long eut)
            implements ResearchRequirement {

        public Scanner {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(dataItem, "dataItem");
            Objects.requireNonNull(researchItem, "researchItem");
        }
    }

    record Station(String id, ItemValue dataItem, ItemValue researchItem, boolean ignoreNbt,
                   int computationPerTick, int totalComputation, long eut)
            implements ResearchRequirement {

        public Station {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(dataItem, "dataItem");
            Objects.requireNonNull(researchItem, "researchItem");
        }
    }
}
