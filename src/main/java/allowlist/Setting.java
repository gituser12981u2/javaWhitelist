package allowlist;

public enum Setting {
    ENFORCE_PREFIXES("ENFORCE_PREFIXES"), // string-value

    DISALLOW_NULL_LITERAL("DISALLOW_NULL_LITERAL"),
    DISALLOW_RETURN_FROM_VOID("DISALLOW_RETURN_FROM_VOID"),
    DISALLOW_BREAK("DISALLOW_BREAK"),
    DISALLOW_CONTINUE("DISALLOW_CONTINUE"),
    DISALLOW_SWITCH("DISALLOW_SWITCH"),
    DISALLOW_TRY("DISALLOW_TRY"),
    REQUIRE_WILDCARD_IMPORTS("REQUIRE_WILDCARD_IMPORTS");

    private final String key;

    Setting(String key) {
        this.key = key;
    }

    public static Setting fromKey(String key) {
        for (Setting s : values()) {
            if (s.key.equals(key)) {
                return s;
            }
        }

        return null;
    }
}
