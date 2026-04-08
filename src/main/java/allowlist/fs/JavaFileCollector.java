package allowlist.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects Java source files from one or more input paths.
 *
 * <p>If a target path is:
 *
 * <ul>
 *   <li>a directory, all descendant {@code .java} file are collected
 *   <li>a file ending in {@code .java}, that file is collected
 *   <li>missing or not a Java source file, it is ignored
 * </ul>
 *
 * <p>Collected paths are returned in traversal order.
 */
public final class JavaFileCollector {

  /**
   * Collects Java source files from the provided target paths.
   *
   * @param targets files or directories to scan.
   * @return collected Java source file paths.
   * @throws IOException on I/O error while walking a directory tree.
   */
  public List<Path> collect(List<Path> targets) throws IOException {
    List<Path> out = new ArrayList<>();

    for (Path target : targets) {
      collectFromPath(target, out);
    }

    return out;
  }

  /**
   * Collects all {@code .java} files beneath a path into {@code out}.
   *
   * @param path file or directory to scan.
   * @param out output list of java source file paths.
   * @throws IOException on I/O error while walking the file tree.
   */
  private static void collectFromPath(Path path, List<Path> out) throws IOException {
    if (!Files.exists(path)) {
      return;
    }

    if (Files.isDirectory(path)) {
      try (var stream = Files.walk(path)) {
        stream.forEach(
            p -> {
              if (isJavaSourceFile(p)) {
                out.add(p);
              }
            });
      }
      return;
    }

    if (isJavaSourceFile(path)) {
      out.add(path);
    }
  }

  /**
   * Returns whether a path looks like a Java source file by filename.
   *
   * @param path candidate path.
   * @return true if the path ends with {@code .java}.
   */
  private static boolean isJavaSourceFile(Path path) {
    return path != null && path.toString().endsWith(".java");
  }
}
