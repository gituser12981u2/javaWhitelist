package allowlist.build;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Group of Java source files that are to be analyzed together.
 *
 * <p>Groups are formed by project root and logical source set so that a single allowlist and javac
 * option set can be applied consistently to the entire group.
 */
public final class CompilationGroup {
  private final Path projectRoot;
  private final SourceSetKind sourceSetKind;
  private final List<Path> files;

  /**
   * Creates a compilation group.
   *
   * @param projectRoot project root for the group.
   * @param sourceSetKind logical source set for the group.
   * @param files files belonging to the group
   */
  public CompilationGroup(Path projectRoot, SourceSetKind sourceSetKind, List<Path> files) {
    this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
    this.sourceSetKind = Objects.requireNonNull(sourceSetKind, "sourceSetKind");
    this.files = List.copyOf(files);
  }

  public Path projectRoot() {
    return projectRoot;
  }

  public SourceSetKind sourceSetKind() {
    return sourceSetKind;
  }

  public List<Path> files() {
    return files;
  }
}
