package allowlist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestUtil {
  public static Path writeTempAllowlist(String txt) throws Exception {
    Path dir = Files.createTempDirectory("aw-allow");
    Path f = dir.resolve("allowlist_test.txt");
    Files.writeString(f, txt);
    return f;
  }

  public Path write(Path tempDir, String relative, String content) throws IOException {
    Path p = tempDir.resolve(relative);
    Files.createDirectories(p.getParent());
    Files.writeString(p, content);
    return p;
  }
}
