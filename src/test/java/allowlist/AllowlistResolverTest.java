package allowlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class AllowlistResolverTest {
  @TempDir Path tempDir;

  @Test
  public void explicitPathWinsOverAll() throws Exception {
    Path root = tempDir.resolve("repo");
    Path module = root.resolve("module-a");
    Files.createDirectories(module);

    Path explicit = tempDir.resolve("explicit-allowlist.txt");
    Files.writeString(explicit, "java.lang.String#length\n");

    Files.writeString(root.resolve("allowlist.txt"), "java.lang.String#trim\n");
    Files.writeString(module.resolve("allowlist.txt"), "java.lang.String#substring\n");

    AllowlistResolver resolver =
        new AllowlistResolver("Linux", tempDir.toString(), new HashMap<>());

    LoadSource src = resolver.resolveSource(explicit.toString(), module);

    assertTrue(src.isFile());
    assertEquals(explicit.toString(), src.filePath());
  }

  @Test
  public void moduleAllowlistWinsOverParentAllowlist() throws Exception {
    Path root = tempDir.resolve("repo");
    Path module = root.resolve("module-a");
    Files.createDirectories(module);

    Path parentAllow = root.resolve("allowlist.txt");
    Path moduleAllow = module.resolve("allowlist.txt");

    Files.writeString(parentAllow, "java.lang.String#length\n");
    Files.writeString(moduleAllow, "java.lang.String#substring\n");

    AllowlistResolver resolver =
        new AllowlistResolver("Linux", tempDir.toString(), new HashMap<>());

    LoadSource src = resolver.resolveSource(null, module);

    assertTrue(src.isFile());
    assertEquals(moduleAllow.toString(), src.filePath());
  }

  @Test
  public void parentAllowlistUsedWhenModuleDoesNotHaveOne() throws Exception {
    Path root = tempDir.resolve("repo");
    Path module = root.resolve("module-a");
    Files.createDirectories(module);

    Path parentAllow = root.resolve("allowlist.txt");
    Files.writeString(parentAllow, "java.lang.String#length\n");

    AllowlistResolver resolver =
        new AllowlistResolver("Linux", tempDir.toString(), new HashMap<>());

    LoadSource src = resolver.resolveSource(null, module);

    assertTrue(src.isFile());
    assertEquals(parentAllow.toString(), src.filePath());
  }

  @Test
  public void universalConfigUsedWhenNoProjectAllowlistExists() throws Exception {
    Path root = tempDir.resolve("repo");
    Path module = root.resolve("module-a");
    Files.createDirectories(module);

    Path universal = tempDir.resolve(".config").resolve("javaWhitelist").resolve("allowlist.txt");
    Files.createDirectories(universal.getParent());
    Files.writeString(universal, "java.lang.String#length\n");

    AllowlistResolver resolver =
        new AllowlistResolver("Linux", tempDir.toString(), new HashMap<>());

    LoadSource src = resolver.resolveSource(null, module);

    assertTrue(src.isFile());
    assertEquals(universal.toString(), src.filePath());
  }

  @Test
  public void osSpecificConfigUsedWhenNoProjectOrUniversalAllowlistExists() throws Exception {
    Path root = tempDir.resolve("repo");
    Path module = root.resolve("module-a");
    Files.createDirectories(module);

    Map<String, String> env = new HashMap<>();
    Path xdg = tempDir.resolve("xdg-config");
    env.put("XDG_CONFIG_HOME", xdg.toString());

    Path osSpecific = xdg.resolve("javaWhitelist").resolve("allowlist.txt");
    Files.createDirectories(osSpecific.getParent());
    Files.writeString(osSpecific, "java.lang.String#length\n");

    AllowlistResolver resolver = new AllowlistResolver("Linux", tempDir.toString(), env);

    LoadSource src = resolver.resolveSource(null, module);

    assertTrue(src.isFile());
    assertEquals(osSpecific.toString(), src.filePath());
  }

  @Test
  public void bundledResourceUsedWhenNoFileBasedAllowlistExists() throws Exception {
    Path root = tempDir.resolve("repo");
    Path module = root.resolve("module-a");
    Files.createDirectories(module);

    AllowlistResolver resolver =
        new AllowlistResolver("Linux", tempDir.toString(), new HashMap<>());

    LoadSource src = resolver.resolveSource(null, module);

    assertTrue(!src.isFile());
    assertEquals("allowlist.txt", src.resourceName());
  }
}
