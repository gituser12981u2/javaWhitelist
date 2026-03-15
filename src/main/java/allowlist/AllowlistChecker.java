package allowlist;

import allowlist.cli.CliCommand;
import allowlist.cli.CliOptions;
import allowlist.cli.CliParser;
import java.io.IOException;
import java.io.PrintStream;

/**
 * CLI entry point for the Java API and style whitelist checker.
 *
 * <h3>Usage</h3>
 *
 * {@code java -jar target/javaWhitelist [--allowlist path/to/allowlist.txt] [--ignore
 * path/to/.javawhitelistignore] <.java file or directory> ...}
 *
 * <h3>Allowlist resolution</h3>
 *
 * <ol>
 *   <li>If {@code --allowlist PATH} is provided, that file is used.
 *   <li>Otherwise, starting from the nearest project root (nearest ancestor containing {@code
 *       pom.xml}), search upward for the first {@code allowlist.txt}. The first match is used.
 *   <li>Otherwise, check the universal path {@code ~/.config/javaWhitelist/allowlist.txt} (all
 *       OSes).
 *   <li>Otherwise, check the OS-specific config path:
 *       <ul>
 *         <li>Windows: {@code %APPDATA%\javaWhitelist\allowlist.txt} else home roaming fallback
 *         <li>macOS: {@code ~/Library/Application Support/javaWhitelist/allowlist.txt}
 *         <li>Linux/Unix: {@code $XDG_CONFIG_HOME/javaWhitelist/allowlist.txt} else {@code
 *             ~/.config/...}
 *       </ul>
 *   <li>Otherwise, fallback to the bundled classpath resource {@code allowlist.txt}.
 * </ol>
 *
 * <p>When scanning multiple Maven modules, resolution is performed per compilation group. A
 * module-local {@code allowlist.txt} overrides a parent/root {@code allowlist.txt}
 *
 * <h3>Ignore file</h3>
 *
 * <p>Input paths are scanned for {@code .java} files, optionally excluding files matched by an
 * ignore file (gitignore-style glob patterns).
 *
 * <p>Ignore file resolution:
 *
 * <ol>
 *   <li>If {@code --ignore PATH} is provided, that file is used.
 *   <li>Otherwise, if {@code .javawhitelistignore} exists in the current working directory, it is
 *       used.
 *   <li>Otherwise, no ignore filtering is applied.
 * </ol>
 *
 * <h3>Exit codes</h3>
 *
 * <ul>
 *   <li>{@code 0}: no violations
 *   <li>{@code 1}: violations found
 *   <li>{@code 2}: usage error or internal/tool failure
 * </ul>
 */
public final class AllowlistChecker {

  public static void main(String[] args) throws IOException {
    try {
      CliCommand command = CliParser.parse(args);
      int exitCode = command.execute(CliApplication.createContext(System.out, System.err));
      System.exit(exitCode);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      System.err.print(CliApplication.helpText());
      System.exit(2);
    }
  }

  static int run(CliOptions options, PrintStream err) throws IOException {
    return newCheckRunner().run(options, err);
  }

  private static CheckRunner newCheckRunner() {
    AllowlistResolver resolver =
        new AllowlistResolver(
            System.getProperty("os.name"), System.getProperty("user.home"), System.getenv());
    return new CheckRunner(resolver);
  }
}
