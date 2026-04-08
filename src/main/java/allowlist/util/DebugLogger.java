package allowlist.util;

import java.io.PrintStream;

/**
 * Minimal debug logging abstraction used for diagnostics.
 *
 * <p>This interface supports a single debug-level message operation and can be backed by a real
 * output sink or by a no-op implementation in production.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * private static final DebugLogger LOG = DebugLoggers.forClass(MyClass.class);
 * LOG.debug("something happened");
 * }</pre>
 */
@FunctionalInterface
public interface DebugLogger {
  /**
   * Emits a debug message.
   *
   * @param message message text to log.
   */
  void debug(String message);

  /**
   * Returns a logger that discards all messages.
   *
   * @return no-op logger.
   */
  static DebugLogger noop() {
    return message -> {};
  }

  /**
   * Returns a logger that writes message to the provided error stream.
   *
   * @param err destination stream.
   * @return logger that prints each message via {@link PrintStream#println(String)}.
   */
  static DebugLogger toErr(PrintStream err) {
    return message -> err.println(message);
  }
}
