package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.helpers.BaseActionSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertSame;

/** Deterministic monitor boundary; the stocking part's production polling and extraction code runs unchanged. */
final class StockingTestStorage<T extends IAEStack<T>> {

    final List<T> contents = new ArrayList<>();
    final IMEMonitor<T> monitor;
    final IActionSource actionSource = new BaseActionSource();
    long extractionLimit = Long.MAX_VALUE;
    int simulations;
    int modulations;
    int listScans;

    @SuppressWarnings("unchecked")
    StockingTestStorage(BiPredicate<T, T> sameType) {
        IItemList<T> list = (IItemList<T>) Proxy.newProxyInstance(IItemList.class.getClassLoader(),
                new Class<?>[] { IItemList.class }, (proxy, method, args) -> {
                    if (method.getName().equals("iterator")) return contents.iterator();
                    throw new AssertionError("Unexpected list operation: " + method.getName());
                });
        this.monitor = (IMEMonitor<T>) Proxy.newProxyInstance(IMEMonitor.class.getClassLoader(),
                new Class<?>[] { IMEMonitor.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getStorageList")) {
                        listScans++;
                        return list;
                    }
                    if (method.getName().equals("extractItems")) {
                        assertSame(actionSource, args[2], "stocking extraction must preserve its action source");
                        T request = (T) args[0];
                        boolean simulate = args[1] == Actionable.SIMULATE;
                        if (simulate) simulations++;
                        else modulations++;
                        for (T stored : contents) {
                            if (!sameType.test(stored, request)) continue;
                            long amount = Math.min(extractionLimit,
                                    Math.min(stored.getStackSize(), request.getStackSize()));
                            if (amount <= 0) return null;
                            T result = stored.copy().setStackSize(amount);
                            if (!simulate) stored.decStackSize(amount);
                            return result;
                        }
                        return null;
                    }
                    throw new AssertionError("Unexpected monitor operation: " + method.getName());
                });
    }
}
