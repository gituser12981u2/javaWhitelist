package allowlist.build;

import allowlist.util.DebugLogger;
import allowlist.util.DebugLoggers;
import allowlist.util.DebugTiming;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves javac classpath/sourcepath options for Maven projects.
 *
 * <p>This resolver infers the standard Maven source layout from the presence of a {@code pom.xml}
 * file and uses Maven itself to compute the dependency classpath. Results are cached per normalized
 * project root and source-set kind so repeated calls for the same module do not repeatedly hit the
 * filesystem or spawn Maven processes.
 *
 * <p>Resolution behavior:
 *
 * <ul>
 *   <li>{@link SourceSetKind#MAIN}: source roots include {@code src/main/java}; dependency scope is
 *       {@code compile}
 *   <li>{@link SourceSetKind#TEST}: source roots include {@code src/test/java} and {@code
 *       src/main/java}; output directories include {@code target/classes}; dependency scope is
 *       {@code test}
 *   <li>other source-set kinds fall back to including both canonical source roots when present and
 *       using {@code compile} dependency scope
 * </ul>
 *
 * <p>If the supplied project root is {@code null} or does not contain a readable {@code pom.xml},
 * this resolver returns empty results.
 */
public final class MavenJavacOptionsResolver implements JavacOptionsResolver {
  /**
   * Cache key for resolver results.
   *
   * @param projectRoot normalized absolute Maven project root.
   * @param sourceSetKind requested source-set kind.
   */
  private record CacheKey(Path projectRoot, SourceSetKind sourceSetKind) {}

  /** Cached canonical source roots per Maven module and source-set kind. */
  private final Map<CacheKey, List<Path>> sourceRootsCache = new HashMap<>();

  /** Cached output directories per Maven module and source-set kind. */
  private final Map<CacheKey, List<Path>> outputDirectoriesCache = new HashMap<>();

  /** Cached dependency classpath entries per Maven module and source-set kind. */
  private final Map<CacheKey, List<Path>> dependencyClasspathEntriesCache = new HashMap<>();

  private static final DebugLogger LOG = DebugLoggers.forClass(MavenJavacOptionsResolver.class);

  /**
   * Resolves the source roots that should be visible to javac for a Maven project and source set.
   *
   * <p>Results are cached by normalized absolute project root and source-set kind.
   *
   * @param projectRoot Maven project root, expected to contain {@code pom.xml}.
   * @param sourceSetKind source set being compiled.
   * @return immutable list of resolved source roots, or an empty list if the path is not a Maven
   *     project or no canonical source roots exist.
   * @throws IOException IOException if resolution fails unexpectedly.
   */
  @Override
  public synchronized List<Path> resolveSourceRoots(Path projectRoot, SourceSetKind sourceSetKind)
      throws IOException {
    long t = DebugTiming.start();

    Path normalizedProjectRoot = normalizedProjectRoot(projectRoot);
    CacheKey key = new CacheKey(normalizedProjectRoot, sourceSetKind);

    List<Path> cached = sourceRootsCache.get(key);
    if (cached != null) {
      DebugTiming.log(
          LOG,
          "source roots cache hit [" + key.projectRoot() + ", " + key.sourceSetKind() + "]",
          t);
      return cached;
    }

    List<Path> resolved = resolveSourceRootsUncached(key.projectRoot(), key.sourceSetKind());
    List<Path> immutable = List.copyOf(resolved);
    sourceRootsCache.put(key, immutable);

    DebugTiming.log(
        LOG, "resolve source roots [" + key.projectRoot() + ", " + key.sourceSetKind() + "]", t);
    return immutable;
  }

  /**
   * Resolves additional output directories that should be visible to javac for a Maven project and
   * source set.
   *
   * <p>For test compilation, this includes {@code target/classes} when it exists so test sources
   * can resolve already-compiled main classes.
   *
   * <p>Results are cached by normalized absolute project root and source-set kind.
   *
   * @param projectRoot Maven project root, expected to contain {@code pom.xml}
   * @param sourceSetKind source set being compiled
   * @return immutable list of resolved output directories, or an empty list if none apply
   * @throws IOException if resolution fails unexpectedly
   */
  @Override
  public synchronized List<Path> resolveOutputDirectories(
      Path projectRoot, SourceSetKind sourceSetKind) throws IOException {
    long t = DebugTiming.start();

    Path normalizedProjectRoot = normalizedProjectRoot(projectRoot);
    CacheKey key = new CacheKey(normalizedProjectRoot, sourceSetKind);

    List<Path> cached = outputDirectoriesCache.get(key);
    if (cached != null) {
      DebugTiming.log(
          LOG,
          "output directories cache hit [" + key.projectRoot() + ", " + key.sourceSetKind() + "]",
          t);
      return cached;
    }

    List<Path> resolved = resolveOutputDirectoriesUncached(key.projectRoot(), sourceSetKind);
    List<Path> immutable = List.copyOf(resolved);
    outputDirectoriesCache.put(key, immutable);

    DebugTiming.log(
        LOG,
        "resolve output directories [" + key.projectRoot() + ", " + key.sourceSetKind() + "]",
        t);
    return immutable;
  }

  /**
   * Resolves external dependency classpath entries for a Maven project and source set.
   *
   * <p>This method shells out to Maven's {@code dependency:build-classpath} goal and converts the
   * resulting path-separated classpath string into a list of {@link Path} entries. Results are
   * cached by normalized absolute project root and source-set kind.
   *
   * @param projectRoot Maven project root, expected to contain {@code pom.xml}.
   * @param sourceSetKind source set being compiled.
   * @return immutable list of dependency classpath entries, or an empty list if none are resolved.
   * @throws IOException if Maven invocation or output processing fails.
   */
  @Override
  public synchronized List<Path> resolveDependencyClasspathEntries(
      Path projectRoot, SourceSetKind sourceSetKind) throws IOException {
    long t = DebugTiming.start();

    Path normalizedProjectRoot = normalizedProjectRoot(projectRoot);
    CacheKey key = new CacheKey(normalizedProjectRoot, sourceSetKind);

    List<Path> cached = dependencyClasspathEntriesCache.get(key);
    if (cached != null) {
      DebugTiming.log(
          LOG,
          "dependency classpath cache hit [" + key.projectRoot() + ", " + key.sourceSetKind() + "]",
          t);
      return cached;
    }

    List<Path> resolved =
        resolveDependencyClasspathEntriesUncached(key.projectRoot(), key.sourceSetKind());
    List<Path> immutable = List.copyOf(resolved);
    dependencyClasspathEntriesCache.put(key, immutable);

    DebugTiming.log(
        LOG,
        "resolve dependency classpath [" + key.projectRoot() + ", " + key.sourceSetKind() + "]",
        t);
    return immutable;
  }

  /**
   * Computes canonical Maven source roots without consulting the cache.
   *
   * <p>Recognized roots are {@code src/main/java} and {@code src/test/java}. For test compilation,
   * both test and main roots are included when present.
   *
   * @param projectRoot normalized project root.
   * @param sourceSetKind source set being compiled.
   * @return mutable list of source roots, possibly empty.
   */
  private static List<Path> resolveSourceRootsUncached(
      Path projectRoot, SourceSetKind sourceSetKind) {
    List<Path> roots = new ArrayList<>();

    if (projectRoot == null || !Files.isRegularFile(projectRoot.resolve("pom.xml"))) {
      return List.of();
    }

    Path mainSrc = projectRoot.resolve("src").resolve("main").resolve("java");
    Path testSrc = projectRoot.resolve("src").resolve("test").resolve("java");

    if (sourceSetKind == SourceSetKind.MAIN) {
      if (Files.isDirectory(mainSrc)) {
        roots.add(mainSrc);
      }
    } else if (sourceSetKind == SourceSetKind.TEST) {
      if (Files.isDirectory(testSrc)) {
        roots.add(testSrc);
      }

      if (Files.isDirectory(mainSrc)) {
        roots.add(mainSrc);
      }
    } else {
      if (Files.isDirectory(mainSrc)) {
        roots.add(mainSrc);
      }

      if (Files.isDirectory(testSrc)) {
        roots.add(testSrc);
      }
    }

    return roots;
  }

  /**
   * Computes additional output directories without consulting the cache.
   *
   * <p>Currently only test compilation contributes an extra directory: {@code target/classes}, when
   * present.
   *
   * @param projectRoot normalized project root.
   * @param sourceSetKind source set being compiled.
   * @return mutable list of output directories, possibly empty.
   */
  private static List<Path> resolveOutputDirectoriesUncached(
      Path projectRoot, SourceSetKind sourceSetKind) {
    List<Path> outputs = new ArrayList<>();

    if (projectRoot == null || !Files.isRegularFile(projectRoot.resolve("pom.xml"))) {
      return List.of();
    }

    if (sourceSetKind == SourceSetKind.TEST) {
      Path mainOutput = projectRoot.resolve("target").resolve("classes");
      if (Files.isDirectory(mainOutput)) {
        outputs.add(mainOutput);
      }
    }

    return outputs;
  }

  /**
   * Computes dependency classpath entries without consulting the cache.
   *
   * <p>The Maven dependency classpath string is split using the platform path separator and
   * converted into {@link Path} objects.
   *
   * @param projectRoot normalized project root.
   * @param sourceSetKind source set being compiled.
   * @return mutable list of dependency classpath entries, possibly empty.
   * @throws IOException if Maven invocation or output processing fails.
   */
  private List<Path> resolveDependencyClasspathEntriesUncached(
      Path projectRoot, SourceSetKind sourceSetKind) throws IOException {
    long t = DebugTiming.start();

    if (projectRoot == null || !Files.isRegularFile(projectRoot.resolve("pom.xml"))) {
      DebugTiming.log(LOG, "no pom.xml -> empty javac options", t);
      return List.of();
    }

    String scope = scopeFor(sourceSetKind);
    String dependencyClasspath = resolveDependencyClasspathString(projectRoot, scope);

    List<Path> out = new ArrayList<>();
    if (dependencyClasspath == null || dependencyClasspath.isBlank()) {
      DebugTiming.log(
          LOG,
          "resolve dependency classpath entries uncached ["
              + projectRoot
              + ", "
              + sourceSetKind
              + "]",
          t);
      return List.of();
    }

    for (String part : dependencyClasspath.split(File.pathSeparator)) {
      if (!part.isBlank()) {
        out.add(Path.of(part));
      }
    }

    DebugTiming.log(
        LOG,
        "resolve dependency classpath entries uncached ["
            + projectRoot
            + ", "
            + sourceSetKind
            + "]",
        t);
    return out;
  }

  /**
   * Maps a source-set kind to the Maven dependency scope used when asking Maven for a classpath.
   *
   * @param sourceSetKind source set being compiled.
   * @return {@code "test"} for test sources, otherwise {@code "compile"}.
   */
  private static String scopeFor(SourceSetKind sourceSetKind) {
    if (sourceSetKind == SourceSetKind.TEST) {
      return "test";
    }

    if (sourceSetKind == SourceSetKind.MAIN) {
      return "compile";
    }

    return "compile";
  }

  /**
   * Invokes Maven to build a dependency classpath string for the given project and scope.
   *
   * <p>The classpath is written by Maven to a temporary file via {@code -Dmdep.outputFile=...}.
   * Standard output and error are redirected to a temporary log file so failures can be surfaced in
   * an exception message.
   *
   * <p>If Maven succeeds but no classpath file is produced, this method returns an empty string.
   *
   * @param projectRoot Maven project root.
   * @param scope Maven dependency scope, usually {@code compile} or {@code test}.
   * @return trimmed classpath string, possibly empty.
   * @throws IOException if process launch, interruption handling, log reading, or temp-file cleanup
   *     fails.
   */
  private static String resolveDependencyClasspathString(Path projectRoot, String scope)
      throws IOException {
    long totalStart = DebugTiming.start();
    Path outFile = Files.createTempFile("javawhitelist-mvn-classpath-", ".txt");
    Path logFile = Files.createTempFile("javawhitelist-mvn-log-", ".txt");

    try {
      List<String> cmd = new ArrayList<>();
      cmd.add(findMavenCommand(projectRoot));
      cmd.add("-q");
      cmd.add("-DincludeScope=" + scope);
      cmd.add("-Dmdep.outputFile=" + outFile);
      cmd.add("dependency:build-classpath");

      long pbStart = DebugTiming.start();
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(projectRoot.toFile());
      pb.redirectErrorStream(true);
      pb.redirectOutput(logFile.toFile());
      DebugTiming.log(
          LOG, "    prepare process builder [" + projectRoot + ", " + scope + "]", pbStart);

      long procStart = DebugTiming.start();
      Process process = pb.start();
      DebugTiming.log(
          LOG, "    start maven process [" + projectRoot + ", " + scope + "]", procStart);

      long waitStart = DebugTiming.start();
      int exitCode;
      try {
        exitCode = process.waitFor();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while resolving Maven classpath.", e);
      }
      DebugTiming.log(LOG, "    wait for maven [" + projectRoot + ", " + scope + "]", waitStart);

      if (exitCode != 0) {
        String output =
            Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
        throw new IOException(
            "Maven classpath resolution failed for "
                + projectRoot
                + " with exit code "
                + exitCode
                + ". Output:\n"
                + output);
      }

      if (!Files.exists(outFile)) {
        DebugTiming.log(
            LOG,
            "    resolve dependency classpath total [" + projectRoot + ", " + scope + "]",
            totalStart);
        return "";
      }

      long fileReadStart = DebugTiming.start();
      String result = Files.readString(outFile, StandardCharsets.UTF_8).trim();
      DebugTiming.log(
          LOG, "    read classpath file [" + projectRoot + ", " + scope + "]", fileReadStart);

      DebugTiming.log(
          LOG,
          "  resolve dependency classpath total [" + projectRoot + ", " + scope + "]",
          totalStart);
      return result;
    } finally {
      Files.deleteIfExists(outFile);
      Files.deleteIfExists(logFile);
    }
  }

  /**
   * Chooses the Maven executable to run for a project.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>project-local Unix wrapper {@code mvnw} if present and executable
   *   <li>project-local Windows wrapper {@code mvnw.cmd} if present
   *   <li>fallback command {@code mvn}
   * </ol>
   *
   * @param projectRoot Maven project root.
   * @return executable name or absolute wrapper path to invoke.
   */
  private static String findMavenCommand(Path projectRoot) {
    Path mvnwUnix = projectRoot.resolve("mvnw");
    if (Files.isRegularFile(mvnwUnix) && Files.isExecutable(mvnwUnix)) {
      return mvnwUnix.toAbsolutePath().toString();
    }

    Path mvnwCmd = projectRoot.resolve("mvnw.cmd");
    if (Files.isRegularFile(mvnwCmd)) {
      return mvnwCmd.toAbsolutePath().toString();
    }

    return "mvn";
  }

  /**
   * Normalizes a project root for cache-key use.
   *
   * @param projectRoot candidate project root.
   * @return normalized absolute path, or {@code null} if the input is {@code null}.
   */
  private static Path normalizedProjectRoot(Path projectRoot) {
    return (projectRoot == null) ? null : projectRoot.toAbsolutePath().normalize();
  }
}
