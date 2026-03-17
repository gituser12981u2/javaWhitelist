package allowlist.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class DefaultCompilationGrouperMavenTest {

  @TempDir Path tempDir;

  @Test
  public void detectsMainSourceSet() throws Exception {
    Path root = tempDir.resolve("repo");
    Files.createDirectories(root);
    Files.writeString(root.resolve("pom.xml"), "<project></project>");

    Path mainFile = root.resolve("src").resolve("main").resolve("java").resolve("App.java");
    Files.createDirectories(mainFile.getParent());
    Files.writeString(mainFile, "class App {}");

    CompilationGrouper grouper = new DefaultCompilationGrouper();
    List<CompilationGroup> groups = grouper.group(List.of(mainFile));

    assertEquals(1, groups.size());
    assertEquals(root.toAbsolutePath().normalize(), groups.get(0).projectRoot());
    assertEquals(SourceSetKind.MAIN, groups.get(0).sourceSetKind());
    assertEquals(List.of(mainFile.toAbsolutePath().normalize()), groups.get(0).files());
  }

  @Test
  public void detectsTestSourceSet() throws Exception {
    Path root = tempDir.resolve("repo");
    Files.createDirectories(root);
    Files.writeString(root.resolve("pom.xml"), "<project></project>");

    Path testFile = root.resolve("src").resolve("test").resolve("java").resolve("AppTest.java");
    Files.createDirectories(testFile.getParent());
    Files.writeString(testFile, "class AppTest {}");

    CompilationGrouper grouper = new DefaultCompilationGrouper();
    List<CompilationGroup> groups = grouper.group(List.of(testFile));

    assertEquals(1, groups.size());
    assertEquals(root.toAbsolutePath().normalize(), groups.get(0).projectRoot());
    assertEquals(SourceSetKind.TEST, groups.get(0).sourceSetKind());
    assertEquals(List.of(testFile.toAbsolutePath().normalize()), groups.get(0).files());
  }

  @Test
  public void splitsMainAndTestIntoSeparateGroupsWithinSameProject() throws Exception {
    Path root = tempDir.resolve("repo");
    Files.createDirectories(root);
    Files.writeString(root.resolve("pom.xml"), "<project></project>");

    Path mainFile = root.resolve("src").resolve("main").resolve("java").resolve("App.java");
    Path testFile = root.resolve("src").resolve("test").resolve("java").resolve("AppTest.java");

    Files.createDirectories(mainFile.getParent());
    Files.createDirectories(testFile.getParent());

    Files.writeString(mainFile, "class App {}");
    Files.writeString(testFile, "class AppTest {}");

    CompilationGrouper grouper = new DefaultCompilationGrouper();
    List<CompilationGroup> groups = grouper.group(List.of(mainFile, testFile));

    assertEquals(2, groups.size());

    CompilationGroup mainGroup =
        groups.stream()
            .filter(g -> g.sourceSetKind() == SourceSetKind.MAIN)
            .findFirst()
            .orElseThrow();
    CompilationGroup testGroup =
        groups.stream()
            .filter(g -> g.sourceSetKind() == SourceSetKind.TEST)
            .findFirst()
            .orElseThrow();

    assertEquals(root.toAbsolutePath().normalize(), mainGroup.projectRoot());
    assertEquals(SourceSetKind.MAIN, mainGroup.sourceSetKind());
    assertEquals(List.of(mainFile.toAbsolutePath().normalize()), mainGroup.files());

    assertEquals(root.toAbsolutePath().normalize(), testGroup.projectRoot());
    assertEquals(SourceSetKind.TEST, testGroup.sourceSetKind());
    assertEquals(List.of(testFile.toAbsolutePath().normalize()), testGroup.files());
  }

  @Test
  public void keepsMultipleMainFilesInOneMainGroup() throws Exception {
    Path root = tempDir.resolve("repo");
    Files.createDirectories(root);
    Files.writeString(root.resolve("pom.xml"), "<project></project>");

    Path a = root.resolve("src").resolve("main").resolve("java").resolve("A.java");
    Path b = root.resolve("src").resolve("main").resolve("java").resolve("B.java");

    Files.createDirectories(a.getParent());
    Files.writeString(a, "class A {}");
    Files.writeString(b, "class B {}");

    CompilationGrouper grouper = new DefaultCompilationGrouper();
    List<CompilationGroup> groups = grouper.group(List.of(a, b));

    assertEquals(1, groups.size());
    assertEquals(SourceSetKind.MAIN, groups.get(0).sourceSetKind());
    assertEquals(
        List.of(a.toAbsolutePath().normalize(), b.toAbsolutePath().normalize()),
        groups.get(0).files());
  }

  @Test
  public void keepsMultipleTestFilesInOneTestGroup() throws Exception {
    Path root = tempDir.resolve("repo");
    Files.createDirectories(root);
    Files.writeString(root.resolve("pom.xml"), "<project></project>");

    Path a = root.resolve("src").resolve("test").resolve("java").resolve("ATest.java");
    Path b = root.resolve("src").resolve("test").resolve("java").resolve("BTest.java");

    Files.createDirectories(a.getParent());
    Files.writeString(a, "class ATest {}");
    Files.writeString(b, "class BTest {}");

    CompilationGrouper grouper = new DefaultCompilationGrouper();
    List<CompilationGroup> groups = grouper.group(List.of(a, b));

    assertEquals(1, groups.size());
    assertEquals(SourceSetKind.TEST, groups.get(0).sourceSetKind());
    assertEquals(
        List.of(a.toAbsolutePath().normalize(), b.toAbsolutePath().normalize()),
        groups.get(0).files());
  }
}
