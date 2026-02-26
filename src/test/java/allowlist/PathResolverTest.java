package allowlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PathResolverTest {
  @TempDir Path tmp;

  @Test
  void universalPath_isAlwaysUnderDotConfig() {
    Path p = PathResolver.universalPath(tmp.toString());
    assertTrue(p.toString().contains(".config"));
    assertTrue(p.toString().endsWith("javaWhitelist" + java.io.File.separator + "allowlist.txt"));
  }

  @Test
  void windows_usesAPPDATAWhenPresent() {
    Map<String, String> env = new HashMap<>();
    env.put("APPDATA", tmp.resolve("Roaming").toString());

    Path p = PathResolver.osSpecificPath("Windows", "C:\\Users\\X", env);
    assertEquals(
        tmp.resolve("Roaming").resolve("javaWhitelist").resolve("allowlist.txt").normalize(),
        p.normalize());
  }

  @Test
  void windows_fallsBackToUserHomeRoamingWhenNoAPPDATA() {
    Map<String, String> env = new HashMap<>();
    Path p = PathResolver.osSpecificPath("Windows", "C:\\Users\\X", env);

    assertTrue(p.toString().contains("AppData"));
    assertTrue(p.toString().contains("Roaming"));
    assertTrue(p.toString().endsWith("javaWhitelist" + java.io.File.separator + "allowlist.txt"));
  }

  @Test
  void macos_usesApplicationSupport() {
    Map<String, String> env = new HashMap<>();
    Path p = PathResolver.osSpecificPath("Mac OS X", tmp.toString(), env);

    assertTrue(p.toString().contains("Library"));
    assertTrue(p.toString().contains("Application Support"));
    assertTrue(p.toString().endsWith("javaWhitelist" + java.io.File.separator + "allowlist.txt"));
  }

  @Test
  void linux_usesXdgConfigHomeWhenSet() {
    Map<String, String> env = new HashMap<>();
    env.put("XDG_CONFIG_HOME", tmp.resolve("xdg").toString());

    Path p = PathResolver.osSpecificPath("Linux", "/home/x", env);
    assertEquals(
        tmp.resolve("xdg").resolve("javaWhitelist").resolve("allowlist.txt").normalize(),
        p.normalize());
  }

  @Test
  void linux_fallsBackToDotConfigWhenNoXdg() {
    Map<String, String> env = new HashMap<>();
    Path p = PathResolver.osSpecificPath("Linux", tmp.toString(), env);

    assertTrue(p.toString().contains(".config"));
    assertTrue(p.toString().endsWith("javaWhitelist" + java.io.File.separator + "allowlist.txt"));
  }
}
