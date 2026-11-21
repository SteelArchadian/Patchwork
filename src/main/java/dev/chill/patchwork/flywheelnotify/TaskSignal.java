package dev.chill.patchwork.flywheelnotify;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces Flywheel's ThreadGroupNotifier (synchronized wait / notifyAll) for one executor. Posting a task never
 * blocks and wakes at most one sleeping worker; with nobody asleep it is a single atomic read. Each permit is handed
 * to exactly one counted sleeper, so permits never pile up.
 */
public final class TaskSignal {
    private final AtomicInteger sleepers = new AtomicInteger();
    private final Semaphore permits = new Semaphore(0);

    /**
     * A worker found no task: sleep until a task is posted or the executor stops. Waits at most 100 ms, so a post
     * or stop that slips in between the worker's last queue check and this call (the same gap the original
     * wait/notifyAll has) costs at most that long instead of a sleep until the next post, or a hang at shutdown.
     */
    public void await() {
        sleepers.incrementAndGet();
        try {
            if (permits.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Withdraw this sleeper. If a post already claimed it, its permit is on the way: take it.
        while (true) {
            int n = sleepers.get();
            if (n == 0) {
                permits.acquireUninterruptibly();
                return;
            }
            if (sleepers.compareAndSet(n, n - 1)) {
                return;
            }
        }
    }

    /** A task was queued: wake one sleeping worker, if any. */
    public void post() {
        int n;
        do {
            n = sleepers.get();
            if (n == 0) {
                return;
            }
        } while (!sleepers.compareAndSet(n, n - 1));
        permits.release();
    }

    /** The executor is stopping: wake every sleeping worker so it sees running == false and exits. */
    public void wakeAll() {
        int n = sleepers.getAndSet(0);
        if (n > 0) {
            permits.release(n);
        }
    }

    /** Implemented on ThreadGroupNotifier by Patchwork's mixin. */
    public interface Holder {
        TaskSignal patchwork$signal();
    }
}
