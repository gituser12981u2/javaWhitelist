package allowlist.cli;

public final class VersionCommand implements CliCommand {
  @Override
  public int execute(CommandContext ctx) {
    ctx.out().println("javaWhitelist v" + ctx.version());
    return 0;
  }
}
