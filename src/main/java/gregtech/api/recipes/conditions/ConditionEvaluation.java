package gregtech.api.recipes.conditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of one observed check. Descriptions in the failed clause are alternatives. */
public final class ConditionEvaluation {

    public static final ConditionEvaluation PASS = new ConditionEvaluation(ConditionState.PASS,
            Collections.emptyList());
    private final ConditionState state;
    private final List<ConditionDescription> alternatives;

    ConditionEvaluation(ConditionState state, List<ConditionDescription> alternatives) {
        this.state = state;
        this.alternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
    }

    public ConditionState state() {
        return state;
    }

    public boolean isSuccess() {
        return state == ConditionState.PASS;
    }

    public List<ConditionDescription> alternatives() {
        return alternatives;
    }
}
