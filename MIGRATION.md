<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

# Migration Guide: From maven-verifier to maven-executor

## ⚠️ Deprecation Notice

**Apache Maven Verifier is deprecated. [maven-executor](https://github.com/apache/maven-executor) was released and has its own repository.**

New projects should use maven-executor. Existing projects should plan migration to maven-executor. This project will be retired soon.

See [Issue #186](https://github.com/apache/maven-verifier/issues/186) for more details.

## Why Migrate?

### Problems with Current Ecosystem

Maven currently has two overlapping components for running Maven programmatically:
- **maven-invoker**: Can only fork Maven processes
- **maven-verifier**: Can fork or embed Maven, but with helper methods unnecessarily coupled to execution

Both have issues:
- ❌ Different APIs for the same purpose
- ❌ Require updates when Maven CLI changes
- ❌ Heavy-handed solutions with duplicated concerns
- ❌ No unified approach for Maven 3 and Maven 4 support
- ❌ Time-consuming to maintain

The concrete failure that forced this migration: maven-verifier 2.0.0-M1's embedded mode (`Embedded3xLauncher`) locates the Maven CLI by reflection and invokes `MavenCli.doMain(String[], String, PrintStream, PrintStream)`. Maven 4 replaced `MavenCli` with `MavenCling`, so embedded mode fails on Maven 4 with a `NoSuchMethodException`. maven-executor's `EmbeddedMavenExecutor` detects the Maven version instead and supports both Maven 3.9+ and Maven 4.

### Benefits of maven-executor

- ✅ **Unified API**: Single, simple API without need for changes when CLI changes
- ✅ **Both execution modes**: Supports both "forked" and "embedded" executors
- ✅ **Dependency-less**: Minimal dependencies
- ✅ **Maven 3.9 & 4+ support**: Transparent support for both Maven versions
- ✅ **Better isolation**: Proper environment isolation
- ✅ **Already in use**: Powers Maven 4 Integration Tests

## Migration Path

### 1. Update Dependencies

**Remove:**
```xml
<dependency>
  <groupId>org.apache.maven.shared</groupId>
  <artifactId>maven-verifier</artifactId>
  <scope>test</scope>
</dependency>
```

**Add:**
```xml
<dependency>
  <groupId>org.apache.maven.executor</groupId>
  <artifactId>maven-executor</artifactId>
  <version>1.0.0</version> <!-- Released 2026-06-15; check https://github.com/apache/maven-executor/releases for newer releases -->
  <scope>test</scope>
</dependency>
```

> **Note**: The externalized standalone maven-executor groupId is `org.apache.maven.executor` — different from the former Maven 4 core artifact `org.apache.maven:maven-executor`. Always check https://github.com/apache/maven-executor for the latest released version and coordinates.

### 2. Code Migration Examples

#### Before (maven-verifier):

```java
import org.apache.maven.shared.verifier.Verifier;
import org.apache.maven.shared.verifier.VerificationException;

public class MyTest {
    @Test
    public void testBuild() throws Exception {
        String baseDir = "/path/to/project";
        Verifier verifier = new Verifier(baseDir);

        // Configure
        verifier.setAutoclean(false);
        verifier.setMavenDebug(true);
        verifier.addCliArgument("-DskipTests=true");

        // Execute
        verifier.addCliArgument("package");
        verifier.execute();

        // Verify
        verifier.verifyErrorFreeLog();
        verifier.verifyFilePresent("target/my-app-1.0.jar");
    }
}
```

#### After (maven-executor):

```java
import org.apache.maven.executor.ExecutorHelper;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MyTest {
    @Test
    public void testBuild() throws Exception {
        Path mavenHome = Paths.get(System.getProperty("maven.home"));
        Path baseDir = Paths.get("/path/to/project");

        // ExecutorHelper selects embedded or forked automatically (Mode.AUTO),
        // or use Mode.FORKED / Mode.EMBEDDED explicitly.
        // It implements AutoCloseable — use try-with-resources.
        try (ExecutorHelper executor = ExecutorHelper.forMavenInstallation(mavenHome, ExecutorHelper.Mode.AUTO)) {
            ExecutorRequest request = ExecutorRequest.mavenBuilder()
                    .cwd(baseDir)
                    .arguments("package", "-DskipTests=true", "-X")
                    .build();

            ExecutorResult result = executor.execute(request);

            // Verify
            assertTrue(result.success(), "Build should succeed");
            assertTrue(Files.exists(baseDir.resolve("target/my-app-1.0.jar")),
                    "JAR file should exist");
        }
    }
}
```

If you need to choose the executor type explicitly:

```java
import org.apache.maven.executor.forked.ForkedMavenExecutor;
import org.apache.maven.executor.embedded.EmbeddedMavenExecutor;

// Forked: spawns a separate Maven process
try (ForkedMavenExecutor executor = new ForkedMavenExecutor(mavenHome)) { ... }

// Embedded: runs Maven inside the same JVM
try (EmbeddedMavenExecutor executor = new EmbeddedMavenExecutor(mavenHome)) { ... }
```

### 3. Key API Mapping

| maven-verifier Concept | maven-executor Equivalent |
|------------------------|---------------------------|
| `new Verifier(baseDir)` | `ExecutorRequest.mavenBuilder().cwd(baseDir).build()` |
| `verifier.addCliArgument(arg)` | `.argument(arg)` or `.arguments(arg1, arg2, ...)` on the builder |
| `verifier.setMavenDebug(true)` | Add `-X` to arguments |
| `verifier.setAutoclean(false)` | Manage clean goal manually in the arguments list |
| `verifier.setForkJvm(true)` | `new ForkedMavenExecutor(mavenHome)` or `Mode.FORKED` |
| `verifier.setForkJvm(false)` | `new EmbeddedMavenExecutor(mavenHome)` or `Mode.EMBEDDED` |
| `verifier.execute()` | `executor.execute(request)` → returns `ExecutorResult`. Unlike `Verifier.execute()`, which throws `VerificationException` on a non-zero exit code, `executor.execute(request)` throws `ExecutorException` only for an execution failure (I/O error, timeout, interruption); a failed build returns normally with `result.success() == false`. Check `result.success()` explicitly, or throw yourself as shown in [Example Adapter Pattern](#example-adapter-pattern) below, to replicate the Verifier's throw-on-failure behavior |
| `verifier.verifyErrorFreeLog()` | No direct equivalent; `result.success()` only proves the exit code was zero, not that the log is free of `[ERROR]` lines. Use `.grabOutputAsString(true)`, then assert neither `result.stdOutString()` nor `result.stdErrString()` contains `[ERROR]` |
| `verifier.verifyFilePresent(path)` | `Files.exists(baseDir.resolve(path))` |
| `verifier.verifyTextInLog(text)` | Check whether `result.stdOutString()` or `result.stdErrString()` contains `text` (requires `.grabOutputAsString(true)`). The Verifier log merges stdout and stderr into one file; the executor keeps them separate, so check both |
| `verifier.setSystemProperty(key, value)` / `setSystemProperties(props)` | In forked mode, `.argument("-D" + key + "=" + value)` — these become Maven user properties, the same as the Verifier's forked launcher passing them as `-Dkey=value` CLI arguments. **Not** `.jvmSystemProperty()`: `ForkedMavenExecutor` appends `jvmSystemProperties` to `MAVEN_OPTS`, making them JVM system properties of the forked Maven process rather than user properties |
| `verifier.setLocalRepo(path)` | `.argument("-Dmaven.repo.local=" + path)` |
| `verifier.setLogFileName(name)` / `getLogFileName()` | The executor writes no log file by default. Use `.stdOut(Files.newOutputStream(baseDir.resolve("log.txt")))` and `.stdErr(...)` (the same or a separate stream), or `.grabOutputAsString(true)` and write `result.stdOutString()` / `result.stdErrString()` yourself |

`result.exitCode()` returns `Optional<Integer>` with the Maven process exit code, if you need the raw value instead of `result.success()`.

`Mode.AUTO` (`ExecutorHelperImpl.getExecutorByRequest`) selects `FORKED` whenever the request sets environment variables or JVM arguments, and `EMBEDDED` otherwise.

### 4. Environment Variables

**Before:**
```java
verifier.setEnvironmentVariable("JAVA_HOME", "/path/to/java");
verifier.setEnvironmentVariable("MAVEN_OPTS", "-Xmx1024m");
```

**After:**
```java
ExecutorRequest request = ExecutorRequest.mavenBuilder()
        .cwd(baseDir)
        .arguments("package")
        .environmentVariable("JAVA_HOME", "/path/to/java")
        .environmentVariable("MAVEN_OPTS", "-Xmx1024m")
        .build();
```

### 5. Verification Helpers

maven-verifier included many helper methods like `verifyFilePresent()`, `verifyTextInLog()`, etc.
These are not part of maven-executor's core responsibility.

**Capturing output and verifying log content:**

The Verifier writes a single merged `log.txt`; maven-executor keeps stdout and stderr separate, so an equivalent of `verifyErrorFreeLog()` must check both streams.

```java
ExecutorRequest request = ExecutorRequest.mavenBuilder()
        .cwd(baseDir)
        .arguments("package")
        .grabOutputAsString(true) // captures stdout and stderr as strings in ExecutorResult
        .build();

ExecutorResult result = executor.execute(request);

// execute() does not throw on a failed build, only on an execution failure
// (I/O error, timeout, interruption), so check success() explicitly
assertTrue(result.success(), "Build should succeed");

// Check log content
String stdOut = result.stdOutString().orElse("");
String stdErr = result.stdErrString().orElse("");
assertTrue(stdOut.contains("BUILD SUCCESS"), "Expected BUILD SUCCESS in output");
assertFalse(stdOut.contains("[ERROR]"), "Expected no errors in stdout");
assertFalse(stdErr.contains("[ERROR]"), "Expected no errors in stderr");
```

**File existence checks:**
```java
assertTrue(Files.exists(baseDir.resolve("target/my-app-1.0.jar")),
        "Expected JAR was not built");
assertFalse(Files.exists(baseDir.resolve("target/should-not-exist.txt")),
        "Unexpected file was created");
```

### 6. Settings and Local Repository

**Before:**
```java
verifier.setLocalRepo("/custom/repo");
verifier.setSettingsFile("/path/to/settings.xml");
```

**After:**
```java
ExecutorRequest request = ExecutorRequest.mavenBuilder()
        .cwd(baseDir)
        .arguments(
            "package",
            "-Dmaven.repo.local=/custom/repo",
            "-s", "/path/to/settings.xml"
        )
        .build();
```

**Artifact helper methods have no equivalent.** `verifier.deleteArtifacts(...)`, `verifier.verifyArtifactPresent(...)`, and `verifier.getArtifactPath(...)` are not part of maven-executor. Compute the path yourself from the local repository: `<localRepo>/<groupId with dots replaced by slashes>/<artifactId>/<version>/`. Do not pull in `ToolboxExecutorTool` for this: it runs a third-party plugin (`eu.maveniverse.maven.plugins:toolbox`) through the Maven under test to compute the same paths — see [maven-executor issue #44](https://github.com/apache/maven-executor/issues/44).

## Migration Checklist

- [ ] Review all usages of `Verifier` class in your codebase
- [ ] Update POM dependencies to use maven-executor
- [ ] Replace Verifier instantiation with ExecutorRequest builder pattern
- [ ] Convert `addCliArgument()` calls to arguments list
- [ ] Choose between `ForkedMavenExecutor`, `EmbeddedMavenExecutor`, or `ExecutorHelper` (with `Mode.AUTO/FORKED/EMBEDDED`)
- [ ] Replace verification methods with standard Java file checks or custom utilities
- [ ] Update environment variable configuration
- [ ] Update local repository and settings configuration
- [ ] Test thoroughly with your integration test suite
- [ ] Update documentation and comments

## Gradual Migration Strategy

For large codebases, consider a gradual approach:

1. **Phase 1**: Add maven-executor dependency alongside maven-verifier
2. **Phase 2**: Create adapter/wrapper classes to ease transition
3. **Phase 3**: Migrate tests module by module
4. **Phase 4**: Remove maven-verifier dependency once all tests are migrated

## Example Adapter Pattern

For gradual migration, you can create an adapter that wraps maven-executor with a Verifier-like interface:

```java
import org.apache.maven.executor.ExecutorException;
import org.apache.maven.executor.ExecutorHelper;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MavenExecutorAdapter implements AutoCloseable {
    private final Path baseDir;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> env = new HashMap<>();
    private final ExecutorHelper executor;

    public MavenExecutorAdapter(Path mavenHome, String baseDir) {
        this.baseDir = Paths.get(baseDir);
        // Mode.AUTO selects embedded when possible, falls back to forked
        this.executor = ExecutorHelper.forMavenInstallation(mavenHome, ExecutorHelper.Mode.AUTO);
    }

    public MavenExecutorAdapter(Path mavenHome, String baseDir, ExecutorHelper.Mode mode) {
        this.baseDir = Paths.get(baseDir);
        this.executor = ExecutorHelper.forMavenInstallation(mavenHome, mode);
    }

    public void addCliArgument(String arg) {
        arguments.add(arg);
    }

    public void setEnvironmentVariable(String key, String value) {
        env.put(key, value);
    }

    /** Executes Maven; throws ExecutorException if the build fails. */
    public ExecutorResult execute() throws ExecutorException {
        ExecutorRequest request = ExecutorRequest.mavenBuilder()
                .cwd(baseDir)
                .arguments(arguments)
                .environmentVariables(env)
                .build();

        ExecutorResult result = executor.execute(request);
        if (!result.success()) {
            throw new ExecutorException(
                    "Maven execution failed with exit code: " + result.exitCode().orElse(-1));
        }
        return result;
    }

    @Override
    public void close() throws ExecutorException {
        executor.close();
    }
}
```

## Resources

- [maven-executor project](https://github.com/apache/maven-executor)
- [maven-executor releases](https://github.com/apache/maven-executor/releases)
- [Issue #186 discussion](https://github.com/apache/maven-verifier/issues/186)

## Support & Questions

For migration questions or issues:
- Post to [Maven Dev Mailing List](https://maven.apache.org/mailing-lists.html)
- Open issues on [maven-executor GitHub](https://github.com/apache/maven-executor/issues)
