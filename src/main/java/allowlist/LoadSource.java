package allowlist;

/**
 * Represents the resolved source of an allowlist configuration.
 *
 * <p>An allowlist may be loaded either from:
 *
 * <ul>
 *   <li>a filesystem path or a classpath resource bundeled with the application.<\li>
 * </ul>
 *
 * <p>This is a small value object used to separate resolution from loading.
 *
 * <p>Exactly one of {@link #filePath()} or {@link #resourceName()} will be non-null. This class
 * does not validate that the referenced file/resource exists.
 */
public final class LoadSource {

  /** Non null when the allowlist should be loaded from a file path. */
  private final String filePath;

  /** Non null when the allowlist should be loaded from a classpath resource name. */
  private final String resourceName;

  /**
   * Constructs a source.
   *
   * @param filePath file path or {@code null} if the source is a resource.
   * @param resourceName resource name or {@code null} if this source is a file.
   */
  private LoadSource(String filePath, String resourceName) {
    this.filePath = filePath;
    this.resourceName = resourceName;
  }

  /**
   * Creates a source that points to a filesystem path.
   *
   * @param path filesystem path to the allowlist file.
   * @return a file backed source.
   */
  public static LoadSource file(String path) {
    return new LoadSource(path, null);
  }

  /**
   * Creates a source that points to a bundled classpath resource.
   *
   * @param name resource name, usually {@code allowlist.txt}.
   * @return a resource backed source.
   */
  public static LoadSource resource(String name) {
    return new LoadSource(null, name);
  }

  public boolean isFile() {
    return filePath != null;
  }

  public String filePath() {
    return filePath;
  }

  public String resourceName() {
    return resourceName;
  }
}
