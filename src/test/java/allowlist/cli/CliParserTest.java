package allowlist.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class CliParserTest {
  @Test
  public void parsesRunCommandWithSingleTarget() {
    CliCommand cmd = CliParser.parse(new String[] {"src/Main.java"});

    RunCommand run = assertInstanceOf(RunCommand.class, cmd);
    CliOptions options = run.options();

    assertEquals(null, options.allowlistPath());
    assertEquals(null, options.ignorePath());
    assertEquals(1, options.targetPaths().size());
    assertEquals(Paths.get("src/Main.java"), options.targetPaths().get(0));
  }

  @Test
  public void parsesRunCommandWithAllowlistAndIgnoreAndMultipleTargets() {
    CliCommand cmd =
        CliParser.parse(
            new String[] {
              "--allowlist",
              "conf/allowlist.txt",
              "--ignore",
              ".javawhitelistignore",
              "src",
              "test/Foo.java",
            });

    RunCommand run = assertInstanceOf(RunCommand.class, cmd);
    CliOptions options = run.options();

    assertEquals("conf/allowlist.txt", options.allowlistPath());
    assertEquals(".javawhitelistignore", options.ignorePath());
    assertEquals(2, options.targetPaths().size());
    assertEquals(Paths.get("src"), options.targetPaths().get(0));
    assertEquals(Paths.get("test/Foo.java"), options.targetPaths().get(1));
  }

  @Test
  public void parsesRunCommandWhenHelpAppears() {
    CliCommand cmd = CliParser.parse(new String[] {"--help", "src"});

    RunCommand run = assertInstanceOf(RunCommand.class, cmd);
    CliOptions options = run.options();

    assertEquals(2, options.targetPaths().size());
    assertEquals(Paths.get("--help"), options.targetPaths().get(0));
    assertEquals(Paths.get("src"), options.targetPaths().get(1));
  }

  @Test
  public void rejectsNoArguments() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> CliParser.parse(new String[0]));

    assertEquals("No arguments provided.", e.getMessage());
  }

  @Test
  public void rejectsNullArguments() {
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> CliParser.parse(null));

    assertEquals("No arguments provided.", e.getMessage());
  }

  @Test
  public void rejectsMissingAllowlistValue() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> CliParser.parse(new String[] {"--allowlist"}));

    assertEquals("Missing value for --allowlist.", e.getMessage());
  }

  @Test
  public void rejectsMissingIgnoreValue() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> CliParser.parse(new String[] {"--ignore"}));

    assertEquals("Missing value for --ignore.", e.getMessage());
  }

  @Test
  public void preservesTargetOrder() {
    CliCommand cmd = CliParser.parse(new String[] {"a", "b", "c"});

    RunCommand run = assertInstanceOf(RunCommand.class, cmd);
    CliOptions options = run.options();

    assertEquals(List.of(Path.of("a"), Path.of("b"), Path.of("c")), options.targetPaths());
  }
}
