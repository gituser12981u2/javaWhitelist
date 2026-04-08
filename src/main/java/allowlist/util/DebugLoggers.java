package allowlist.util;

import java.io.PrintStream;

/**
 * Factory and global configuration holder for class scoped debug loggers.
 *
 * <p>Debug logging is enabled only when the JVM system property {@code javawhitelist.debugTiming}
 * is {@code true}. When disabled, {@link #forClass(Class)} returns a no-op logger.
 *
 * <p>When enabled, loggers write to a shared error stream, which defaults to {@link System#err} and
 * may be redirected through {@link #setErrorStream(PrintStream)}.
 */
public final class DebugLoggers {
  private static final boolean ENABLED = Boolean.getBoolean("javawhitelist.debugTiming");

  private static volatile PrintStream errStream = System.err;

  private DebugLoggers() {}

  public static void setErrorStream(PrintStream err) {
    if (err != null) {
      errStream = err;
    }
  }

  /**
   * Returns a class scoped debug logger.
   *
   * <p>When debug logging is enabled, the returned logger prefixes message with the simple class
   * name, for example:
   *
   * <pre>{@code
   * [debug:MavenJavacOptionsResolver] resolve source roots: 3 ms
   * }</pre>
   *
   * <p>When the debug logging is disabled, the returned logger discards all message.
   *
   * @param clazz owning class for the logger prefix.
   * @return enabled prefixed logger or no-op logger.
   */
  public static DebugLogger forClass(Class<?> clazz) {
    String prefix = "[debug:" + clazz.getSimpleName() + "] ";

    if (!ENABLED) {
      return message -> {};
    }

    return message -> errStream.println(prefix + message);
  }
}
