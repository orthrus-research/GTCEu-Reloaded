package gregtech.api.capability.impl;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;

import java.util.Collections;
import java.util.List;

/** Exposes only physical fluid abilities, exercising ordinary shared-tank selection beside paired inputs. */
final class PaintGroupTestFluidPart extends PaintGroupTestPart {

    PaintGroupTestFluidPart(int color) {
        super(false, color);
    }

    @Override
    public List<MultiblockAbility<?>> getAbilities() {
        return Collections.singletonList(MultiblockAbility.IMPORT_FLUIDS);
    }
}
