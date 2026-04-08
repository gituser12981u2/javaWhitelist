package allowlist.build;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class CompilationGrouperTest {
  @TempDir Path tempDir;

  @Test
  public void groupsFilesUnderSameProjectRootTogether() throws Exception {
    Path root = tempDir.resolve("repo");
    Files.createDirectories(root);
    Files.writeString(root.resolve("pom.xml"), "<project></project>");

    Path a = root.resolve("src").resolve("misc").resolve("A.java");
    Path b = root.resolve("src").resolve("misc").resolve("B.java");
    Files.createDirectories(a.getParent());
    Files.writeString(a, "class A {}");
    Files.writeString(b, "class B {}");

    CompilationGrouper grouper = new DefaultCompilationGrouper();
    List<CompilationGroup> groups = grouper.group(List.of(a, b));

    assertEquals(1, groups.size());

    CompilationGroup group = groups.get(0);
    assertEquals(root.toAbsolutePath().normalize(), group.projectRoot());
    assertEquals(SourceSetKind.UNKNOWN, group.sourceSetKind());
    assertEquals(
        List.of(a.toAbsolutePath().normalize(), b.toAbsolutePath().normalize()), group.files());
  }

  @Test
  public void createsSeparateGroupsForDifferentProjectRoots() throws Exception {
    Path rootA = tempDir.resolve("repo-a");
    Path rootB = tempDir.resolve("repo-b");
    Files.createDirectories(rootA);
    Files.createDirectories(rootB);
    Files.writeString(rootA.resolve("pom.xml"), "<project></project>");
    Files.writeString(rootB.resolve("pom.xml"), "<project></project>");

    Path a = rootA.resolve("A.java");
    Path b = rootB.resolve("B.java");
    Files.writeString(a, "class A {}");
    Files.writeString(b, "class B {}");

    CompilationGrouper grouper = new DefaultCompilationGrouper();
    List<CompilationGroup> groups = grouper.group(List.of(a, b));

    assertEquals(2, groups.size());

    assertEquals(rootA.toAbsolutePath().normalize(), groups.get(0).projectRoot());
    assertEquals(List.of(a.toAbsolutePath().normalize()), groups.get(0).files());

    assertEquals(rootB.toAbsolutePath().normalize(), groups.get(1).projectRoot());
    assertEquals(List.of(b.toAbsolutePath().normalize()), groups.get(1).files());
  }

  @Test
  public void nestedProjectUsesNearestProjectRoot() throws Exception {
    Path outer = tempDir.resolve("outer");
    Path inner = outer.resolve("inner");
    Files.createDirectories(inner);

    Files.writeString(outer.resolve("pom.xml"), "<project></project>");
    Files.writeString(inner.resolve("pom.xml"), "<project></project>");

    Path file = inner.resolve("src").resolve("other").resolve("Thing.java");
    Files.createDirectories(file.getParent());
    Files.writeString(file, "class Thing {}");

    CompilationGrouper grouper = new DefaultCompilationGrouper();
    List<CompilationGroup> groups = grouper.group(List.of(file));

    assertEquals(1, groups.size());
    assertEquals(inner.toAbsolutePath().normalize(), groups.get(0).projectRoot());
    assertEquals(SourceSetKind.UNKNOWN, groups.get(0).sourceSetKind());
    assertEquals(List.of(file.toAbsolutePath().normalize()), groups.get(0).files());
  }

  @Test
  public void fileWithoutProjectMarkerFallsBackToParentDirectory() throws Exception {
    Path dir = tempDir.resolve("loose");
    Files.createDirectories(dir);

    Path file = dir.resolve("Loose.java");
    Files.writeString(file, "class Loose {}");

    CompilationGrouper grouper = new DefaultCompilationGrouper();
    List<CompilationGroup> groups = grouper.group(List.of(file));

    assertEquals(1, groups.size());
    assertEquals(dir.toAbsolutePath().normalize(), groups.get(0).projectRoot());
    assertEquals(SourceSetKind.UNKNOWN, groups.get(0).sourceSetKind());
    assertEquals(List.of(file.toAbsolutePath().normalize()), groups.get(0).files());
  }

  @Test
  public void groupsRawJavaFilesWithoutProjectMarker() throws Exception {
    Path dir = tempDir.resolve("raw");
    Files.createDirectories(dir);

    Path a = dir.resolve("A.java");
    Path b = dir.resolve("B.java");

    Files.writeString(a, "class A {}");
    Files.writeString(b, "class B {}");

    CompilationGrouper grouper = new DefaultCompilationGrouper();
    List<CompilationGroup> groups = grouper.group(List.of(a, b));

    assertEquals(1, groups.size());

    CompilationGroup group = groups.get(0);

    assertEquals(dir.toAbsolutePath().normalize(), group.projectRoot());
    assertEquals(SourceSetKind.UNKNOWN, group.sourceSetKind());

    assertEquals(
        List.of(a.toAbsolutePath().normalize(), b.toAbsolutePath().normalize()), group.files());
  }

  @Test
  public void rawFilesInDifferentDirectoriesProduceSeparateGroups() throws Exception {
    Path dirA = tempDir.resolve("dirA");
    Path dirB = tempDir.resolve("dirB");

    Files.createDirectories(dirA);
    Files.createDirectories(dirB);

    Path a = dirA.resolve("A.java");
    Path b = dirB.resolve("B.java");

    Files.writeString(a, "class A {}");
    Files.writeString(b, "class B {}");

    CompilationGrouper grouper = new DefaultCompilationGrouper();
    List<CompilationGroup> groups = grouper.group(List.of(a, b));

    assertEquals(2, groups.size());

    CompilationGroup g1 = groups.get(0);
    CompilationGroup g2 = groups.get(1);

    assertEquals(SourceSetKind.UNKNOWN, g1.sourceSetKind());
    assertEquals(SourceSetKind.UNKNOWN, g2.sourceSetKind());

    assertEquals(dirA.toAbsolutePath().normalize(), g1.projectRoot());
    assertEquals(dirB.toAbsolutePath().normalize(), g2.projectRoot());
  }
}
