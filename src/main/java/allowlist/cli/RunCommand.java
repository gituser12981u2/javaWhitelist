package allowlist.cli;

import java.io.IOException;
import java.util.Objects;

public final class RunCommand implements CliCommand {
  private final CliOptions options;

  public RunCommand(CliOptions options) {
    this.options = Objects.requireNonNull(options, "options");
  }

  public CliOptions options() {
    return options;
  }

  @Override
  public int execute(CommandContext ctx) throws IOException {
    return ctx.runner().run(options, ctx.err());
  }
}
