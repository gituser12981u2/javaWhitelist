package allowlist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class AllowlistResolver {
  private final String osName;
  private final String userHome;
  private final Map<String, String> env;

  public AllowlistResolver(String osName, String userHome, Map<String, String> env) {
    this.osName = osName;
    this.userHome = userHome;
    this.env = env;
  }

  public AllowlistConfig resovleAndLoad(String explicitPath, Path projectRoot) throws IOException {
    LoadSource src = resolveSource(explicitPath, projectRoot);

    if (src.isFile()) {
      return AllowlistLoader.loadFromFile(src.filePath());
    }

    return AllowlistLoader.loadFromResource(src.resourceName());
  }

  /**
   * Resolves which allowlist source to load, based on CLI and config lookup.
   *
   * <p>Precedence:
   *
   * <ol>
   *   <li>Explicit CLI path
   *   <li>Nearest ancestor {@code allowlist.txt}, starting from {@code projectRoot} and walking
   *       upward
   *   <li>Universal config path ({@code ~/.config/...})
   *   <li>OS specific config path
   *   <li>Bundled resource
   * </ol>
   *
   * @param explicitPath allowlist path from {@code --allowlist}, or {@code null}.
   * @param osName OS name.
   * @param userHome home directory.
   * @param projectRoot neartest compilation/project root for the current file group.
   * @return resolved load source (file or resource).
   */
  LoadSource resolveSource(String explicitPath, Path projectRoot) {
    if (explicitPath != null) {
      return LoadSource.file(explicitPath);
    }

    Path nearestAllowlist = findNearestAllowlist(projectRoot);
    if (isReadableFile(nearestAllowlist)) {
      return LoadSource.file(nearestAllowlist.toString());
    }

    Path universal = PathResolver.universalPath(userHome);
    if (isReadableFile(universal)) {
      return LoadSource.file(universal.toString());
    }

    Path osSpecific = PathResolver.osSpecificPath(osName, userHome, env);
    if (isReadableFile(osSpecific)) {
      return LoadSource.file(osSpecific.toString());
    }

    return LoadSource.resource("allowlist.txt");
  }

  /**
   * Finds the first readable {@code allowlist.txt} which walking upward from a starting directory.
   *
   * <p>The search beings at {@code startDir} itself, then continues through each ancestor until the
   * filesystem root. The first matching file is returned.
   *
   * <p>This provides first match wins semantics for nested Maven projects and multi-module builds:
   * a module-local {@code allowlist.txt} overrides a parent/root one, while a root allowlist still
   * applies when a module does not define its own.
   *
   * @param startDir directory from which to begin the upward search.
   * @return the nearest readable {@code allowlist.txt}, or {@code null} if none is found.
   */
  private static Path findNearestAllowlist(Path startDir) {
    Path cur = (startDir == null) ? null : startDir.toAbsolutePath().normalize();

    while (cur != null) {
      Path candidate = cur.resolve("allowlist.txt");
      if (isReadableFile(candidate)) {
        return candidate;
      }
      cur = cur.getParent();
    }

    return null;
  }

  /**
   * Checks whether a path is an existing, readable regular file.
   *
   * @param p candidate path
   * @return true if {@code p} exists, is a regular file, and is readable.
   */
  private static boolean isReadableFile(Path p) {
    return p != null && Files.exists(p) && Files.isRegularFile(p) && Files.isReadable(p);
  }
}
