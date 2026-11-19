package org.unipod.unipoderpbackend.controller.equipment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Starts all the tasks at the same moment so they really overlap, and returns their results in order. */
public final class ConcurrentRunner {
    private ConcurrentRunner() {
    }

    public static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Not every task became ready, so the race would not be a real one");
            }
            go.countDown();
            // One deadline for the whole batch, so a stalled run cannot block for a minute per task.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
