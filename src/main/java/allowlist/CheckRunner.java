package allowlist;

import allowlist.cli.CliOptions;
import allowlist.fs.IgnoreMatcher;
import allowlist.scan.CheckerScanner;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Runs the allowlist checker for a parsed set of CLI options.
 *
 * <p>This class coordinates the end to end checking flow:
 *
 * <ol>
 *   <li>resolve the ignore file
 *   <li>collect {@code .java} input files from the requested targets
 *   <li>filter ignored files
 *   <li>group source files by nearest Maven root ({@code pom.xml})
 *   <li>resolve and load the effective allowlist for each group
 *   <li>parse and analyze each group with javac
 *   <li>scan ASTs and violations
 *   <li>print warnings and violations and return an exit code
 * </ol>
 */
public final class CheckRunner {
  /** Resolver used to locate and load the effective allowlist for each compilation group. */
  private final AllowlistResolver resolver;

  /**
   * Creates a new runner.
   *
   * @param resolver resolver used to locate and load allowlist configurations.
   */
  public CheckRunner(AllowlistResolver resolver) {
    this.resolver = resolver;
  }

  /**
   * Runs the checker and returns the process exit code.
   *
   * <p>Exit codes:
   *
   * <ul>
   *   <li>{@code 0}: no violations
   *   <li>{@code 1}: one or more violations were found
   *   <li>{@code 2}: usage error, tool failure, or internal failure
   * </ul>
   *
   * @param options CLI options.
   * @param err output stream used for diagnostics.
   * @return exit code (0/1/2).
   * @throws IOException if file traversal or compiler I/O fails.
   */
  public int run(CliOptions options, PrintStream err) throws IOException {
    Path cwd = Paths.get("").toAbsolutePath().normalize();
    IgnoreMatcher ignores = IgnoreMatcher.empty(cwd);

    Path ignoreFile = resolveIgnoreFile(options, cwd);
    if (ignoreFile != null) {
      try {
        ignores = IgnoreMatcher.fromFile(ignoreFile);
      } catch (IOException e) {
        err.println("Failed to read ignore file: " + ignoreFile + ": " + e.getMessage());
        return 2;
      }
    }

    List<Path> javaFiles = new ArrayList<>();
    for (Path target : options.targetPaths()) {
      collectJavaFiles(target, javaFiles);
    }

    if (javaFiles.isEmpty()) {
      err.println("No .java files found in the provided paths.");
      return 2;
    }

    // Precompute ignored set
    Set<Path> ignoredAbs = computeIgnoredFiles(javaFiles, ignores);

    Map<Path, List<Path>> groups = groupByPomRoot(javaFiles);
    if (groups.isEmpty()) {
      err.println("No .java files found after applying ignore rules.");
      return 2;
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      err.println("No system Java compiler found.");
      return 2;
    }

    List<String> violations = new ArrayList<>();
    boolean hadInternalFailure = false;

    // Compile and scan each group separately
    for (Map.Entry<Path, List<Path>> entry : groups.entrySet()) {
      Path projectRoot = entry.getKey();
      List<Path> groupFiles = entry.getValue();

      AllowlistConfig config;
      try {
        config = resolver.resovleAndLoad(options.allowlistPath(), projectRoot);
      } catch (FileNotFoundException e) {
        err.println("Allowlist file not found: " + options.allowlistPath());
        return 2;
      } catch (IOException e) {
        err.println(
            "Failed to load allowlist for project root " + projectRoot + ": " + e.getMessage());
        return 2;
      }

      DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
      StandardJavaFileManager fm =
          compiler.getStandardFileManager(diags, null, StandardCharsets.UTF_8);

      try {
        Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(groupFiles);

        // Avoid annotation processing
        List<String> annotOptions = Arrays.asList("-proc:none");

        JavacTask task = (JavacTask) compiler.getTask(null, fm, diags, annotOptions, null, units);

        Iterable<? extends CompilationUnitTree> parsed;

        try {

          parsed = task.parse();
          task.analyze();
        } catch (IOException ex) {
          err.println("Failed to parse/analyze sources: " + ex.getMessage());
          hadInternalFailure = true;
          continue;
        }

        Trees trees = Trees.instance(task);
        Types types = task.getTypes();
        Elements elements = task.getElements();

        for (CompilationUnitTree cu : parsed) {
          JavaFileObject src = cu.getSourceFile();
          if (src != null) {
            try {
              Path srcPath = Paths.get(src.toUri()).toAbsolutePath().normalize();
              if (ignoredAbs.contains(srcPath)) {
                continue;
              }
            } catch (Exception ex) {
              // If path cannot be resolved, then fall through and scan.
            }
          }

          new CheckerScanner(trees, types, elements, cu, violations, config).scan(cu, null);
        }

        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
          if (d.getKind() != Diagnostic.Kind.ERROR) {
            continue;
          }

          JavaFileObject src = d.getSource();
          if (src != null) {
            try {
              Path srcPath = Paths.get(src.toUri()).toAbsolutePath().normalize();
              if (ignoredAbs.contains(srcPath)) {
                continue;
              }
            } catch (Exception ex) {
              // ignore and print diagnostic
            }
          }

          err.println("Warning: " + formatDiagnostic(d));
        }
      } finally {
        fm.close();
      }
    }

