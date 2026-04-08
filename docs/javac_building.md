# RFC: Fast javac Options Resolution for Allowlist Analysis

## 1. Purpose

This document records the current architecture and intent of the Java
compilation-planning path used by the allowlist checker.

The main goal is to keep common-case analysis fast while still supporting projects that require build-tool dependency resolution.

In practice, the expensive part is no javac AST scanning but, rather,
the dependency classpath resolution through Maven, which on a cache miss
is roughly:

- about 700-900 ms for `dependency:build-classpath`
- versus 170-220 ms for parse/analyze on simple, no-dependency projects

Thus the architecture is designed to avoid build-tool dependency resolution
unless it is actually needed.

## Problem

When the checker always resolves the Maven dependency classpath up front,
total runtime increases sharply.

Observed shape:

- no dependency classpath resolution:
  - total run around 220 ms
- dependency classpath resolution vs Maven:
  - total run around 1 s

The dominant cost is maven subprocess lifetime instead of AST
scanning or file reading.

## Design Goals

1. Avoid Maven/Gradle depndency resolution on the common path.
2. Preserve correctness for project that do require third-party symbols.
3. Keep `CompilationPlanner` free of build-tool-specific layout logic.
4. Keep the architecture readable and easy to extend.
5. Allow a robust retry path when the lexical heuristic misses a dependency need.

High-Level Architecture

There are three conceptual buckets of javac inputs:

1. Source root

These are project source directories, for example:

- src/main/java
- src/test/java

They are cheap to derive from project layout.

1. Output directories

These are already-compiled projects outputs, for example:

- target/classes
- target/test-classes
- build/classes/java/main

These are also cheap to derive from project layout.

1. Dependency classpath entries

These are third-party jars or equivalent dependency artifacts resolved by the build tool.

Examples:

- JUnit Jupiter
- Mockito
- Guava
- Jackson

These are expensive to resolve and usually require a build-tool call.

## Core Interfaces

### JavacOptionsResolver

This abstraction owns build-tool-specific logic for converting a project root and source set into javac inputs.

It exposes:

- `resolveSourceRoots(...)`
- `resolveOutputDirectories(...)`
- `resolveDependencyClasspathEntries(...)`

And it provides default assembly helpers:

- `resolveLocalJavacOptions(...)`
- `resolveJavacOptions(...)`

### Current Maven Behavior

#### Source roots

For Maven projects:

- MAIN uses src/main/java
- TEST uses src/test/java and src/main/java
- other/mixed modes may include both

This lets test analysis resolve main project sources without immediately needing dependency jars.

#### Output directories

Currently:

- TEST may include target/classes if it exists

This allows already-built main project classes to help resolve test references cheaply.

#### Dependency classpath

Dependency classpath entries are resolved by running Maven:

- `dependency:build-classpath`
- `scope compile for main`
- `scope test for test`

This is the slow path the architecture tries to avoid.

### Planning Strategy

CompilationPlanner currently performs:

1. group Java files into compilation groups
2. collect declared packages across all input Java files
3. request resolveLocalJavacOptions(...)
4. run an import-based heuristic
5. if imports suggest third-party dependencies, replace local options with full options
6. append -proc:none

This means local options are always available and full options are used only when the heuristic indicates they are likely necessary.

### Import-Based Heuristic

#### Purpose

The import heuristic avoids dependency resolution for projects that only use:

- JDK imports
- project-local package imports

and no visible third-party imports.

#### Behavior

The heuristic scans only the header region of each file:

- package ...
- import ...
- stops at the first top-level declaration

It treats as non-external:

- java.*
- javax.*
- jakarta.*
- imports under known project-local packages

It treats as external:

- any other imported package

##### Why the project-wide package set matters

A test file may import a main package from the same project:

```java
import example.core.App;
```

If the package index is built only from the current group, this can be misclassified as external.

To avoid that, the planner computes declared packages across all collected Java files and passes that set to the heuristic.

#### Why the Heuristic Is Not Enough

The heuristic is intentionally lexical and import-based.

It will miss cases such as:

- fully-qualified third-party types used directly in code
- no import statement present
- external type names in certain unusual code forms

Example:

```java
org.junit.jupiter.api.Test t;
```

with no import.

The heuristic is only the fast first filter.

Correctness is preserved by the fallback path.

#### Fallback Strategy

After planning and initial analysis, `CheckRunner` should detect likely unresolved external-type failures and retry with full dependency-aware options when appropriate.

##### Intended flow

1. run plan with local options
2. if diagnostics suggest missing external types or packages:

- and the project is a recognized build-tool project
- and full dependency options were not already used
1. resolve full javac options
2. rerun once

This makes the system both:

- fast on the common path
- robust on missed lexical cases

##### Typical retry triggers

Examples of diagnostics worth treating as retry candidates:

- package ... does not exist
- cannot find symbol

These should be interpreted carefully so retry happens only when it is likely useful.

#### Preference Order

The architecture prefers the following escalation order:

1. Source roots

Use project sources directly whenever possible.

1. Output directories

Use already-built project outputs if present and useful.

1. Dependency classpath

Invoke build-tool dependency resolution only when needed.

This ordering minimizes cost while preserving correctness.
