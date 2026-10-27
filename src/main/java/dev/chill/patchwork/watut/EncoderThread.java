package dev.chill.patchwork.watut;

import dev.chill.patchwork.Patchwork;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Encodes captured gui frames off the render thread. One daemon thread, results come back in submission order. */
final class EncoderThread {
    private final FrameCodec.Encoder encoder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Patchwork WATUT encoder");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentLinkedQueue<FrameCodec.EncodedFrame> finished = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();

    EncoderThread(FrameCodec.Encoder encoder) {
        this.encoder = encoder;
    }

    void submit(FrameCodec.RawFrame frame) {
        pending.incrementAndGet();
        executor.execute(() -> {
            try {
                finished.add(encoder.encode(frame));
            } catch (RuntimeException e) {
                Patchwork.LOG.warn("[Patchwork/watut] could not encode a gui frame, skipping it", e);
            } finally {
                pending.decrementAndGet();
            }
        });
    }

    /** True while a frame is queued or being encoded (or its result hasn't been collected yet). */
    boolean busy() {
        return pending.get() > 0 || !finished.isEmpty();
    }

    /** Next finished frame, or null. Never blocks. */
    FrameCodec.EncodedFrame poll() {
        return finished.poll();
    }
}
