package allowlist.cli;

public final class HelpCommand implements CliCommand {
  @Override
  public int execute(CommandContext ctx) {
    ctx.out().println(ctx.helpText());
    return 0;
  }
}
