package gregtech.common.pipelike.itempipe.tile;

/** A transient ingress allowance. Probes read elapsed credit; only real transfers spend it. */
final class ItemTransferBudget {

    private final int rate;
    private long updatedAt;
    private double items;
    private int capacity;

    ItemTransferBudget(int rate, int transferred, int batch, long now) {
        this.rate = rate;
        this.items = Math.max(0, rate - transferred);
        this.updatedAt = now;
        this.capacity = capacity(rate, batch);
    }

    ItemTransferBudget(ItemTransferBudget original) {
        this.rate = original.rate;
        this.updatedAt = original.updatedAt;
        this.items = original.items;
        this.capacity = original.capacity;
    }

    int available(int batch, long now) {
        if (now < updatedAt) {
            this.items = rate;
            this.updatedAt = now;
        }
        int requestedCapacity = capacity(rate, batch);
        if (requestedCapacity > capacity) {
            this.items = current(now);
            advanceClock(now);
            this.capacity = requestedCapacity;
        }
        return (int) Math.max(0, Math.min(batch, current(now)));
    }

    void spend(int amount, long now) {
        if (amount == 0) return;
        this.items = current(now) - amount;
        advanceClock(now);
    }

    private void advanceClock(long now) {
        if (now < updatedAt) {
            this.updatedAt = now;
        } else {
            this.updatedAt += ((now - updatedAt) / 20) * 20;
        }
    }

    private double current(long now) {
        if (now < updatedAt) return Math.min(capacity, rate);
        long periods = (now - updatedAt) / 20;
        return Math.min(capacity, items + (double) periods * rate);
    }

    private static int capacity(int rate, int batch) {
        // Leave a window of headroom so earlier ordinary traffic cannot perpetually shave a ready exact batch.
        return (int) Math.min(Integer.MAX_VALUE, (long) rate + batch);
    }
}
