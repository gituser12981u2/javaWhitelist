package allowlist.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;

public final class CommandContext {
  private final PrintStream out;
  private final PrintStream err;
  private final String helpText;
  private final String version;
  private final CliApplicationRunner runner;

  public CommandContext(
      PrintStream out,
      PrintStream err,
      String helpText,
      String version,
      CliApplicationRunner runner) {
    this.out = Objects.requireNonNull(out, "out");
    this.err = Objects.requireNonNull(err, "err");
    this.helpText = Objects.requireNonNull(helpText, "helpText");
    this.version = Objects.requireNonNull(version, "version");
    this.runner = Objects.requireNonNull(runner, "runner");
  }

  public PrintStream out() {
    return out;
  }

  public PrintStream err() {
    return err;
  }

  public String helpText() {
    return helpText;
  }

  public String version() {
    return version;
  }

  public CliApplicationRunner runner() {
    return runner;
  }

  @FunctionalInterface
  public interface CliApplicationRunner {
    int run(CliOptions options, PrintStream err) throws IOException;
  }
}
