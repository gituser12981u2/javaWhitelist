package allowlist;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;

/**
 * CLI entry point
 *
 * Usage:
 * java -jar javawhitelist-1.0.0 [--allowlist path/to/allowlist.txt] <.java file
 * or directory> ...
 *
 * Behavior:
 * - If --allowlist is provided, loads allowlist from that file path.
 * - Otherwise, loads allowlist from classpath resource: "allowlist.txt"
 *
 * Exit codes:
 * 0 = OK
 * 1 = violations found
 * 2 = usage/tool failure
 */
public final class AllowlistChecker {

    private static final String DEFAULT_ALLOWLIST_RESOURCE = "allowlist.txt";

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            printHelp();
            System.exit(2);
        }

        if (args.length == 1) {
            if (args[0].equals("--help") || args[0].equals("-h")) {
                printHelp();
                ;
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
        System.out.println("  javaWhitelist [--allowlist path/to/allowlist.txt] <file-or-directory>...");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --help, -h        Show this help message");
        System.out.println("  --version, -v     Show version");
        System.out.println("  --allowlist PATH  Use a custom allowlist file");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0  No violations");
        System.out.println("  1  Violations found");
        System.out.println("  2  Usage or internal error");
    }

    static int run(String[] args, PrintStream err) throws IOException {
        if (args.length < 1) {
            usageAndExit();
        }

        String allowlistPath = null;
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
            } else {
                targetPaths.add(args[i]);
            }
        }

        if (targetPaths.isEmpty()) {
            usageAndExit();
        }

        AllowlistConfig config;
        try {
            if (allowlistPath != null) {
                config = AllowlistLoader.loadFromFile(allowlistPath);
            } else {
                config = AllowlistLoader.loadFromResource(DEFAULT_ALLOWLIST_RESOURCE);
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

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            err.println("No system Java compiler found.");
            return 2;
        }

        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        StandardJavaFileManager fm = compiler.getStandardFileManager(diags, null, StandardCharsets.UTF_8);

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
            new CheckerScanner(trees, types, elements, cu, violations, config).scan(cu, null);
        }

        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                err.println("Compile error: " + formatDiagnostic(d));
            }
        }

        if (!violations.isEmpty()) {
            for (int i = 0; i < violations.size(); i++) {
                err.println(violations.get(i));
            }
            return 1;
        }

        return 0;
    }

    private static String version() {
        Package p = AllowlistChecker.class.getPackage();
        String v = (p == null) ? null : p.getImplementationVersion();
        return (v == null || v.isBlank()) ? "dev" : v;
    }

    private static void usageAndExit() {
        System.err.println(
                "Usage: java allowlist.AllowlistChecker [--allowlist allowlist.txt] <.java file or directory> ...");
        System.exit(2);
    }

    private static void collectJavaFiles(Path p, List<Path> out) throws IOException {
        if (!Files.exists(p)) {
            return;
        }

        if (Files.isDirectory(p)) {
            Files.walk(p).forEach(path -> {
                if (path.toString().endsWith(".java")) {
                    out.add(path);
                }
            });
        } else if (p.toString().endsWith(".java")) {
            out.add(p);
        }
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> d) {
        String src = (d.getSource() == null) ? "<unknown>" : d.getSource().getName();
        return src + ":" + d.getLineNumber() + ":" + d.getColumnNumber() + ": " + d.getMessage(null);
    }

}
