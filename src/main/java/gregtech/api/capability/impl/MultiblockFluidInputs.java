package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;

import net.minecraftforge.fluids.IFluidTank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Keeps a combined input's tanks local to that input in distinct mode. */
public final class MultiblockFluidInputs {

    private MultiblockFluidInputs() {}

    public static List<IFluidTank> shared(List<? extends IFluidTank> fluidAbilities, Iterable<?> itemAbilities) {
        Set<IFluidTank> owned = identities();
        for (Object handler : itemAbilities) {
            for (IFluidTank tank : tanks(handler)) owned.add(identity(tank));
        }
        List<IFluidTank> shared = new ArrayList<>();
        for (IFluidTank tank : fluidAbilities) {
            if (!owned.contains(identity(tank))) shared.add(tank);
        }
        return shared;
    }

    public static IMultipleTankHandler extend(IMultipleTankHandler shared, Iterable<?> itemHandlers) {
        List<IFluidTank> result = new ArrayList<>(shared.getFluidTanks());
        Set<IFluidTank> present = identities();
        for (IFluidTank tank : result) present.add(identity(tank));
        for (Object handler : itemHandlers) {
            for (IFluidTank tank : tanks(handler)) {
                if (present.add(identity(tank))) result.add(tank);
            }
        }
        // Keep the original entries, so their owning handler's fill policy and notifications survive.
        return FluidTankList.preservingTankOwners(shared.allowSameFluidFill(), result);
    }

    private static List<? extends IFluidTank> tanks(Object handler) {
        if (handler instanceof IMultipleTankHandler multiple) return multiple.getFluidTanks();
        if (handler instanceof IFluidTank tank) return Collections.singletonList(tank);
        return Collections.emptyList();
    }

    private static Set<IFluidTank> identities() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static IFluidTank identity(IFluidTank tank) {
        Set<IFluidTank> visited = identities();
        while (tank instanceof IMultipleTankHandler.ITankEntry entry && visited.add(tank)) {
            tank = entry.getDelegate();
        }
        return tank;
    }
}
