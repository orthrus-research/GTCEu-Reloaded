package gregtech.api.capability.impl;

import gregtech.api.metatileentity.MTETrait;
import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class IdleRecipeCountingTrait extends MTETrait {

    final List<Long> ticks = new ArrayList<>();

    IdleRecipeCountingTrait(MetaTileEntity machine) {
        super(machine);
    }

    @Override
    public @NotNull String getName() {
        return "IdleRecipeOtherTrait";
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        return null;
    }

    @Override
    public void update() {
        ticks.add(metaTileEntity.getOffsetTimer());
    }
}
