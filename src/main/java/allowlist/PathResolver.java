package allowlist;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

public final class PathResolver {
    private static final String APP_DIR_NAME = "javaWhitelist";
    private static final String ALLOWLIST_FILE_NAME = "allowlist.txt";

    private PathResolver() {
    }

    public static Path universalPath(String userHome) {
        return Paths.get(userHome, ".config", APP_DIR_NAME, ALLOWLIST_FILE_NAME);
    }

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
            return Paths.get(userHome, "Library", "Application Support", APP_DIR_NAME, ALLOWLIST_FILE_NAME);
        }

        // Prefer XDG_CONFIG_HOME if set, else ~/.config
        String xdg = (env == null) ? null : env.get("XDG_CONFIG_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Paths.get(xdg, APP_DIR_NAME, ALLOWLIST_FILE_NAME);
        }

        return Paths.get(userHome, ".config", APP_DIR_NAME, ALLOWLIST_FILE_NAME);
    }

}
