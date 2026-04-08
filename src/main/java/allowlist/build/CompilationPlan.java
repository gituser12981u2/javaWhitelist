package allowlist.build;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Prepared compilation unit for analysis.
 *
 * <p>A compilation plan combines:
 *
 * <ul>
 *   <li>project root
 *   <li>source set kind
 *   <li>source files
 *   <li>resolved javac options
 * </ul>
 */
public final class CompilationPlan {
  private final Path projectRoot;
  private final SourceSetKind sourceSetKind;
  private final List<Path> files;
  private final List<String> javacOptions;

  /**
   * Creates a compilation plan.
   *
   * @param projectRoot project root for the plan.
   * @param sourceSetKind logical source set for the plan.
   * @param files source files belonging to the plan.
   * @param javacOptions resolved javac options for the plan.
   */
  public CompilationPlan(
      Path projectRoot, SourceSetKind sourceSetKind, List<Path> files, List<String> javacOptions) {
    this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
    this.sourceSetKind = Objects.requireNonNull(sourceSetKind, "sourceSetKind");
    this.files = List.copyOf(files);
    this.javacOptions = List.copyOf(javacOptions);
  }

  /**
   * Returns the project root for this plan.
   *
   * @return project root path.
   */
  public Path projectRoot() {
    return projectRoot;
  }

  /**
   * Returns the source set kind for this plan.
   *
   * @return source set kind.
   */
  public SourceSetKind sourceSetKind() {
    return sourceSetKind;
  }

  /**
   * Returns the source files belonging to this plan.
   *
   * @return immutable source file list.
   */
  public List<Path> files() {
    return files;
  }

  /**
   * Returns the resolved javac options for this plan.
   *
   * @return immutable javac options list.
   */
  public List<String> javacOptions() {
    return javacOptions;
  }
}
