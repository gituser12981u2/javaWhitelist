package allowlist.cli;

public final class CliParser {
  private CliParser() {}

  /**
   * Parses CLI arguments into a command.
   *
   * @param args raw CLI args
   * @return parsed command
   * @throws IllegalArgumentException if the arguments are invalid
   */
  public static CliCommand parse(String[] args) {
    if (args == null || args.length == 0) {
      throw new IllegalArgumentException("No arguments provided.");
    }

    if (args.length == 1) {
      if (args[0].equals("--help") || args[0].equals("-h")) {
        return new HelpCommand();
      }

      if (args[0].equals("--version") || args[0].equals("-v")) {
        return new VersionCommand();
      }
    }

    return new RunCommand(CliOptions.parse(args));
  }
}
