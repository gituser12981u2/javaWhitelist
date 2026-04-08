package allowlist.build;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CompilationPlanner {
  private final CompilationGrouper compilationGrouper;
  private final JavacOptionsResolver javacOptionsResolver;
  private final ImportDependencyHeuristic importDependencyHeuristic;

  public CompilationPlanner(
      CompilationGrouper compilationGrouper, JavacOptionsResolver javacOptionsResolver) {
    this(compilationGrouper, javacOptionsResolver, new ImportDependencyHeuristic());
  }

  public CompilationPlanner(
      CompilationGrouper compilationGrouper,
      JavacOptionsResolver javacOptionsResolver,
      ImportDependencyHeuristic importDependencyHeuristic) {
    this.compilationGrouper = Objects.requireNonNull(compilationGrouper, "compilationGrouper");
    this.javacOptionsResolver =
        Objects.requireNonNull(javacOptionsResolver, "javacOptionsResolver");
    this.importDependencyHeuristic =
        Objects.requireNonNull(importDependencyHeuristic, "importDependencyHeuristic");
  }

  public List<CompilationPlan> plan(List<Path> javaFiles) throws IOException {
    List<CompilationGroup> groups = compilationGrouper.group(javaFiles);
    List<CompilationPlan> plans = new ArrayList<>();

    Set<String> projectPackages = importDependencyHeuristic.collectDeclaredPackages(javaFiles);

    for (CompilationGroup group : groups) {
      List<String> javacOptions =
          new ArrayList<>(
              javacOptionsResolver.resolveLocalJavacOptions(
                  group.projectRoot(), group.sourceSetKind()));

      if (importDependencyHeuristic.needsExternalClasspath(group.files(), projectPackages)) {
        javacOptions =
            new ArrayList<>(
                javacOptionsResolver.resolveJavacOptions(
                    group.projectRoot(), group.sourceSetKind()));
      }
      javacOptions.add("-proc:none");

      plans.add(
          new CompilationPlan(
              group.projectRoot(),
              group.sourceSetKind(),
              group.files(),
              List.copyOf(javacOptions)));
    }

    return plans;
  }

  public List<String> resolveFallbackJavacOptions(CompilationPlan plan) throws IOException {
    List<String> javacOptions =
        new ArrayList<>(
            javacOptionsResolver.resolveJavacOptions(plan.projectRoot(), plan.sourceSetKind()));
    javacOptions.add("-proc:none");
    return List.copyOf(javacOptions);
  }
}
