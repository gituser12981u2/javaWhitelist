package allowlist;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Ignore matcher with glob rules.
 *
 * <p>Rules are read from a file, one pattern per line.
 *
 * <h3>Supported</h3>
 *
 * <ul>
 *   <li>Blank lines and {@code # comments}
 *   <li>Glob patterns via {@link FileSystem#getPathMatcher(String)}
 *   <li>{@code **} recursive matching
 *   <li>Negation via leading {@code !} (last match wins)
 *   <li>If a pattern contains no {@code / }, it is treated as {@code ** /pattern}
 * </ul>
 */
public final class IgnoreMatcher {
  private static final class Rule {
    final boolean negated;
    final PathMatcher matcher;

    Rule(boolean negated, PathMatcher matcher) {
      this.negated = negated;
      this.matcher = matcher;
    }
  }

  private final Path baseDir;
  private final List<Rule> rules;

  private IgnoreMatcher(Path baseDir, List<Rule> rules) {
    this.baseDir = baseDir;
    this.rules = rules;
  }

  /**
   * Returns an empty matcher that never ignores anything.
   *
   * @param baseDir the base level directory.
   * @return empty {@code IgnoreMatcher}.
   */
  public static IgnoreMatcher empty(Path baseDir) {
    return new IgnoreMatcher(baseDir, List.of());
  }

  /**
   * Loads ignore rules from {@code ignoreFile}.
   *
   * @param ignoreFile ignore file path.
   * @return matcher.
   * @throws IOException if reading fails.
   */
  public static IgnoreMatcher fromFile(Path ignoreFile) throws IOException {
    Path absIgnore = ignoreFile.toAbsolutePath().normalize();
    Path baseDir = absIgnore.getParent();
    if (baseDir == null) {
      baseDir = absIgnore;
    }

    List<String> lines = Files.readAllLines(absIgnore);
    FileSystem fs = baseDir.getFileSystem();

    List<Rule> rs = new ArrayList<>();
    for (String raw : lines) {
      String line = raw.trim();

      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }

      boolean negated = false;
      if (line.startsWith("!")) {
        negated = true;
        line = line.substring(1).trim();
        if (line.isEmpty()) {
          continue;
        }
      }

      LinkedHashSet<String> patterns = new LinkedHashSet<>();
      if (line.startsWith("**/")) {
        patterns.add(line.substring(3)); // drop leading "**/"
      }

      // If no slash, match anywhere, i.e. "Bad.java" => "**/Bad.java"
      if (!line.contains("/")) {
        patterns.add(line);
        patterns.add("**/" + line);
      } else {
        patterns.add(line);
      }

      for (String pattern : patterns) {
        PathMatcher m = fs.getPathMatcher("glob:" + pattern);
        rs.add(new Rule(negated, m));
      }
    }

    return new IgnoreMatcher(baseDir, rs);
  }

  /**
   * Returns whether {@code file} should be ignored.
   *
   * <p>Evaluation uses "last match wins" with {@code !} negation.
   *
   * @param file the ignored file.
   * @return if {@code file} should be ignored.
   */
  public boolean isIgnored(Path file) {
    if (rules.isEmpty()) {
      return false;
    }

    Path abs = file.toAbsolutePath().normalize();

    Path rel;
    try {
      rel = baseDir.relativize(abs).normalize();
    } catch (IllegalArgumentException ex) {
      // Different roots are treated as not ignored.
      return false;
    }

    boolean ignored = false;
    for (Rule r : rules) {
      if (r.matcher.matches(rel)) {
        ignored = !r.negated;
      }
    }

    return ignored;
  }
}
