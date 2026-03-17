package allowlist.build;

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
 * <p>This resolver asks Maven for the dependency classpath and then augments it with the canonical
 * Maven source roots. For test sources, this also adds {@code target/classes} so tests can resolve
 * main classes when they have already been compiled.
 */
public final class MavenJavacOptionsResolver implements JavacOptionsResolver {
  private record CacheKey(Path projectRoot, SourceSetKind sourceSetKind) {}

  private final Map<CacheKey, List<Path>> sourceRootsCache = new HashMap<>();
  private final Map<CacheKey, List<Path>> outputDirectoriesCache = new HashMap<>();
  private final Map<CacheKey, List<Path>> dependencyClasspathEntriesCache = new HashMap<>();

  @Override
  public synchronized List<Path> resolveSourceRoots(Path projectRoot, SourceSetKind sourceSetKind)
      throws IOException {
    long t = nowNanos();

    CacheKey key = new CacheKey(projectRoot.toAbsolutePath().normalize(), sourceSetKind);
    List<Path> cached = sourceRootsCache.get(key);
    if (cached != null) {
      debugTiming(
          "source roots cache hit [" + key.projectRoot() + ", " + key.sourceSetKind() + "]", t);
      return cached;
    }

    List<Path> resolved = resolveSourceRootsUncached(key.projectRoot(), key.sourceSetKind());
    List<Path> immutable = List.copyOf(resolved);
    sourceRootsCache.put(key, immutable);

    debugTiming("resolve source roots [" + key.projectRoot() + ", " + key.sourceSetKind() + "]", t);
    return immutable;
  }

  @Override
  public synchronized List<Path> resolveOutputDirectories(
      Path projectRoot, SourceSetKind sourceSetKind) throws IOException {
    long t = nowNanos();

    CacheKey key = new CacheKey(projectRoot.toAbsolutePath().normalize(), sourceSetKind);
    List<Path> cached = outputDirectoriesCache.get(key);
    if (cached != null) {
      debugTiming(
          "output directories cache hit [" + key.projectRoot() + ", " + key.sourceSetKind() + "]",
          t);
      return cached;
    }

    List<Path> resolved = resolveOutputDirectoriesUncached(key.projectRoot(), sourceSetKind);
    List<Path> immutable = List.copyOf(resolved);
    outputDirectoriesCache.put(key, immutable);

    debugTiming(
        "resolve output directories [" + key.projectRoot() + ", " + key.sourceSetKind() + "]", t);
    return immutable;
  }

  @Override
  public synchronized List<Path> resolveDependencyClasspathEntries(
      Path projectRoot, SourceSetKind sourceSetKind) throws IOException {
    long t = nowNanos();

    CacheKey key = new CacheKey(projectRoot.toAbsolutePath().normalize(), sourceSetKind);
    List<Path> cached = dependencyClasspathEntriesCache.get(key);
    if (cached != null) {
      debugTiming(
          "dependency classpath cache hit [" + key.projectRoot() + ", " + key.sourceSetKind() + "]",
          t);
      return cached;
    }

    List<Path> resolved =
        resolveDependencyClasspathEntriesUncached(key.projectRoot(), key.sourceSetKind());
    List<Path> immutable = List.copyOf(resolved);
    dependencyClasspathEntriesCache.put(key, immutable);

    debugTiming(
        "resolve dependency classpath [" + key.projectRoot() + ", " + key.sourceSetKind() + "]", t);
    return immutable;
  }

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

  private List<Path> resolveDependencyClasspathEntriesUncached(
      Path projectRoot, SourceSetKind sourceSetKind) throws IOException {
    long t = nowNanos();

    if (projectRoot == null || !Files.isRegularFile(projectRoot.resolve("pom.xml"))) {
      debugTiming("no pom.xml -> empty javac options", t);
      return List.of();
    }

    String scope = scopeFor(sourceSetKind);
    String dependencyClasspath = resolveDependencyClasspathString(projectRoot, scope);

    List<Path> out = new ArrayList<>();
    if (dependencyClasspath == null || dependencyClasspath.isBlank()) {
      debugTiming(
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

    debugTiming(
        "resolve dependency classpath entries uncached ["
            + projectRoot
            + ", "
            + sourceSetKind
            + "]",
        t);
    return out;
  }

  private static String scopeFor(SourceSetKind sourceSetKind) {
    if (sourceSetKind == SourceSetKind.TEST) {
      return "test";
    }

    if (sourceSetKind == SourceSetKind.MAIN) {
      return "compile";
    }

    return "compile";
  }

  private static String resolveDependencyClasspathString(Path projectRoot, String scope)
      throws IOException {
    long totalStart = nowNanos();
    Path outFile = Files.createTempFile("javawhitelist-mvn-classpath-", ".txt");
    Path logFile = Files.createTempFile("javawhitelist-mvn-log-", ".txt");

    try {
      List<String> cmd = new ArrayList<>();
      cmd.add(findMavenCommand(projectRoot));
      cmd.add("-q");
      cmd.add("-DincludeScope=" + scope);
      cmd.add("-Dmdep.outputFile=" + outFile);
      cmd.add("dependency:build-classpath");

      long pbStart = nowNanos();
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(projectRoot.toFile());
      pb.redirectErrorStream(true);
      pb.redirectOutput(logFile.toFile());
      debugTiming("    prepare process builder [" + projectRoot + ", " + scope + "]", pbStart);

      long procStart = nowNanos();
      Process process = pb.start();
      debugTiming("    start maven process [" + projectRoot + ", " + scope + "]", procStart);

      long waitStart = nowNanos();
      int exitCode;
      try {
        exitCode = process.waitFor();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while resolving Maven classpath.", e);
      }
      debugTiming("    wait for maven [" + projectRoot + ", " + scope + "]", waitStart);

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
        debugTiming(
            "    resolve dependency classpath total [" + projectRoot + ", " + scope + "]",
            totalStart);
        return "";
      }

      long fileReadStart = nowNanos();
      String result = Files.readString(outFile, StandardCharsets.UTF_8).trim();
      debugTiming("    read classpath file [" + projectRoot + ", " + scope + "]", fileReadStart);

      debugTiming(
          "  resolve dependency classpath total [" + projectRoot + ", " + scope + "]", totalStart);
      return result;
    } finally {
      Files.deleteIfExists(outFile);
      Files.deleteIfExists(logFile);
    }
  }

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

  private static long nowNanos() {
    return System.nanoTime();
  }

  private static long elapsedMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  private static void debugTiming(String phase, long startNanos) {
    System.err.println("[debug:mvn] " + phase + ": " + elapsedMillis(startNanos) + " ms");
  }
}
