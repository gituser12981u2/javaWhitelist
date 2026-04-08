package allowlist.util;

/**
 * Helper for timing code regions and logging their elapsed duration.
 *
 * <p>Callers capture a start timestamp with {@link #start()} and later report the elapsed time with
 * {@link #log(DebugLogger, String, long)}.
 *
 * <p>Example:
 *
 * <pre>{@code
 * long t = DebugTiming.start();
 * doWork();
 * DebugTiming.log(LOG, "doWork", t);
 * }</pre>
 */
public final class DebugTiming {
  private DebugTiming() {}

  /**
   * Returns the current time source for elapsed-time measurement.
   *
   * @return current value of {@line System#nanoTime()}.
   */
  public static long start() {
    return System.nanoTime();
  }

  /**
   * Logs the elapsed time since {@code startNanos} using the provided logger.
   *
   * @param logger destination logger.
   * @param phase human-readable phase name.
   * @param startNanos timestamp previously returned by {@link #start()}.
   */
  public static void log(DebugLogger logger, String phase, long startNanos) {
    long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
    logger.debug(phase + ": " + elapsedMillis + " ms");
  }
}
