package allowlist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class IgnoreMatcherTest {
  @TempDir Path tmp;

  private static Path write(Path dir, String name, String contents) throws Exception {
    Path p = dir.resolve(name);
    Files.createDirectories(p.getParent());
    Files.writeString(p, contents);
    return p;
  }

  private IgnoreMatcher matcherFrom(String ignoreContents) throws Exception {
    Path ignore = write(tmp, ".javawhitelistignore", ignoreContents);
    return IgnoreMatcher.fromFile(ignore);
  }

  @Test
  void exactFilenameMatchesRootAndRecursive() throws Exception {
    IgnoreMatcher m = matcherFrom("Bad.java\n");

    Path rootBad = write(tmp, "Bad.java", "class Bad {}");
    Path nestedBad = write(tmp, "a/b/Bad.java", "class Bad {}");
    Path other = write(tmp, "Good.java", "class Good {}");

    assertTrue(m.isIgnored(rootBad));
    assertTrue(m.isIgnored(nestedBad));
    assertFalse(m.isIgnored(other));
  }

  @Test
  void starGlobMatchesExtension() throws Exception {
    IgnoreMatcher m = matcherFrom("**/*.java\n");

    Path a = write(tmp, "A.java", "class A {}");
    Path b = write(tmp, "x/y/B.java", "class B {}");
    Path c = write(tmp, "x/y/readme.txt", "hi");

    assertTrue(m.isIgnored(a));
    assertTrue(m.isIgnored(b));
    assertFalse(m.isIgnored(c));
  }

  @Test
  void recursiveGlobMatchesDeepPaths() throws Exception {
    IgnoreMatcher m = matcherFrom("src/**\n");

    Path inSrc = write(tmp, "src/main/java/T.java", "class T {}");
    Path outside = write(tmp, "test/T.java", "class T {}");

    assertTrue(m.isIgnored(inSrc));
    assertFalse(m.isIgnored(outside));
  }

  @Test
  void negationLastMatchWins() throws Exception {
    IgnoreMatcher m = matcherFrom("**/*.java\n" + "!Good.java\n");

    Path bad = write(tmp, "Bad.java", "class Bad {}");
    Path good = write(tmp, "Good.java", "class Good {}");
    Path nestedGood = write(tmp, "a/b/Good.java", "class Good {}");

    assertTrue(m.isIgnored(bad));

    // This should unignore both root and nested copies.
    assertFalse(m.isIgnored(good));
    assertFalse(m.isIgnored(nestedGood));
  }

  @Test
  void commentsAndBlankLinesIgnored() throws Exception {
    IgnoreMatcher m = matcherFrom("\n" + "# comment\n" + "Bad.java\n");

    Path bad = write(tmp, "Bad.java", "class Bad {}");
    Path good = write(tmp, "Good.java", "class Good {}");

    assertTrue(m.isIgnored(bad));
    assertFalse(m.isIgnored(good));
  }

  @Test
  void trailingSpacesAreTrimmed() throws Exception {
    IgnoreMatcher m = matcherFrom("Bad.java   \n");

    Path bad = write(tmp, "Bad.java", "class Bad {}");
    assertTrue(m.isIgnored(bad));
  }

  @Test
  void negationWithOnlyBangIsIgnoredLine() throws Exception {
    IgnoreMatcher m = matcherFrom("!\nBad.java\n");

    Path bad = write(tmp, "Bad.java", "class Bad {}");
    assertTrue(m.isIgnored(bad));
  }
}
