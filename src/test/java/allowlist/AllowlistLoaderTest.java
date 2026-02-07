package allowlist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public final class AllowlistLoaderTest {
    @Test
    public void parsesSettingsAndEntries() throws Exception {
        String txt = "@ENFORCE_PREFIXES=java.,javax.\n" +
                "\n" +
                "# comment\n" +
                "java.lang.String#length\n" +
                "java.lang.Integer#parseInt\n";

        ByteArrayInputStream in = new ByteArrayInputStream(txt.getBytes(StandardCharsets.UTF_8));
        AllowlistConfig cfg = AllowlistLoader.loadFromStreamForTests(in);

        assertTrue(cfg.shouldEnforceOwner("java.lang.String"), "should enforce java.*");
        assertTrue(cfg.shouldEnforceOwner("javax.swing.JButton"), "should enforce javax.*");
        assertFalse(cfg.shouldEnforceOwner("my.project.Foo"), "should not enforce user code");

        assertTrue(cfg.isAllowed("java.lang.String", "length"));
        assertTrue(cfg.isAllowed("java.lang.Integer", "parseInt"));
        assertFalse(cfg.isAllowed("java.lang.String", "trim"));
    }

    @Test
    public void missingPrefixesMeansEnforceNothing() throws Exception {
        String txt = "java.lang.String#length\n";

        ByteArrayInputStream in = new ByteArrayInputStream(txt.getBytes(StandardCharsets.UTF_8));
        AllowlistConfig cfg = AllowlistLoader.loadFromStreamForTests(in);

        assertFalse(cfg.shouldEnforceOwner("java.lang.String"), "no prefixes should enforce nothing");
        assertFalse(cfg.shouldEnforceOwner("javax.swing.JButton"), "no prefixes should enforce nothing");
    }
}
