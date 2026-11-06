package gregtech.api.recipes.definition;

import gregtech.api.recipes.chance.output.ChancedOutputLogic;

/** Fixed native chance algorithms, with no retained script callback. */
public enum ChanceMode {

    OR(ChancedOutputLogic.OR),
    AND(ChancedOutputLogic.AND),
    XOR(ChancedOutputLogic.XOR),
    NONE(ChancedOutputLogic.NONE);

    private final ChancedOutputLogic logic;

    ChanceMode(ChancedOutputLogic logic) {
        this.logic = logic;
    }

    public ChancedOutputLogic nativeLogic() {
        return logic;
    }
}
