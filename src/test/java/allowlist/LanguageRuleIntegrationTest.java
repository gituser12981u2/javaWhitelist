package allowlist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public final class LanguageRuleIntegrationTest {
  private static int runOnSource(String allowlistTxt, String src) throws Exception {
    Path dir = Files.createTempDirectory("aw-rule");
    Path f = dir.resolve("T.java");
    Files.writeString(f, src);

    Path allowFile = TestUtil.writeTempAllowlist(allowlistTxt);

    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBuf);

    return AllowlistChecker.run(
        new String[] {"--allowlist", allowFile.toString(), f.toString()}, err);
  }

  @Test
  public void disallowNullLiteral() throws Exception {
    int code =
        runOnSource(
            "@DISALLOW_NULL_LITERAL=true\n",
            "public class T {\n"
                + "  static Object f() {\n"
                + "    return null;\n"
                + "  }\n"
                + "  public static void main(String[] a) {}\n"
                + "}\n");
    assertEquals(1, code);
  }

  @Test
  public void disallowReturnFromVoid() throws Exception {
    int code =
        runOnSource(
            "@DISALLOW_RETURN_FROM_VOID=true\n",
            "public class T {\n"
                + " static void f(int x) {\n"
                + " if (x == 0) return;\n"
                + " }\n"
                + " public static void main(String[] a) {}\n"
                + "}\n");
    assertEquals(1, code);
  }

  @Test
  public void disallowBreakContinue() throws Exception {
    int code =
        runOnSource(
            "@DISALLOW_BREAK=true\n" + "@DISALLOW_CONTINUE=true\n",
            "public class T {\n"
                + " public static void main(String[] a) {\n"
                + " for (int i = 0; i < 3; i++) {\n"
                + " if (i == 1) continue;\n"
                + " if (i == 2) break;\n"
                + " }\n"
                + " }\n"
                + "}\n");
    assertEquals(1, code);
  }

  @Test
  public void disallowSwitch() throws Exception {
    int code =
        runOnSource(
            "@DISALLOW_SWITCH=true\n",
            "public class T {\n"
                + " public static void main(String[] a) {\n"
                + " int x = 1;\n"
                + " switch (x) { case 1: x = 2; }\n"
                + " }\n"
                + "}\n");
    assertEquals(1, code);
  }

  @Test
  public void disallowTryCatch() throws Exception {
    int code =
        runOnSource(
            "@DISALLOW_TRY=true\n",
            "public class T {\n"
                + " public static void main(String[] a) {\n"
                + " try { int x = 1; } catch (Exception e) { }\n"
                + " }\n"
                + "}\n");
    assertEquals(1, code);
  }

  @Test
  public void requireWildcardImports() throws Exception {
    int code =
        runOnSource(
            "@REQUIRE_WILDCARD_IMPORTS=true\n",
            "import java.util.ArrayList;\n"
                + "public class T { public static void main(String[] a) {} }\n");
    assertEquals(1, code);
  }

  @Test
  public void disallowEnhancedForOverQueue() throws Exception {
    String allow =
        "@ENFORCE_PREFIXES=java.,javax.\n"
            + "@DISALLOW_ENHANCED_FORLOOP_OVER_STACK_OR_QUEUE=true\n"
            + "\n"
            + "java.lang.Object#<init>\n"
            + "java.util.LinkedList#<init>\n";

    int code =
        runOnSource(
            allow,
            "import java.util.*;\n"
                + "public class T {\n"
                + "  public static void main(String[] a) {\n"
                + "    Queue<String> q = new LinkedList<>();\n"
                + "    for (String s : q) {\n"
                + "      // no-op\n"
                + "    }\n"
                + "  }\n"
                + "}\n");

    assertEquals(1, code);
  }

  @Test
  public void disallowEnhancedForOverStack() throws Exception {
    String allow =
        "@ENFORCE_PREFIXES=java.,javax.\n"
            + "@DISALLOW_ENHANCED_FORLOOP_OVER_STACK_OR_QUEUE=true\n"
            + "\n"
            + "java.lang.Object#<init>\n"
            + "java.util.Stack#<init>\n";

    int code =
        runOnSource(
            allow,
            "import java.util.*;\n"
                + "public class T {\n"
                + "  public static void main(String[] a) {\n"
                + "    Stack<String> st = new Stack<>();\n"
                + "    for (String s : st) {\n"
                + "      // no-op\n"
                + "    }\n"
                + "  }\n"
                + "}\n");

    assertEquals(1, code);
  }
}
