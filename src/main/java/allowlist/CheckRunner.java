package allowlist;

import allowlist.build.CompilationPlan;
import allowlist.build.CompilationPlanner;
import allowlist.cli.CliOptions;
import allowlist.fs.IgnoreMatcher;
import allowlist.fs.JavaFileCollector;
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
import java.util.HashSet;
import java.util.List;
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
 *   <li>resolve and load the effective allowlist for each group
 *   <li>parse and analyze each group with javac
 *   <li>scan ASTs and violations
 *   <li>print warnings and violations and return an exit code
 * </ol>
 */
public final class CheckRunner {
  private final AllowlistResolver resolver;
  private final CompilationPlanner compilationPlanner;
  private final JavaFileCollector javaFileCollector = new JavaFileCollector();

  /**
   * Creates a new runner.
   *
   * @param resolver resolver used to locate and load allowlist configurations.
   * @param compilationPlanner planner used to prepare compilation plans.
   */
  public CheckRunner(AllowlistResolver resolver, CompilationPlanner compilationPlanner) {
    this.resolver = resolver;
    this.compilationPlanner = compilationPlanner;
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
    long totalStart = nowNanos();

    long t = nowNanos();
    Path cwd = Paths.get("").toAbsolutePath().normalize();
    IgnoreMatcher ignores = IgnoreMatcher.empty(cwd);
    debugTiming(err, "init cwd/ignore matcher", t);

    t = nowNanos();
    Path ignoreFile = resolveIgnoreFile(options, cwd);
    if (ignoreFile != null) {
      try {
        ignores = IgnoreMatcher.fromFile(ignoreFile);
      } catch (IOException e) {
        err.println("Failed to read ignore file: " + ignoreFile + ": " + e.getMessage());
        return 2;
      }
    }
    debugTiming(err, "resolve ignore file", t);

    t = nowNanos();
    List<Path> javaFiles = javaFileCollector.collect(options.targetPaths());
    debugTiming(err, "collect java files", t);

    if (javaFiles.isEmpty()) {
      err.println("No .java files found in the provided paths.");
      return 2;
    }

    t = nowNanos();
    Set<Path> ignoredAbs = computeIgnoredFiles(javaFiles, ignores);
    debugTiming(err, "compute ignored files", t);

    List<CompilationPlan> plans;
    t = nowNanos();
    try {
      plans = compilationPlanner.plan(javaFiles);
    } catch (IOException e) {
      err.println("Failed to build compilation plans: " + e.getMessage());
      return 2;
    }
    debugTiming(err, "build compilation plans", t);

    if (plans.isEmpty()) {
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

    for (CompilationPlan plan : plans) {
      long planStart = nowNanos();

      boolean planFailed =
          runPlan(compiler, plan, options.allowlistPath(), ignoredAbs, violations, err);

      debugTiming(
          err, "run plan[" + plan.projectRoot() + ", " + plan.sourceSetKind() + "]", planStart);

      if (planFailed) {
        hadInternalFailure = true;
      }
    }

    debugTiming(err, "total run", totalStart);

    if (!violations.isEmpty()) {
      for (String violation : violations) {
        err.println(violation);
      }
      return 1;
    }

    if (hadInternalFailure) {
      return 2;
    }

    return 0;
  }

  /**
   * Runs a single compilation plan through javac parsing, analysis, and AST scanning.
   *
   * @param compiler system Java compiler.
   * @param plan prepared compilation plan.
   * @param explicitAllowlistPath explicit allowlist path from CLI or {@code null}.
   * @param ignoredAbs ignored source files as absolute normalized paths.
   * @param violations sink for accumulated violations.
   * @param err stream used for diagnostics.
   * @return true if an internal parse/analyze failure occurred for this plan; false otherwise.
   */
  private boolean runPlan(
      JavaCompiler compiler,
      CompilationPlan plan,
      String explicitAllowlistPath,
      Set<Path> ignoredAbs,
      List<String> violations,
      PrintStream err) {
    long t = nowNanos();

    AllowlistConfig config;
    try {
      config = resolver.resolveAndLoad(explicitAllowlistPath, plan.projectRoot());
    } catch (FileNotFoundException e) {
      err.println("Allowlist file not found: " + explicitAllowlistPath);
      return true;
    } catch (IOException e) {
      err.println(
          "Failed to load allowlist for project root "
              + plan.projectRoot()
              + ": "
              + e.getMessage());
      return true;
    }
    debugTiming(err, " allowlist load", t);

    DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
    StandardJavaFileManager fm =
        compiler.getStandardFileManager(diags, null, StandardCharsets.UTF_8);

    try {
      t = nowNanos();
      Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(plan.files());
      JavacTask task =
          (JavacTask) compiler.getTask(null, fm, diags, plan.javacOptions(), null, units);
      debugTiming(err, " javac task setup", t);

      Iterable<? extends CompilationUnitTree> parsed;
      t = nowNanos();
      try {
        parsed = task.parse();
        task.analyze();
      } catch (IOException ex) {
        err.println("Failed to parse/analyze sources: " + ex.getMessage());
        return true;
      }
      debugTiming(err, " javac parse+analyze", t);

      Trees trees = Trees.instance(task);
      Types types = task.getTypes();
      Elements elements = task.getElements();

      t = nowNanos();
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
      debugTiming(err, " AST scan", t);

      t = nowNanos();
      emitDiagnostics(diags, ignoredAbs, err);
      debugTiming(err, " emit diagnostics", t);

      return false;
    } finally {
      try {
        fm.close();
      } catch (IOException e) {
        err.println("Warning: failed to close file manager: " + e.getMessage());
      }
    }
  }

  /**
   * Emits javac error diagnostics for non-ignored files.
   *
   * @param diags diagnostics collected during compilation.
   * @param ignoredAbs ignored source files as absolute normalized paths.
   * @param err stream used for output.
   */
  private static void emitDiagnostics(
      DiagnosticCollector<JavaFileObject> diags, Set<Path> ignoredAbs, PrintStream err) {
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
   * Formats a compiler diagnostic into {@code file:line:col: message}.
   *
   * @param d diagnostic to format.
   * @return formatted diagnostic string.
   */
  private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> d) {
    String src = (d.getSource() == null) ? "<unknown>" : d.getSource().getName();
    return src + ":" + d.getLineNumber() + ":" + d.getColumnNumber() + ": " + d.getMessage(null);
  }

  private static long nowNanos() {
    return System.nanoTime();
  }

  private static long elapsedMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000L;
  }

  private static void debugTiming(PrintStream err, String phase, long startNanos) {
    err.println("[debug] " + phase + ": " + elapsedMillis(startNanos) + " ms");
  }
}
