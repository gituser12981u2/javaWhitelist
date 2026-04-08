package allowlist.build;

/** Java Source set for a compilation group. */
public enum SourceSetKind {

  /** Files under a main source root, such as {@code src/main/java}. */
  MAIN,

  /** Files under a main source root, such as {@code src/test/java}. */
  TEST,

  /** Files that do not match a known source set layout. */
  UNKNOWN,
}
