package allowlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import allowlist.cli.CliOptions;
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
        "public class Good {\n"
            + " public static void main(String[] args) {\n"
            + " String s = \"hi\";\n"
            + " int n = s.length();\n"
            + " System.out.println(n);\n"
            + " }\n"
            + "}\n");

    String allow =
        "@ENFORCE_PREFIXES=java.,javax.\n"
            + "\n"
            + "java.lang.Object#<init>\n"
            + "java.lang.String#length\n"
            + "java.lang.System#out\n"
            + "java.io.PrintStream#println\n";

    Path allowFile = TestUtil.writeTempAllowlist(allow);

    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBuf);

    CliOptions options =
        CliOptions.parse(new String[] {"--allowlist", allowFile.toString(), f.toString()});

    int code = AllowlistChecker.run(options, err);

    assertEquals(0, code, "Expected success for whitelisted program. stderr:\n" + errBuf);
  }

  @Test
  public void rejectsNonWhitelistedCalls() throws Exception {
    Path dir = Files.createTempDirectory("aw-bad");
    Path f = dir.resolve("Bad.java");

    // String.trim should be rejected
    Files.writeString(
        f,
        "public class Bad {\n"
            + " public static void main(String[] args) {\n"
            + " String s = \" hi \";\n"
            + " System.out.println(s.trim());\n"
            + " }\n"
            + "}\n");

    String allow =
        "@ENFORCE_PREFIXES=java.,javax.\n"
            + "\n"
            + "java.lang.Object#<init>\n"
            + "java.lang.System#out\n"
            + "java.io.PrintStream#println\n";

    Path allowFile = TestUtil.writeTempAllowlist(allow);

    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBuf);

    CliOptions options =
        CliOptions.parse(new String[] {"--allowlist", allowFile.toString(), f.toString()});

    int code = AllowlistChecker.run(options, err);

    assertEquals(1, code, "Expected violation exit code. stderr:\n" + errBuf);
    assertTrue(
        errBuf.toString().contains("Disallowed API usage"),
        "stderr should mention disallowed usage. stderr:\n" + errBuf);
  }

  @Test
  public void ignoresMatchedFiles() throws Exception {
    Path dir = Files.createTempDirectory("aw-ignore");

    Path good = dir.resolve("Good.java");
    Files.writeString(
        good,
        "public class Good {\n"
            + " public static void main(String[] args) {\n"
            + " String s = \"hi\";\n"
            + " System.out.println(s.length());\n"
            + " }\n"
            + "}\n");

    Path bad = dir.resolve("Bad.java");
    Files.writeString(
        bad,
        "public class Bad {\n"
            + " public static void main(String[] args) {\n"
            + " String s = \" hi \";\n"
            + " System.out.println(s.trim());\n"
            + " }\n"
            + "}\n");

    Path ignore = dir.resolve(".javawhitelistignore");
    Files.writeString(ignore, "Bad.java\n");

    String allow =
        "@ENFORCE_PREFIXES=java.,javax.\n"
            + "\n"
            + "java.lang.Object#<init>\n"
            + "java.lang.String#length\n"
            + "java.lang.System#out\n"
            + "java.io.PrintStream#println\n";

    Path allowFile = TestUtil.writeTempAllowlist(allow);

    ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    PrintStream err = new PrintStream(errBuf);

    CliOptions options =
        CliOptions.parse(
            new String[] {
              "--allowlist", allowFile.toString(), "--ignore", ignore.toString(), dir.toString()
            });

    int code = AllowlistChecker.run(options, err);

    assertEquals(0, code, "Expected success because Bad.java is ignored. stderr:\n" + errBuf);
    assertTrue(!errBuf.toString().contains("Bad.java"), "stderr should not mention ignored file");
  }
}
