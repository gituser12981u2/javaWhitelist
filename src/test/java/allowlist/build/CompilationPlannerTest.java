package allowlist.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CompilationPlannerTest {

  @TempDir Path tempDir;

  @Test
  void noExternalImports_usesOnlyLocalJavacOptions() throws IOException {
    Path file =
        write(
            "src/main/java/example/App.java",
            """
                        package example;

                        import java.util.List;

                        public class App {}
                        """);

    CompilationGroup group = new CompilationGroup(tempDir, SourceSetKind.MAIN, List.of(file));

    CountingResolver resolver =
        new CountingResolver(
            List.of("-sourcepath", "LOCAL_SRC"),
            List.of("-sourcepath", "FULL_SRC", "-classpath", "FULL_CP"));

    CompilationPlanner planner =
        new CompilationPlanner(new StubCompilationGrouper(List.of(group)), resolver);

    List<CompilationPlan> plans = planner.plan(List.of(file));

    assertEquals(1, plans.size());
    assertEquals(1, resolver.localCalls);
    assertEquals(0, resolver.fullCalls);

    List<String> options = plans.get(0).javacOptions();
    assertIterableEquals(List.of("-sourcepath", "LOCAL_SRC", "-proc:none"), options);
  }

  @Test
  void externalImports_useFullJavacOptions() throws IOException {
    Path file =
        write(
            "src/test/java/example/AppTest.java",
            """
                        package example;

                        import org.junit.jupiter.api.Test;

                        class AppTest {}
                        """);

    CompilationGroup group = new CompilationGroup(tempDir, SourceSetKind.TEST, List.of(file));

    CountingResolver resolver =
        new CountingResolver(
            List.of("-sourcepath", "LOCAL_SRC"),
            List.of("-sourcepath", "FULL_SRC", "-classpath", "FULL_CP"));

    CompilationPlanner planner =
        new CompilationPlanner(new StubCompilationGrouper(List.of(group)), resolver);

    List<CompilationPlan> plans = planner.plan(List.of(file));

    assertEquals(1, plans.size());
    assertEquals(1, resolver.localCalls);
    assertEquals(1, resolver.fullCalls);

    List<String> options = plans.get(0).javacOptions();
    assertIterableEquals(
        List.of("-sourcepath", "FULL_SRC", "-classpath", "FULL_CP", "-proc:none"), options);
  }

  @Test
  void crossGroupProjectLocalImport_usesOnlyLocalJavacOptions() throws IOException {
    Path main =
        write(
            "src/main/java/example/core/App.java",
            """
                        package example.core;

                        public class App {}
                        """);

    Path test =
        write(
            "src/test/java/example/tests/AppTest.java",
            """
                        package example.tests;

                        import example.core.App;

                        class AppTest {}
                        """);

    CompilationGroup mainGroup = new CompilationGroup(tempDir, SourceSetKind.MAIN, List.of(main));
    CompilationGroup testGroup = new CompilationGroup(tempDir, SourceSetKind.TEST, List.of(test));

    CountingResolver resolver =
        new CountingResolver(
            List.of("-sourcepath", "LOCAL_SRC"),
            List.of("-sourcepath", "FULL_SRC", "-classpath", "FULL_CP"));

    CompilationPlanner planner =
        new CompilationPlanner(new StubCompilationGrouper(List.of(mainGroup, testGroup)), resolver);

    List<CompilationPlan> plans = planner.plan(List.of(main, test));

    assertEquals(2, plans.size());
    assertEquals(2, resolver.localCalls);
    assertEquals(0, resolver.fullCalls);

    for (CompilationPlan plan : plans) {
      assertTrue(plan.javacOptions().contains("-proc:none"));
      assertTrue(plan.javacOptions().contains("LOCAL_SRC"));
    }
  }

  @Test
  void mixedGroups_onlyExternalGroupTriggersFullResolution() throws IOException {
    Path main =
        write(
            "src/main/java/example/App.java",
            """
                        package example;

                        import java.util.List;

                        public class App {}
                        """);

    Path test =
        write(
            "src/test/java/example/AppTest.java",
            """
                        package example;

                        import org.junit.jupiter.api.Test;

                        class AppTest {}
                        """);

    CompilationGroup mainGroup = new CompilationGroup(tempDir, SourceSetKind.MAIN, List.of(main));
    CompilationGroup testGroup = new CompilationGroup(tempDir, SourceSetKind.TEST, List.of(test));

    CountingResolver resolver =
        new CountingResolver(
            List.of("-sourcepath", "LOCAL_SRC"),
            List.of("-sourcepath", "FULL_SRC", "-classpath", "FULL_CP"));

    CompilationPlanner planner =
        new CompilationPlanner(new StubCompilationGrouper(List.of(mainGroup, testGroup)), resolver);

    List<CompilationPlan> plans = planner.plan(List.of(main, test));

    assertEquals(2, plans.size());
    assertEquals(2, resolver.localCalls);
    assertEquals(1, resolver.fullCalls);

    CompilationPlan mainPlan = plans.get(0);
    CompilationPlan testPlan = plans.get(1);

    assertIterableEquals(
        List.of("-sourcepath", "LOCAL_SRC", "-proc:none"), mainPlan.javacOptions());

    assertIterableEquals(
        List.of("-sourcepath", "FULL_SRC", "-classpath", "FULL_CP", "-proc:none"),
        testPlan.javacOptions());
  }

  @Test
  void fullyQualifiedThirdPartyUsageWithoutImport_staysOnLocalPathAtPlannerLevel()
      throws IOException {
    Path file =
        write(
            "src/test/java/example/AppTest.java",
            """
                        package example;

                        class AppTest {
                        org.junit.jupiter.api.Test t;
                        }
                        """);

    CompilationGroup group = new CompilationGroup(tempDir, SourceSetKind.TEST, List.of(file));

    CountingResolver resolver =
        new CountingResolver(
            List.of("-sourcepath", "LOCAL_SRC"),
            List.of("-sourcepath", "FULL_SRC", "-classpath", "FULL_CP"));

    CompilationPlanner planner =
        new CompilationPlanner(new StubCompilationGrouper(List.of(group)), resolver);

    List<CompilationPlan> plans = planner.plan(List.of(file));

    assertEquals(1, plans.size());
    assertEquals(1, resolver.localCalls);
    assertEquals(0, resolver.fullCalls);

    assertIterableEquals(
        List.of("-sourcepath", "LOCAL_SRC", "-proc:none"), plans.get(0).javacOptions());
  }

  @Test
  void resolveFallbackJavacOptions_usesFullResolverAndAddsProcNone() throws IOException {
    CountingResolver resolver =
        new CountingResolver(
            List.of("-sourcepath", "LOCAL_SRC"),
            List.of("-sourcepath", "FULL_SRC", "-classpath", "FULL_CP"));

    CompilationPlanner planner =
        new CompilationPlanner(new StubCompilationGrouper(List.of()), resolver);

    CompilationPlan plan =
        new CompilationPlan(
            tempDir,
            SourceSetKind.TEST,
            List.of(),
            List.of("-sourcepath", "LOCAL_SRC", "-proc:none"));

    List<String> fallback = planner.resolveFallbackJavacOptions(plan);

    assertEquals(0, resolver.localCalls);
    assertEquals(1, resolver.fullCalls);

    assertIterableEquals(
        List.of("-sourcepath", "FULL_SRC", "-classpath", "FULL_CP", "-proc:none"), fallback);
  }

  private Path write(String relative, String content) throws IOException {
    Path path = tempDir.resolve(relative);
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
    return path;
  }

  private static final class CountingResolver implements JavacOptionsResolver {
    private final List<String> localOptions;
    private final List<String> fullOptions;

    private int localCalls = 0;
    private int fullCalls = 0;

    private CountingResolver(List<String> localOptions, List<String> fullOptions) {
      this.localOptions = List.copyOf(localOptions);
      this.fullOptions = List.copyOf(fullOptions);
    }

    @Override
    public List<Path> resolveSourceRoots(Path projectRoot, SourceSetKind sourceSetKind) {
      return List.of();
    }

    @Override
    public List<Path> resolveOutputDirectories(Path projectRoot, SourceSetKind sourceSetKind) {
      return List.of();
    }

    @Override
    public List<Path> resolveDependencyClasspathEntries(
        Path projectRoot, SourceSetKind sourceSetKind) {
      return List.of();
    }

    @Override
    public List<String> resolveLocalJavacOptions(Path projectRoot, SourceSetKind sourceSetKind) {
      localCalls++;
      return new ArrayList<>(localOptions);
    }

    @Override
    public List<String> resolveJavacOptions(Path projectRoot, SourceSetKind sourceSetKind) {
      fullCalls++;
      return new ArrayList<>(fullOptions);
    }
  }

  private static final class StubCompilationGrouper implements CompilationGrouper {
    private final List<CompilationGroup> groups;

    private StubCompilationGrouper(List<CompilationGroup> groups) {
      this.groups = groups;
    }

    @Override
    public List<CompilationGroup> group(List<Path> javaFiles) {
      return groups;
    }
  }
}
