package allowlist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public final class LanguageRuleIntegrationTest {
    private static int runOnSource(String src) throws Exception {
        Path dir = Files.createTempDirectory("aw-rule");
        Path f = dir.resolve("T.java");
        Files.writeString(f, src);

        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(errBuf);

        int code = AllowlistChecker.run(new String[] { f.toString() }, err);

        return code;
    }

    @Test
    public void disallowNullLiteral() throws Exception {
        int code = runOnSource(
                "public class T {\n" +
                        "  static Object f() {\n" +
                        "    return null;\n" +
                        "  }\n" +
                        "  public static void main(String[] a) {}\n" +
                        "}\n");
        assertEquals(1, code);
    }

    @Test
    public void disallowReturnFromVoid() throws Exception {
        int code = runOnSource(
                "public class T {\n" +
                        " static void f(int x) {\n" +
                        " if (x == 0) return;\n" +
                        " }\n" +
                        " public static void main(String[] a) {}\n" +
                        "}\n");
        assertEquals(1, code);
    }

    @Test
    public void disallowBreakContinue() throws Exception {
        int code = runOnSource(
                "public class T {\n" +
                        " public static void main(String[] a) {\n" +
                        " for (int i = 0; i < 3; i++) {\n" +
                        " if (i == 1) continue;\n" +
                        " if (i == 2) break;\n" +
                        " }\n" +
                        " }\n" +
                        "}\n");
        assertEquals(1, code);
    }

    @Test
    public void disallowSwitch() throws Exception {
        int code = runOnSource(
                "public class T {\n" +
                        " public static void main(String[] a) {\n" +
                        " int x = 1;\n" +
                        " switch (x) { case 1: x = 2; }\n" +
                        " }\n" +
                        "}\n");
        assertEquals(1, code);
    }

    @Test
    public void disallowTryCatch() throws Exception {
        int code = runOnSource(
                "public class T {\n" +
                        " public static void main(String[] a) {\n" +
                        " try { int x = 1; } catch (Exception e) { }\n" +
                        " }\n" +
                        "}\n");
        assertEquals(1, code);
    }

    @Test
    public void requireWildcardImports() throws Exception {
        int code = runOnSource(
                "import java.util.ArrayList;\n" +
                        "public class T { public static void main(String[] a) {} }\n");
        assertEquals(1, code);
    }
}
