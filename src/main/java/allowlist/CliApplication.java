package allowlist;

import allowlist.cli.CommandContext;
import java.io.PrintStream;
import java.util.Objects;

public final class CliApplication {
  private CliApplication() {}

  public static CommandContext createContext(PrintStream out, PrintStream err) {
    Objects.requireNonNull(out, "out");
    Objects.requireNonNull(err, "err");

    return new CommandContext(out, err, helpText(), version(), AllowlistChecker::run);
  }

  public static String helpText() {
    return ""
        + "javaWhitelist - Java API + style whitelist checker\n"
        + "\n"
        + "Usage:\n"
        + "  javaWhitelist [--allowlist path/to/allowlist.txt] "
        + "[--ignore path/to/.javawhitelistignore] <file-or-directory>...\n"
        + "\n"
        + "Options:\n"
        + "  --help, -h        Show this help message\n"
        + "  --version, -v     Show version\n"
        + "  --allowlist PATH  Use a custom allowlist file (overrides config lookup)\n"
        + "  --ignore PATH     Ignore file (overrides default .javawhitelistignore lookup)\n"
        + "\n"
        + "Exit codes:\n"
        + "  0  No violations\n"
        + "  1  Violations found\n"
        + "  2  Usage or internal error\n";
  }

  /**
   * Returns the implementation version from the JAR manifest when available.
   *
   * @return version string of {@code "dev"} if unavailable.
   */
  private static String version() {
    Package p = AllowlistChecker.class.getPackage();
    String v = (p == null) ? null : p.getImplementationVersion();
    return (v == null || v.isBlank()) ? "dev" : v;
  }
}
