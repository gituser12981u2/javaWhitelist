package allowlist;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TestUtil {
    public static Path writeTempAllowlist(String txt) throws Exception {
        Path dir = Files.createTempDirectory("aw-allow");
        Path f = dir.resolve("allowlist_test.txt");
        Files.writeString(f, txt);
        return f;
    }
}
