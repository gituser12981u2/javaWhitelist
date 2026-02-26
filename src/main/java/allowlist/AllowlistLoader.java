package allowlist;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

/**
 * Loads allowlist.txt
 *
 * <p>Supported lines: - Blank lines (or only spaces): ignored - Comments: lines starting with '#':
 * ignored - Settings: lines starting with '@', e.g. @DISALLOW_NULL_LITERAL=true - Allow entries:
 * OWNER#member
 */
public final class AllowlistLoader {
  private AllowlistLoader() {}

  public static AllowlistConfig loadFromFile(String path) throws FileNotFoundException {
    InputStream in = new java.io.FileInputStream(new File(path));
    return loadFromStream(in);
  }

  public static AllowlistConfig loadFromResource(String resourceName) throws IOException {
    InputStream in = AllowlistLoader.class.getClassLoader().getResourceAsStream(resourceName);
    if (in == null) {
      throw new FileNotFoundException("Resource not found: " + resourceName);
    }

    return loadFromStream(in);
  }

  private static AllowlistConfig loadFromStream(InputStream in) throws FileNotFoundException {
    Scanner sc = new Scanner(in, "UTF-8");

    Set<String> allowed = new HashSet<>();
    List<String> prefixes = new ArrayList<>();
    EnumSet<Setting> enabled = EnumSet.noneOf(Setting.class);

    while (sc.hasNextLine()) {
      String line = sc.nextLine();

      if (isBlankOrSpaces(line)) {
        continue;
      }

      // ignore comments
      if (line.charAt(0) == '#') {
        continue;
      }

      // settings
      if (line.charAt(0) == '@') {
        int eq = line.indexOf('=');
        if (eq == -1) {
          continue;
        }

        String keyStr = line.substring(1, eq);
        String value = line.substring(eq + 1);

        Setting key = Setting.fromKey(keyStr);
        if (key == null) {
          continue;
        }

        if (key == Setting.ENFORCE_PREFIXES) {
          prefixes = splitByComma(value);
        } else {
          boolean on = value.equalsIgnoreCase("true");
          if (on) {
            enabled.add(key);
          } else {
            enabled.remove(key);
          }
        }

        continue;
      }

      allowed.add(line);
    }

    sc.close();
    return new AllowlistConfig(allowed, prefixes, enabled);
  }

  static AllowlistConfig loadFromStreamForTests(java.io.InputStream in)
      throws FileNotFoundException {
    return loadFromStream(in);
  }

  private static boolean isBlankOrSpaces(String s) {
    if (s.equals("")) {
      return true;
    }

    boolean onlySpaces = true;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) != ' ') {
        onlySpaces = false;
      }
    }

    return onlySpaces;
  }

  private static List<String> splitByComma(String s) {
    List<String> out = new ArrayList<>();

    int start = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == ',') {
        String part = s.substring(start, i);
        if (!isBlankOrSpaces(part)) {
          out.add(part);
        }

        start = i + 1;
      }
    }

    // tail
    if (start <= s.length()) {
      String part = s.substring(start);
      if (!isBlankOrSpaces(part)) {
        out.add(part);
      }
    }

    return out;
  }
}
