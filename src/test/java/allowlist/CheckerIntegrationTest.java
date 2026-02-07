package allowlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public final class CheckerIntegrationTest {
    @Test
    public void allowsWhitelistedCalls() throws Exception {
        Path dir = Files.createTempDirectory("aw-good");
        Path f = dir.resolve("Good.java");

        Files.writeString(
                f,
                "public class Good {\n" +
                        " public static void main(String[] args) {\n" +
                        " String s = \"hi\";\n" +
                        " int n = s.length();\n" +
                        " System.out.println(n);\n" +
                        " }\n" +
                        "}\n");

        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBuf);

        int code = AllowlistChecker.run(new String[] { f.toString() }, err);

        assertEquals(0, code, "Expected success for whitelisted program. stderr:\n" +
                errBuf);
    }

    @Test
    public void rejectsNonWhitelistedCalls() throws Exception {
        Path dir = Files.createTempDirectory("aw-bad");
        Path f = dir.resolve("Bad.java");

        // String.trim should be rejected
        Files.writeString(
                f,
                "public class Bad {\n" +
                        " public static void main(String[] args) {\n" +
                        " String s = \" hi \";\n" +
                        " System.out.println(s.trim());\n" +
                        " }\n" +
                        "}\n");

        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBuf);

        int code = AllowlistChecker.run(new String[] { f.toString() }, err);

        assertEquals(1, code, "Expected violation exit code. stderr:\n" + errBuf);
        assertTrue(errBuf.toString().contains("Disallowed API usage"),
                "stderr should mention disallowed usage. stderr:\n" + errBuf);
    }
}
