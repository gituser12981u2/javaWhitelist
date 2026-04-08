package allowlist.build;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Cheap heuristic for deciding whether source files appear to require external dependencies on the
 * javac classpath.
 *
 * <p>This scans package and import declarations near the tops of source files and avoids resolving
 * build-tool dependency classpaths when sources appear to depend only on JDK classes and
 * project-local packages.
 *
 * <p>This is intentionally a heuristic. It may miss cases where external types are referenced via
 * fully qualified names without imports.
 */
public final class ImportDependencyHeuristic {

  /**
   * Returns true if the group appears to require external classpath entries.
   *
   * <p>This overload collects package declarations from the group's own files only.
   *
   * @param group compilation group to inspect.
   * @return {@code true} if the group appears to require external classpath entries.
   * @throws IOException if file reading fails.
   */
  public boolean needsExternalClasspath(CompilationGroup group) throws IOException {
    Objects.requireNonNull(group, "group");
    Set<String> projectPackages = collectDeclaredPackages(group.files());
    return needsExternalClasspath(group.files(), projectPackages);
  }

  /**
   * Returns true if the given files appear to require external classpath entries.
   *
   * <p>The supplied {@code projectPackages} should typically be collected across all project files,
   * not only the current compilation group, so project-local cross-group imports are not mistaken
   * for external dependencies.
   *
   * @param files source files to inspect.
   * @param projectPackages known project-local package names.
   * @return {@code true} if the files appear to require external classpath entries.
   * @throws IOException if file reading fails.
   */
  public boolean needsExternalClasspath(List<Path> files, Set<String> projectPackages)
      throws IOException {
    Objects.requireNonNull(files, "files");
    Objects.requireNonNull(projectPackages, "projectPackages");

    for (Path file : files) {
      if (file == null || !Files.isRegularFile(file)) {
        continue;
      }

      if (fileHasExternalImport(file, projectPackages)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Collects declared package names from the supplied files.
   *
   * <p>This is intended for building a project-wide package index so project-local imports can be
   * recognized across compilation groups.
   *
   * @param files source files to inspect.
   * @return declared package names found in the files.
   * @throws IOException if file reading fails.
   */
  public Set<String> collectDeclaredPackages(List<Path> files) throws IOException {
    Objects.requireNonNull(files, "files");

    Set<String> packages = new HashSet<>();

    for (Path file : files) {
      if (file == null || !Files.isRegularFile(file)) {
        continue;
      }

      String pkg = readPackageDeclaration(file);
      if (pkg != null && !pkg.isBlank()) {
        packages.add(pkg);
      }
    }

    return packages;
  }

  private static String readPackageDeclaration(Path file) throws IOException {
    HeaderScan scan = scanHeader(file);
    return scan.packageName();
  }

  private static boolean fileHasExternalImport(Path file, Set<String> projectPackages)
      throws IOException {
    HeaderScan scan = scanHeader(file);
    for (String imported : scan.imports()) {
      if (isExternalImport(imported, projectPackages)) {
        return true;
      }
    }
    return false;
  }

  private static HeaderScan scanHeader(Path file) throws IOException {
    String packageName = null;
    List<String> imports = new ArrayList<>();
    boolean[] inBlockComment = new boolean[] {false};

    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;
      while ((line = reader.readLine()) != null) {
        String s = stripComments(line, inBlockComment).trim();
        if (s.isEmpty()) {
          continue;
        }

        if (s.startsWith("package ")) {
          String rest = s.substring("package ".length()).trim();
          int semi = rest.indexOf(';');
          if (semi >= 0) {
            rest = rest.substring(0, semi).trim();
          }
          packageName = rest.isEmpty() ? null : rest;
          continue;
        }

        if (s.startsWith("import ")) {
          String imported = parseImportedName(s);
          if (imported != null) {
            imports.add(imported);
          }
          continue;
        }

        if (startsTopLevelTypeOrMember(s)) {
          break;
        }
      }
    }

    return new HeaderScan(packageName, List.copyOf(imports));
  }

  private static String parseImportedName(String line) {
    String s = line.trim();
    if (!s.startsWith("import ")) {
      return null;
    }

    s = s.substring("import ".length()).trim();

    if (s.startsWith("static ")) {
      s = s.substring("static ".length()).trim();
    }

    if (s.endsWith(";")) {
      s = s.substring(0, s.length() - 1).trim();
    }

    return s.isEmpty() ? null : s;
  }

  private static boolean isExternalImport(String imported, Set<String> projectPackages) {
    if (imported == null || imported.isBlank()) {
      return false;
    }

    if (imported.startsWith("java.")
        || imported.startsWith("javax.")
        || imported.startsWith("jakarta.")) {
      return false;
    }

    if (imported.equals("java") || imported.equals("javax") || imported.equals("jakarta")) {
      return false;
    }

    for (String pkg : projectPackages) {
      if (imported.equals(pkg) || imported.startsWith(pkg + ".")) {
        return false;
      }
    }

    return true;
  }

  private static boolean startsTopLevelTypeOrMember(String s) {
    return s.startsWith("class ")
        || s.startsWith("interface ")
        || s.startsWith("enum ")
        || s.startsWith("record ")
        || s.startsWith("@interface ")
        || s.startsWith("public ")
        || s.startsWith("protected ")
        || s.startsWith("private ")
        || s.startsWith("abstract ")
        || s.startsWith("final ")
        || s.startsWith("sealed ")
        || s.startsWith("non-sealed ");
  }

  private static String stripComments(String s, boolean[] inBlockComment) {
    StringBuilder out = new StringBuilder();
    int i = 0;

    while (i < s.length()) {
      char c = s.charAt(i);
      char next = (i + 1 < s.length()) ? s.charAt(i + 1) : '\0';

      if (inBlockComment[0]) {
        if (c == '*' && next == '/') {
          inBlockComment[0] = false;
          i += 2;
        } else {
          i++;
        }
        continue;
      }

      if (c == '/' && next == '*') {
        inBlockComment[0] = true;
        i += 2;
        continue;
      }

      if (c == '/' && next == '/') {
        break;
      }

      out.append(c);
      i++;
    }

    return out.toString();
  }

  private record HeaderScan(String packageName, List<String> imports) {}
}
