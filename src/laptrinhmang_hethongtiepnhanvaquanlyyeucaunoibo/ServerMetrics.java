package laptrinhmang_hethongtiepnhanvaquanlyyeucaunoibo;

import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe counters for the server monitoring dashboard. */
public final class ServerMetrics {
    private final AtomicLong processedCommands = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final AtomicLong totalResponseNanos = new AtomicLong();
    private final AtomicLong maxResponseNanos = new AtomicLong();

    public void recordCommand(long durationNanos, boolean failed) {
        processedCommands.incrementAndGet();
        totalResponseNanos.addAndGet(Math.max(0, durationNanos));
        maxResponseNanos.accumulateAndGet(Math.max(0, durationNanos), Math::max);
        if (failed) errors.incrementAndGet();
    }

    public void recordError() {
        errors.incrementAndGet();
    }

    public long getProcessedCommands() {
        return processedCommands.get();
    }

    public long getErrors() {
        return errors.get();
    }

    public double getAverageResponseMillis() {
        long count = processedCommands.get();
        return count == 0 ? 0 : totalResponseNanos.get() / 1_000_000.0 / count;
    }

    public double getMaxResponseMillis() {
        return maxResponseNanos.get() / 1_000_000.0;
    }
}
