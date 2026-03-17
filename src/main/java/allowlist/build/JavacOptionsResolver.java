package allowlist.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves additional javac options needed to analyze a compilation group.
 *
 * <p>The main use is supplying build-tool-aware options such as {@code -classpath} and {@code
 * -sourcepath}.
 */
public interface JavacOptionsResolver {

  /**
   * Resolves source roots for the given project root and source set.
   *
   * <p>Examples include {@code src/main/java} and {@code src/test/java}.
   *
   * @param projectRoot compilation/project root.
   * @param sourceSetKind source set kind for the current group.
   * @return source roots that should be supplied to javac via {@code -sourcepath}; this may be
   *     empty.
   * @throws IOException if resolution fails.
   */
  List<Path> resolveSourceRoots(Path projectRoot, SourceSetKind sourceSetKind) throws IOException;

  /**
   * Resolves project output directories for the given project root and source set.
   *
   * <p>Examples include build output directories such as {@code target/classes}. These are project
   * outputs, not third-party dependency jars.
   *
   * @param projectRoot compilation/project root.
   * @param sourceSetKind source set kind for the current group.
   * @return project output directories that may be supplied to javac via {@code -classpath}; this
   *     may be empty.
   * @throws IOException if resolution fails.
   */
  List<Path> resolveOutputDirectories(Path projectRoot, SourceSetKind sourceSetKind)
      throws IOException;

  /**
   * Resolves dependency classpath entries for the given project root and source set.
   *
   * <p>These are third-party dependency jars or equivalent entries obtained from the underlying
   * build tool.
   *
   * @param projectRoot compilation/project root.
   * @param sourceSetKind source set kind for the current group.
   * @return dependency classpath entries that may be supplied to javac via {@code -classpath}; this
   *     may be empty.
   * @throws IOException if resolution fails.
   */
  List<Path> resolveDependencyClasspathEntries(Path projectRoot, SourceSetKind sourceSetKind)
      throws IOException;

  /**
   * Resolves a cheap project-local javac option set.
   *
   * <p>This includes source roots and project output directories only. It intentionally excludes
   * third-party dependency resolution so callers can use it as a fast first pass.
   *
   * @param projectRoot compilation/project root.
   * @param sourceSetKind source set kind for the current group.
   * @return javac options such as {@code -sourcepath} and {@code -classpath}; this may be empty.
   * @throws IOException if resolution fails.
   */
  default List<String> resolveLocalJavacOptions(Path projectRoot, SourceSetKind sourceSetKind)
      throws IOException {
    return buildJavacOptions(
        resolveSourceRoots(projectRoot, sourceSetKind),
        resolveOutputDirectories(projectRoot, sourceSetKind));
  }

  /**
   * Resolves the full dependency-aware javac option set.
   *
   * <p>This includes source roots, project output directories, and resolved dependency classpath
   * entries.
   *
   * @param projectRoot compilation/project root.
   * @param sourceSetKind source set kind for the current group.
   * @return full javac options such as {@code -sourcepath} and {@code -classpath}; this may be
   *     empty.
   * @throws IOException if resolution fails.
   */
  default List<String> resolveJavacOptions(Path projectRoot, SourceSetKind sourceSetKind)
      throws IOException {
    List<Path> classpathEntries = new ArrayList<>();
    classpathEntries.addAll(resolveOutputDirectories(projectRoot, sourceSetKind));
    classpathEntries.addAll(resolveDependencyClasspathEntries(projectRoot, sourceSetKind));

    return buildJavacOptions(resolveSourceRoots(projectRoot, sourceSetKind), classpathEntries);
  }

  /**
   * Builds javac options from the supplied source root and classpath entries.
   *
   * @param sourceRoots source roots for {@code -sourcepath}.
   * @param classpathEntries classpath entries for {@code -classpath}.
   * @return javac option list.
   */
  private static List<String> buildJavacOptions(
      List<Path> sourceRoots, List<Path> classpathEntries) {
    List<String> out = new ArrayList<>();

    String sourcepath = joinPaths(sourceRoots);
    if (!sourcepath.isBlank()) {
      out.add("-sourcepath");
      out.add(sourcepath);
    }

    String classpath = joinPaths(classpathEntries);
    if (!classpath.isBlank()) {
      out.add("-classpath");
      out.add(classpath);
    }

    return List.copyOf(out);
  }

  /**
   * Joins paths with platform classpath separator.
   *
   * @param paths paths to join.
   * @return joined path string, or an empty string if no non-blank paths are present.
   */
  private static String joinPaths(List<Path> paths) {
    List<String> parts = new ArrayList<>();

    for (Path path : paths) {
      if (path == null) {
        continue;
      }

      String s = path.toString();
      if (!s.isBlank()) {
        parts.add(s);
      }
    }

    return String.join(File.pathSeparator, parts);
  }
}
