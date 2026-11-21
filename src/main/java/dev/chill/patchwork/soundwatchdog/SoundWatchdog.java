package dev.chill.patchwork.soundwatchdog;

import dev.chill.patchwork.Patchwork;
import dev.chill.patchwork.mixin.soundwatchdog.SoundEngineExecutorAccessor;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngineExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Vanilla's "Sound engine" thread can die (seen: OutOfMemoryError) without crashing the game; every later
 * SoundEngine.play then waits forever for a channel and the game freezes. This restarts a dead thread and caps the
 * wait, dropping the sound instead. Render thread only.
 */
public final class SoundWatchdog {
    private static final long WAIT_MS = 1000;

    /** The last channel request that timed out; while it is still pending, new sounds are dropped without waiting. */
    private static CompletableFuture<?> stuck;
    private static int dropped;

    private SoundWatchdog() {}

    public static void ensureAlive(SoundEngineExecutor executor) {
        SoundEngineExecutorAccessor access = (SoundEngineExecutorAccessor) executor;
        // While vanilla's flush() is restarting the thread, shutdown is set and the old thread is meant to be dead.
        if (access.patchwork$isShutdown() || access.patchwork$getThread().isAlive()) {
            return;
        }
        access.patchwork$setThread(access.patchwork$createThread());
        Patchwork.LOG.warn("Patchwork soundwatchdog: the sound engine thread had died; started a new one");
    }

    public static Object await(CompletableFuture<?> future, SoundEngineExecutor executor) {
        ensureAlive(executor);
        if (future.isDone()) {
            return future.join();
        }
        if (stuck != null) {
            if (!stuck.isDone()) {
                return drop(future, executor);
            }
            Patchwork.LOG.info("Patchwork soundwatchdog: the sound engine is responding again ({} sounds were skipped)", dropped);
            stuck = null;
            dropped = 0;
        }
        try {
            return future.get(WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            stuck = future;
            Patchwork.LOG.warn("Patchwork soundwatchdog: the sound engine did not answer within {} ms; skipping sounds until it does", WAIT_MS);
            return drop(future, executor);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return drop(future, executor);
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        }
    }

    /**
     * Skips this sound. A channel granted later is stopped, so vanilla's own channel tick releases it and takes it out
     * of ChannelAccess's list. Releasing it here instead left a released handle in that list: every later tick threw
     * (channel is null) and the next sound reload crashed the game ("Tried to release unknown channel").
     */
    private static Object drop(CompletableFuture<?> future, SoundEngineExecutor executor) {
        dropped++;
        future.thenAccept(handle -> {
            if (handle instanceof ChannelAccess.ChannelHandle channel) {
                channel.execute(Channel::stop);
            }
        });
        return null;
    }
}
