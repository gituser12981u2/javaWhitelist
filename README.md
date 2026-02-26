# JavaWhitelist

Static analysis CLI for enforcing a restricted Java subset and API allowlist.

Designed for course environments where certain langauge features
and library APIs are forbidden.

The default whitelist is setup for UW Seattle's CSE 122.

## Features 

- API allowlist enforcement (OWNER#member)
- Prefix-based enforcement (e.g. java.,javax.)
- Optional syntax bans:
    - break
    - continue
    - switch
    - try/catch
    - return from void
    - package declarations
    - enhanced for loop over Queues and Stacks
    - non-wildcard imports
    - null literal restriction
- Fully configurable via allowlist.txt
- Usable with CI pipelines

## Install

macOS/Linux
```bash
curl -fsSL https://raw.githubusercontent.com/gituser12981u2/javaWhitelist/main/install.sh | bash
```

Windows (PowerShell)
```PowerShell
iwr -useb https://raw.githubusercontent.com/gituser12981u2/javaWhitelist/main/install.ps1 | iex
```

## Usage

```bash
javaWhitelist [-allowlist allowlist.txt] <paths...>
```

Example
```bash
javaWhitelist --allowlist allowlist.txt src/
```

## Allowlist Configuration

Example allowlist.txt
```txt
@ENFORCE_PREFIXES=java.,javax.
@DISALOW_NULL_LITERAL=true

java.lang.String#length
java.lang.Integer#parseInt
```

Supported settings:
- ENFORCE_PREFIXES
- DISALLOW_NULL_LITERAL
- DISALLOW_RETURN_FROM_VOID
- DISALLOW_BREAK
- DISALLOW_CONTINUE
- DISALLOW_SWITCH
- DISALLOW_TRY
- DISALLOW_ENHANCED_FORLOOP_OVER_STACK_OR_QUEUE
- REQUIRE_WILDCARD_IMPORTS

## CI Integration Example 

```yaml
- name: Download javaWhitelist
  run: |
    curl -L https://github.com/gituser12981u2/javaWhitelist/releases/latest/download/javaWhitelist.jar -o javaWhitelist.jar

- name: Run whitelist checker
  run: |
    java -jar javaWhitelist.jar src/
```
