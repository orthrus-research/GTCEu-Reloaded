package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Function;

/** Retains only the best candidates; the queue head is the worst retained candidate. */
final class MEStockingSelection<T> {

    private final int capacity;
    private final Comparator<T> bestFirst;
    private final PriorityQueue<T> candidates;

    MEStockingSelection(int capacity, Comparator<T> bestFirst) {
        if (capacity < 1) throw new IllegalArgumentException("Selection capacity must be positive");
        this.capacity = capacity;
        this.bestFirst = bestFirst;
        this.candidates = new PriorityQueue<>(capacity, bestFirst.reversed());
    }

    void offer(T candidate) {
        if (candidates.size() < capacity) {
            candidates.offer(candidate);
        } else if (bestFirst.compare(candidate, candidates.peek()) < 0) {
            candidates.poll();
            candidates.offer(candidate);
        }
    }

    int size() {
        return candidates.size();
    }

    List<T> sorted() {
        List<T> result = new ArrayList<>(candidates);
        result.sort(bestFirst);
        return result;
    }

    static final class Candidate<T> implements Comparable<Candidate<T>> {

        private final T stack;
        private final long amount;
        private final Function<T, String> identity;
        private String key;

        Candidate(T stack, long amount, Function<T, String> identity) {
            this.stack = stack;
            this.amount = amount;
            this.identity = identity;
        }

        T getStack() {
            return stack;
        }

        private String key() {
            if (key == null) this.key = identity.apply(stack);
            return key;
        }

        @Override
        public int compareTo(Candidate<T> other) {
            int byAmount = Long.compare(other.amount, amount);
            return byAmount != 0 ? byAmount : key().compareTo(other.key());
        }
    }
}
