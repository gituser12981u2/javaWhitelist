package allowlist.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default grouping strategy for Java source files.
 *
 * <p>Files are grouped by:
 *
 * <ol>
 *   <li>nearest project root
 *   <li>logical source set
 * </ol>
 *
 * <p>The nearest project root is currently determined by the neartest ancestor containing a {@code
 * pom.xml} file. This will change in the future to allow gradle. If no {@code pom.xml} is found,
 * the file's parent directory is used.
 *
 * <p>Source sets are detected by conventional path layout:
 *
 * <ul>
 *   <li>{@code src/main/java} -> {@link SourceSetKind#MAIN}
 *   <li>{@code src/test/java} -> {@link SourceSetKind#TEST}
 *   <li>otherwise -> {@link SourceSetKind#UNKNOWN}
 * </ul>
 */
public final class DefaultCompilationGrouper implements CompilationGrouper {

  /**
   * Groups Java source files by their nearest Maven root.
   *
   * <p>The root for each file is the nearest ancestor directory containing a {@code pom.xml} file.
   * If no {@code pom.xml} is found, the file's parent directory is used.
   *
   * @param javaFiles collected Java source files.
   * @return map from compilation root to the files belonging to that group.
   */
  @Override
  public List<CompilationGroup> group(List<Path> javaFiles) {
    record GroupKey(Path projectRoot, SourceSetKind sourceSetKind) {}

    Map<GroupKey, List<Path>> grouped = new LinkedHashMap<>();

    for (Path javaFile : javaFiles) {
      Path abs = javaFile.toAbsolutePath().normalize();
      Path root = findNearestPomRoot(abs);
      SourceSetKind sourceSetKind = detectSourceSet(abs);

      GroupKey key = new GroupKey(root, sourceSetKind);
      grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(abs);
    }

    List<CompilationGroup> out = new ArrayList<>();
    for (Map.Entry<GroupKey, List<Path>> entry : grouped.entrySet()) {
      out.add(
          new CompilationGroup(
              entry.getKey().projectRoot(), entry.getKey().sourceSetKind(), entry.getValue()));
    }

    return out;
  }

  /**
   * Detects the logical source set for a file using conventional Maven/Gradle path layout.
   *
   * @param file Java source file path.
   * @return detected source set kind.
   */
  private static SourceSetKind detectSourceSet(Path file) {
    String norm = file.toAbsolutePath().normalize().toString().replace('\\', '/');

    if (norm.contains("/src/test/java/")) {
      return SourceSetKind.TEST;
    }

    if (norm.contains("/src/main/java/")) {
      return SourceSetKind.MAIN;
    }

    return SourceSetKind.UNKNOWN;
  }

  /**
   * Finds the nearest ancestor directory (including the file's parent chain) that contains a
   * pom.xml file.
   *
   * <p>If no pom.xml file is found, fallback to the file's immediate parent directory.
   *
   * @param file Java source file path.
   * @return compilation group root for the file.
   */
  private static Path findNearestPomRoot(Path file) {
    Path cur = file.toAbsolutePath().normalize();
    if (!Files.isDirectory(cur)) {
      cur = cur.getParent();
    }

    while (cur != null) {
      if (Files.exists(cur.resolve("pom.xml")) && Files.isRegularFile(cur.resolve("pom.xml"))) {
        return cur;
      }
      cur = cur.getParent();
    }

    return file.toAbsolutePath().normalize().getParent();
  }
}
