package allowlist;

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
 * CLI entry point for the Java API and style whitelist checker.
 *
 * <h3>Usage</h3>
 *
 * {@code java -jar target/javaWhitelist [--allowlist path/to/allowlist.txt] [--ignore
 * path/to/.javawhitelistignore] <.java file or directory> ...}
 *
 * <h3>Allowlist resolution</h3>
 *
 * <ol>
 *   <li>If {@code --allowlist PATH} is provided, that file is used.
 *   <li>Otherwise, check the universal path {@code ~/.config/javaWhitelist/allowlist.txt} (all
 *       OSes).
 *   <li>Otherwise, check the OS-specific config path:
 *       <ul>
 *         <li>Windows: {@code %APPDATA%\javaWhitelist\allowlist.txt} else home roaming fallback
 *         <li>macOS: {@code ~/Library/Application Support/javaWhitelist/allowlist.txt}
 *         <li>Linux/Unix: {@code $XDG_CONFIG_HOME/javaWhitelist/allowlist.txt} else {@code
 *             ~/.config/...}
 *       </ul>
 *   <li>Otherwise, fallback to the bundled classpath resource {@code allowlist.txt}.
 * </ol>
 *
 * <h3>Ignore file</h3>
 *
 * <p>Input paths are scanned for {@code .java} files, optionally excluding files matched by an
 * ignore file (gitignore-style glob patterns).
 *
 * <p>Ignore file resolution:
 *
 * <ol>
 *   <li>If {@code --ignore PATH} is provided, that file is used.
 *   <li>Otherwise, if {@code .javawhitelistignore} exists in the current working directory, it is
 *       used.
 *   <li>Otherwise, no ignore filtering is applied.
 * </ol>
 *
 * <h3>Exit codes</h3>
 *
 * <ul>
 *   <li>{@code 0}: no violations
 *   <li>{@code 1}: violations found
 *   <li>{@code 2}: usage error or internal/tool failure
 * </ul>
 */
public final class AllowlistChecker {

