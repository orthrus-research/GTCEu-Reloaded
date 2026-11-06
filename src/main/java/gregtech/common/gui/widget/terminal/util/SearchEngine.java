package gregtech.common.gui.widget.terminal.util;

import gregtech.api.util.GTLog;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** One bounded worker; obsolete searches may finish but cannot publish results into a newer query. */
public class SearchEngine<T> implements AutoCloseable {

    private final ISearch<T> search;
    private final Consumer<T> result;
    private final Thread owner = Thread.currentThread();
    private final ConcurrentLinkedQueue<SearchResult<T>> pending = new ConcurrentLinkedQueue<>();
    private final ThreadPoolExecutor executor;
    private Future<?> task;
    private volatile long generation;

    public SearchEngine(@NotNull ISearch<T> search, @NotNull Consumer<T> result) {
        this.search = search;
        this.result = result;
        this.executor = new ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon().name("gregtech-terminal-search").factory());
        this.executor.allowCoreThreadTimeOut(true);
    }

    public void searchWord(String word) {
        dispose();
        long query = generation;
        this.task = executor.submit(() -> {
            try {
                search.search(word, found -> {
                    if (query == generation) pending.add(new SearchResult<>(query, found));
                });
            } catch (RuntimeException failure) {
                GTLog.logger.error("Terminal search failed", failure);
            }
        });
    }

    public boolean isSearching() {
        requireOwner();
        return task != null && (!task.isDone() || !pending.isEmpty());
    }

    /** Delivers results on the creating UI thread, never from the search worker. */
    public void drainResults() {
        requireOwner();
        SearchResult<T> found;
        while ((found = pending.poll()) != null) {
            if (found.generation() == generation) result.accept(found.value());
        }
    }

    public void dispose() {
        requireOwner();
        this.generation++;
        if (task != null) task.cancel(true);
        executor.getQueue().clear();
        pending.clear();
        this.task = null;
    }

    @Override
    public void close() {
        dispose();
        executor.shutdownNow();
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Search UI access is thread confined");
    }

    private record SearchResult<T>(long generation, T value) {}
}
