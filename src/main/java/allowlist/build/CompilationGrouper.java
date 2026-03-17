package allowlist.build;

import java.nio.file.Path;
import java.util.List;

/** Groups Java source files into compilation units. */
public interface CompilationGrouper {

  /**
   * Groups Java source files into compilation groups.
   *
   * @param javaFiles Java source files to group.
   * @return ordered compilation groups.
   */
  List<CompilationGroup> group(List<Path> javaFiles);
}
