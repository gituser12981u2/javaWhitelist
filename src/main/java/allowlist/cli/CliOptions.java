package allowlist.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Parsed CLI options for a normal checker run. */
public final class CliOptions {
  private final String allowlistPath;
  private final String ignorePath;
  private final List<Path> targetPaths;

  private CliOptions(String allowlistPath, String ignorePath, List<Path> targetPaths) {
    this.allowlistPath = allowlistPath;
    this.ignorePath = ignorePath;
    this.targetPaths = List.copyOf(targetPaths);
  }

  public String allowlistPath() {
    return allowlistPath;
  }

  public String ignorePath() {
    return ignorePath;
  }

  public List<Path> targetPaths() {
    return targetPaths;
  }

  /**
   * Parses run-mode CLI options.
   *
   * @param args raw CLI args
   * @return parsed run options
   * @throws IllegalArgumentException if the arguments are invalid
   */
  public static CliOptions parse(String[] args) {
    String allowlistPath = null;
    String ignorePath = null;
    List<Path> targetPaths = new ArrayList<>();

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--allowlist")) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("Missing value for --allowlist.");
        }

        allowlistPath = args[++i];
      } else if (args[i].equals("--ignore")) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("Missing value for --ignore.");
        }

        ignorePath = args[++i];
      } else {
        targetPaths.add(Paths.get(args[i]));
      }
    }

    if (targetPaths.isEmpty()) {
      throw new IllegalArgumentException("No target paths provided.");
    }

    return new CliOptions(allowlistPath, ignorePath, targetPaths);
  }
}