  /** Default bundled resource name used when no config file is found. */
  private static final String DEFAULT_ALLOWLIST_RESOURCE = "allowlist.txt";

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      printHelp();
      System.exit(2);
    }

    if (args.length == 1) {
      if (args[0].equals("--help") || args[0].equals("-h")) {
        printHelp();
        System.exit(0);
      }

      if (args[0].equals("--version") || args[0].equals("-v")) {
        System.out.println("javaWhitelist v" + version());
        System.exit(0);
      }
    }

    System.exit(run(args, System.err));
  }

  private static void printHelp() {
    System.out.println("javaWhitelist - Java API + style whitelist checker");
    System.out.println();
    System.out.println("Usage:");
    System.out.println(
        "  javaWhitelist [--allowlist path/to/allowlist.txt] <file-or-directory>...");
    System.out.println();
    System.out.println("Options:");
    System.out.println("  --help, -h        Show this help message");
    System.out.println("  --version, -v     Show version");
    System.out.println("  --allowlist PATH  Use a custom allowlist file (overrides config lookup)");
    System.out.println(
        "  --ignore PATH    Ignore file (overrides default .javawhitelistignore lookup)");
    System.out.println();
    System.out.println("Exit codes:");
    System.out.println("  0  No violations");
    System.out.println("  1  Violations found");
    System.out.println("  2  Usage or internal error");
  }

  /**
   * Runs the checker and returns the process exit code.
   *
   * @param args CLI arguments.
   * @param err output stream used for diagnostics.
   * @return exit code (0/1/2).
   * @throws IOException if file traversal or compiler I/O fails.
   */
  static int run(String[] args, PrintStream err) throws IOException {
    if (args.length < 1) {
      usageAndExit();
    }

    String allowlistPath = null;
    String ignorePath = null;
    List<String> targetPaths = new ArrayList<>();

    // Parse args: --allowlist <path> (optional)
    // remaining args are target paths (files/dirs)
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--allowlist")) {
        if (i + 1 >= args.length) {
          usageAndExit();
        }

        allowlistPath = args[i + 1];
        i++;
      } else if (args[i].equals("--ignore")) {
        if (i + 1 >= args.length) {
          usageAndExit();
        }

        ignorePath = args[i + 1];
        i++;
      } else {
        targetPaths.add(args[i]);
      }
    }

    if (targetPaths.isEmpty()) {
      usageAndExit();
    }

    Path cwd = Paths.get("").toAbsolutePath().normalize();
    IgnoreMatcher ignores = IgnoreMatcher.empty(cwd);

    // Resolve ignore file with CWD default, unless --ignore
    Path ignoreFile = null;
    if (ignorePath != null) {
      ignoreFile = Paths.get(ignorePath);
    } else {
      Path defaultIgnore = cwd.resolve(".javawhitelistignore");
      if (Files.exists(defaultIgnore) && Files.isRegularFile(defaultIgnore)) {
        ignoreFile = defaultIgnore;
      }
    }

    if (ignoreFile != null) {
      try {
        ignores = IgnoreMatcher.fromFile(ignoreFile);
      } catch (IOException e) {
        err.println("Failed to read ignore file: " + ignoreFile + ": " + e.getMessage());
        return 2;
      }
    }

    AllowlistConfig config;
    try {
      // Resolve allowlist source
      String userHome = System.getProperty("user.home");
      String osName = System.getProperty("os.name");
      LoadSource src = resolveAllowlistSource(allowlistPath, osName, userHome);

      if (src.isFile()) {
        config = AllowlistLoader.loadFromFile(src.filePath());
      } else {
        config = AllowlistLoader.loadFromResource(src.resourceName());
      }
    } catch (FileNotFoundException e) {
      err.println("Allowlist file not found: " + allowlistPath);
      return 2;
    } catch (IOException e) {
      err.println("Failed to load allowlist: " + e.getMessage());
      return 2;
    }

    List<Path> javaFiles = new ArrayList<>();
    for (int i = 0; i < targetPaths.size(); i++) {
      collectJavaFiles(Paths.get(targetPaths.get(i)), javaFiles);
    }

    if (javaFiles.isEmpty()) {
      err.println("No .java files found in the provided paths.");
      return 2;
    }

    // Precompute ignored set
    Set<Path> ignoredAbs = new HashSet<>();
    for (int i = 0; i < javaFiles.size(); i++) {
      Path f = javaFiles.get(i).toAbsolutePath().normalize();
      if (ignores != null && ignores.isIgnored(f)) {
        ignoredAbs.add(f);
      }
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      err.println("No system Java compiler found.");
      return 2;
    }

    DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
    StandardJavaFileManager fm =
        compiler.getStandardFileManager(diags, null, StandardCharsets.UTF_8);

    Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(javaFiles);

    // Avoid annotation processing
    List<String> options = Arrays.asList("-proc:none");

    JavacTask task = (JavacTask) compiler.getTask(null, fm, diags, options, null, units);

    Iterable<? extends CompilationUnitTree> parsed;
    try {
      parsed = task.parse();
      task.analyze();
    } catch (IOException ex) {
      err.println("Failed to parse/analyze sources: " + ex.getMessage());
      return 2;
    }

    Trees trees = Trees.instance(task);
    Types types = task.getTypes();
    Elements elements = task.getElements();

    List<String> violations = new ArrayList<>();

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

      err.println("Compile error: " + formatDiagnostic(d));
    }

    if (!violations.isEmpty()) {
      for (int i = 0; i < violations.size(); i++) {
        err.println(violations.get(i));
      }
      return 1;
    }

    return 0;
  }

  /**
   * Returns the implementation version from the JAR manifest when available.
   *
   * @return version string of {@code "dev"} if unavailable.
   */
  private static String version() {
    Package p = AllowlistChecker.class.getPackage();
    String v = (p == null) ? null : p.getImplementationVersion();
    return (v == null || v.isBlank()) ? "dev" : v;
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

  /**
   * Resolves which allowlist source to load, based on CLI and config lookup.
   *
   * <p>Precedence:
   *
   * <ol>
   *   <li>Explicit CLI path
   *   <li>Universal config path ({@code ~/.config/...})
   *   <li>OS specific config path
   *   <li>Bundled resource
   * </ol>
   *
   * @param explicitPath allowlist path from {@code --allowlist}, or {@code null}.
   * @param osName OS name.
   * @param userHome home directory.
   * @return resolved load source (file or resource).
   */
  private static LoadSource resolveAllowlistSource(
      String explicitPath, String osName, String userHome) {
    if (explicitPath != null) {
      return LoadSource.file(explicitPath);
    }

    Path p1 = PathResolver.universalPath(userHome);
    if (isReadableFile(p1)) {
      return LoadSource.file(p1.toString());
    }

    Path p2 = PathResolver.osSpecificPath(osName, userHome, System.getenv());
    if (isReadableFile(p2)) {
      return LoadSource.file(p2.toString());
    }

    return LoadSource.resource(DEFAULT_ALLOWLIST_RESOURCE);
  }

  /** Prints usage and terminates the process with exit code 2. */
  private static void usageAndExit() {
    System.err.println(
        "Use java allowlist.AllowlistChecker [--allowlist allowlist.txt] <file or directory> ...");
    System.exit(2);
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
}
