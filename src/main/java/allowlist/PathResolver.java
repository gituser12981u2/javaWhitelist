package allowlist;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

/**
 * Computes candidate locations for the allowlist configuration file.
 *
 * <p>This class is pure in that it only constructs {@link Path} values and does not perform
 * filesystem I/O. Callers are expected to check the existence and readability and choose a
 * precedence order.
 *
 * <h3>Universal config location</h3>
 *
 * {@code ~/.config/javaWhitelist/allowlist.txt}
 *
 * <h3>OS-specific config locations</h3>
 *
 * <ul>
 *   <li><b>Windows</b>: {@code %APPDATA%\javaWhitelist/allowlist.txt} (preferred), else {@code
 *       %USERPROFILE%\AppData\Roaming\javaWhitelist\allowlist.txt}
 *   <li><b>macOS</b>: {@code ~/Library/Application Support/javaWhitelist/allowlist.txt}
 *   <li><b>Linux</b>: {@code $XDG_CONFIG_HOME/javaWhitelist/allowlist.txt} else {@code
 *       ~/.config/javaWhitelist/allowlist.txt}
 * </ul>
 */
public final class PathResolver {
  /** Application directory name under config roots. */
  private static final String APP_DIR_NAME = "javaWhitelist";

  /** Allowlist filename under the application directory. */
  private static final String ALLOWLIST_FILE_NAME = "allowlist.txt";

  /** No instances. */
  private PathResolver() {}

  /**
   * Returns the universal allowlist path under {@code ~/.config} for any OS.
   *
   * <p>This is checked first by {@link allowlist.AllowlistChecker} to support a consistent config
   * location across platforms.
   *
   * @param userHome the user's home directory.
   * @return the candidate path {@code <userHome>/.config/javaWhitelist/allowlist.txt}.
   */
  public static Path universalPath(String userHome) {
    return Paths.get(userHome, ".config", APP_DIR_NAME, ALLOWLIST_FILE_NAME);
  }

  /**
   * Returns the OS-specific allowlist path using provided OS name, home directory, and env.
   *
   * <p>This method does not validate that {@code userHome} or any environment variables exist. It
   * only selects the appropriate convention for the target OS.
   *
   * <h4>Resolution</h4>
   *
   * <ul>
   *   <li><b>Windows</b>: if {@code env["APPDATA"]} is set and non blank, then use it; otherwise,
   *       fallback to {@code <userHome>/AppData/Roaming}.
   *   <li><b>macOS</b>: {@code <userHome>/Library/Application Support}.
   *   <li><b>Linux</b>: if {@code env["XDG_CONFIG_HOME"]} is set and non blank, then use it;
   *       otherwise, fallback to {@code <userHome>/.config}.
   * </ul>
   *
   * @param osName the OS name
   * @param userHome the user's home directory
   * @param env environment map
   * @return the candidate OS specific allowlist path.
   */
  public static Path osSpecificPath(String osName, String userHome, Map<String, String> env) {
    String os = (osName == null) ? "" : osName.toLowerCase(Locale.ROOT);

    if (os.contains("win")) {
      // Prefer APPDATA
      String appData = (env == null) ? null : env.get("APPDATA");
      if (appData != null && !appData.isBlank()) {
        return Paths.get(appData, APP_DIR_NAME, ALLOWLIST_FILE_NAME);
      }

      // Fallback to roaming location under user home
      return Paths.get(userHome, "AppData", "Roaming", APP_DIR_NAME, ALLOWLIST_FILE_NAME);
    }

    if (os.contains("mac")) {
      return Paths.get(
          userHome, "Library", "Application Support", APP_DIR_NAME, ALLOWLIST_FILE_NAME);
    }

    // Prefer XDG_CONFIG_HOME if set, else ~/.config
    String xdg = (env == null) ? null : env.get("XDG_CONFIG_HOME");
    if (xdg != null && !xdg.isBlank()) {
      return Paths.get(xdg, APP_DIR_NAME, ALLOWLIST_FILE_NAME);
    }

    return Paths.get(userHome, ".config", APP_DIR_NAME, ALLOWLIST_FILE_NAME);
  }
}
