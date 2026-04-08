package allowlist.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ImportDependencyHeuristicTest {

  @TempDir Path tempDir;

  private final ImportDependencyHeuristic heuristic = new ImportDependencyHeuristic();

  @Test
  void javaImports_only_doNotNeedExternalClasspath() throws IOException {
    Path file =
        write(
            "src/main/java/example/App.java",
            """
            package example;

            import java.util.List;
            import java.util.Map;

            public class App {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(file));

    assertFalse(heuristic.needsExternalClasspath(List.of(file), projectPackages));
  }

  @Test
  void javaxImports_only_doNotNeedExternalClasspath() throws IOException {
    Path file =
        write(
            "src/main/java/example/App.java",
            """
            package example;

            import javax.crypto.Cipher;

            public class App {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(file));

    assertFalse(heuristic.needsExternalClasspath(List.of(file), projectPackages));
  }

  @Test
  void jakartaImports_only_doNotNeedExternalClasspath() throws IOException {
    Path file =
        write(
            "src/main/java/example/App.java",
            """
            package example;

            import jakarta.persistence.Entity;

            public class App {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(file));

    assertFalse(heuristic.needsExternalClasspath(List.of(file), projectPackages));
  }

  @Test
  void thirdPartyImport_needsExternalClasspath() throws IOException {
    Path file =
        write(
            "src/test/java/example/AppTest.java",
            """
            package example;

            import org.junit.jupiter.api.Test;

            class AppTest {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(file));

    assertTrue(heuristic.needsExternalClasspath(List.of(file), projectPackages));
  }

  @Test
  void thirdPartyStaticImport_needsExternalClasspath() throws IOException {
    Path file =
        write(
            "src/test/java/example/AppTest.java",
            """
            package example;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            class AppTest {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(file));

    assertTrue(heuristic.needsExternalClasspath(List.of(file), projectPackages));
  }

  @Test
  void projectLocalImport_samePackageSet_doesNotNeedExternalClasspath() throws IOException {
    Path main =
        write(
            "src/main/java/example/core/App.java",
            """
            package example.core;

            public class App {}
            """);

    Path other =
        write(
            "src/main/java/example/consumer/UseApp.java",
            """
            package example.consumer;

            import example.core.App;

            public class UseApp {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(main, other));

    assertFalse(heuristic.needsExternalClasspath(List.of(other), projectPackages));
  }

  @Test
  void projectLocalWildcardImport_doesNotNeedExternalClasspath() throws IOException {
    Path main =
        write(
            "src/main/java/example/core/App.java",
            """
            package example.core;

            public class App {}
            """);

    Path other =
        write(
            "src/main/java/example/consumer/UseApp.java",
            """
            package example.consumer;

            import example.core.*;

            public class UseApp {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(main, other));

    assertFalse(heuristic.needsExternalClasspath(List.of(other), projectPackages));
  }

  @Test
  void crossGroupProjectLocalImport_doesNotNeedExternalClasspath_whenUsingGlobalPackageIndex()
      throws IOException {
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

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(main, test));

    assertFalse(heuristic.needsExternalClasspath(List.of(test), projectPackages));
  }

  @Test
  void blockCommentedFakeImport_isIgnored() throws IOException {
    Path file =
        write(
            "src/main/java/example/App.java",
            """
            package example;

            /*
             * import org.junit.jupiter.api.Test;
             */

            import java.util.List;

            public class App {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(file));

    assertFalse(heuristic.needsExternalClasspath(List.of(file), projectPackages));
  }

  @Test
  void lineCommentedFakeImport_isIgnored() throws IOException {
    Path file =
        write(
            "src/main/java/example/App.java",
            """
            package example;

            // import org.junit.jupiter.api.Test;
            import java.util.List;

            public class App {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(file));

    assertFalse(heuristic.needsExternalClasspath(List.of(file), projectPackages));
  }

  @Test
  void realImportAfterBlockComment_isDetected() throws IOException {
    Path file =
        write(
            "src/test/java/example/AppTest.java",
            """
            package example;

            /*
             * comment
             */
            import org.junit.jupiter.api.Test;

            class AppTest {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(file));

    assertTrue(heuristic.needsExternalClasspath(List.of(file), projectPackages));
  }

  @Test
  void defaultPackageFileWithOnlyJavaImport_doesNotNeedExternalClasspath() throws IOException {
    Path file =
        write(
            "src/main/java/App.java",
            """
            import java.util.List;

            public class App {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(file));

    assertFalse(heuristic.needsExternalClasspath(List.of(file), projectPackages));
  }

  @Test
  void packagePrefixBugRegression_javascriptIsNotJava() throws IOException {
    Path file =
        write(
            "src/main/java/example/App.java",
            """
            package example;

            import javascript.foo.Bar;

            public class App {}
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(file));

    assertTrue(heuristic.needsExternalClasspath(List.of(file), projectPackages));
  }

  @Test
  void fullyQualifiedThirdPartyUsageWithoutImport_isNotDetectedByHeuristic() throws IOException {
    Path file =
        write(
            "src/test/java/example/AppTest.java",
            """
            package example;

            class AppTest {
              org.junit.jupiter.api.Test t;
            }
            """);

    Set<String> projectPackages = heuristic.collectDeclaredPackages(List.of(file));

    assertFalse(heuristic.needsExternalClasspath(List.of(file), projectPackages));
  }

  @Test
  void compilationGroupOverload_usesOwnPackageCollection() throws IOException {
    Path file =
        write(
            "src/test/java/example/AppTest.java",
            """
            package example;

            import org.junit.jupiter.api.Test;

            class AppTest {}
            """);

    CompilationGroup group = new CompilationGroup(tempDir, SourceSetKind.TEST, List.of(file));

    assertTrue(heuristic.needsExternalClasspath(group));
  }

  private Path write(String relative, String content) throws IOException {
    Path path = tempDir.resolve(relative);
    Files.createDirectories(path.getParent());
    Files.writeString(path, content);
    return path;
  }
}