    if (!violations.isEmpty()) {
      for (int i = 0; i < violations.size(); i++) {
        err.println(violations.get(i));
      }
      return 1;
    }

    if (hadInternalFailure) {
      return 2;
    }

    return 0;
  }

  /**
   * Resolves the ignore file to use for the current run.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>the explicit path from {@code --ignore}
   *   <li>{@code .javawhitelistignore} in the current working directory
   *   <li>no ignore file
   * </ol>
   *
   * @param options parsed CLI options
   * @param cwd current working directory
   * @return resolved ignore file path or {@code null} if none should be used.
   */
  private static Path resolveIgnoreFile(CliOptions options, Path cwd) {
    if (options.ignorePath() != null) {
      return Paths.get(options.ignorePath());
    }

    Path defaultIgnore = cwd.resolve(".javawhitelistignore");
    if (Files.exists(defaultIgnore) && Files.isRegularFile(defaultIgnore)) {
      return defaultIgnore;
    }

    return null;
  }

  /**
   * Computes the subset of collected Java files that should be ignored.
   *
   * <p>All returned paths are absolute and normalized so they can be compared consistently against
   * source file paths reported by javac.
   *
   * @param javaFiles collected Java source files.
   * @param ignores ignore matcher to apply.
   * @return set of ignored absolute normalized path.
   */
  private static Set<Path> computeIgnoredFiles(List<Path> javaFiles, IgnoreMatcher ignores) {
    Set<Path> ignoredAbs = new HashSet<>();

    for (Path javaFile : javaFiles) {
      Path f = javaFile.toAbsolutePath().normalize();
      if (ignores != null && ignores.isIgnored(f)) {
        ignoredAbs.add(f);
      }
    }

    return ignoredAbs;
  }

  /**
   * Groups Java source files by their nearest Maven root.
   *
   * <p>The root for each file is the nearest ancestor directory containing a {@code pom.xml} file.
   * If no {@code pom.xml} is found, the file's parent directory is used.
   *
   * @param javaFiles collected Java source files.
   * @return map from compilation root to the files belonging to that group.
   */
  private static Map<Path, List<Path>> groupByPomRoot(List<Path> javaFiles) {
    Map<Path, List<Path>> groups = new LinkedHashMap<>();

    for (int i = 0; i < javaFiles.size(); i++) {
      Path abs = javaFiles.get(i).toAbsolutePath().normalize();
      Path root = findNearestPomRoot(abs);
      groups.computeIfAbsent(root, k -> new ArrayList<>()).add(abs);
    }

    return groups;
  }

  /**
   * Collects all {@code .java} files beneath a path into {@code out}.
   *
   * @param p file or directory to scan.
   * @param out output list of java source file paths.
   * @throws IOException on I/O error while walking the file tree.
   */
  private static void collectJavaFiles(Path p, List<Path> out) throws IOException {
    if (!Files.exists(p)) {
      return;
    }

    if (Files.isDirectory(p)) {
      try (var stream = Files.walk(p)) {
        stream.forEach(
            path -> {
              if (path.toString().endsWith(".java")) {
                out.add(path);
              }
            });
      }
      return;
    }

    if (p.toString().endsWith(".java")) {
      out.add(p);
    }
  }

  /**
   * Formats a compiler diagnostic into {@code file:line:col: message}.
   *
   * @param d diagnostic to format.
   * @return formatted diagnostic string.
   */
  private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> d) {
    String src = (d.getSource() == null) ? "<unknown>" : d.getSource().getName();
    return src + ":" + d.getLineNumber() + ":" + d.getColumnNumber() + ": " + d.getMessage(null);
  }

  /**
   * Finds the nearest ancestor directory (including the file's parent chain) that contains a
   * pom.xml file.
   *
   * <p>If no pom.xml file is found, fallback to the file's immediate parent directory.
   *
   * @param file Java source file path.
   * @return compilation group root for the file.
   */
  private static Path findNearestPomRoot(Path file) {
    Path cur = file.toAbsolutePath().normalize();
    if (!Files.isDirectory(cur)) {
      cur = cur.getParent();
    }

    while (cur != null) {
      if (Files.exists(cur.resolve("pom.xml")) && Files.isRegularFile(cur.resolve("pom.xml"))) {
        return cur;
      }
      cur = cur.getParent();
    }

    return file.toAbsolutePath().normalize().getParent();
  }
}
