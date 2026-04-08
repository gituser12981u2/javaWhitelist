package allowlist.cli;

import java.io.IOException;

public interface CliCommand {
  int execute(CommandContext ctx) throws IOException;
}
