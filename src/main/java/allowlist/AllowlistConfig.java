package allowlist;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class AllowlistConfig {
  private final Set<String> allowedKeys;
  private final List<String> enforcedOwnerPrefixes;
  private final EnumSet<Setting> settings;

  public AllowlistConfig(
      Set<String> allowedKeys, List<String> enforcedOwnerPrefixes, EnumSet<Setting> settings) {
    this.allowedKeys = allowedKeys;
    this.enforcedOwnerPrefixes = enforcedOwnerPrefixes;
    this.settings = settings;
  }

  public boolean has(Setting s) {
    return settings.contains(s);
  }

  public boolean shouldEnforceOwner(String owner) {
    for (int i = 0; i < enforcedOwnerPrefixes.size(); i++) {
      String pref = enforcedOwnerPrefixes.get(i);
      if (owner.startsWith(pref)) {
        return true;
      }
    }

    return false;
  }

  public boolean isAllowed(String owner, String member) {
    return allowedKeys.contains(owner + "#" + member);
  }

  public boolean disallowNullLiteral() {
    return has(Setting.DISALLOW_NULL_LITERAL);
  }

  public boolean disallowReturnFromVoid() {
    return has(Setting.DISALLOW_RETURN_FROM_VOID);
  }

  public boolean disallowBreak() {
    return has(Setting.DISALLOW_BREAK);
  }

  public boolean disallowContinue() {
    return has(Setting.DISALLOW_CONTINUE);
  }

  public boolean disallowSwitch() {
    return has(Setting.DISALLOW_SWITCH);
  }

  public boolean disallowTry() {
    return has(Setting.DISALLOW_TRY);
  }

  public boolean disallowEnhancedForloopOverStackOrQueue() {
    return has(Setting.DISALLOW_ENHANCED_FORLOOP_OVER_STACK_OR_QUEUE);
  }

  public boolean requireWildcardImports() {
    return has(Setting.REQUIRE_WILDCARD_IMPORTS);
  }
}
