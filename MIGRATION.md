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

**Apache Maven Verifier is deprecated and will be replaced by [maven-executor](https://github.com/apache/maven/tree/master/impl/maven-executor).**

New projects should use maven-executor. Existing projects should plan migration to maven-executor.

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
  <groupId>org.apache.maven</groupId>
  <artifactId>maven-executor</artifactId>
  <version>4.0.0-rc-5</version> <!-- Use latest version -->
  <scope>test</scope>
</dependency>
```

> **Note**: maven-executor location may change as it might be moved out of Maven 4 core to become a standalone project. Check the latest documentation.

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
        verifier.resetStreams();
    }
}
```

#### After (maven-executor):

```java
import org.apache.maven.api.cli.Executor;
import org.apache.maven.api.cli.ExecutorRequest;
import org.apache.maven.cling.executor.forked.ForkedExecutor;
import org.apache.maven.cling.executor.embedded.EmbeddedExecutor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class MyTest {
    @Test
    public void testBuild() throws Exception {
        Path baseDir = Paths.get("/path/to/project");
        
        // Choose executor type: Forked or Embedded
        Executor executor = new ForkedExecutor();
        // OR: Executor executor = new EmbeddedExecutor();
        
        // Build request with CLI arguments
        ExecutorRequest request = ExecutorRequest.builder()
            .cwd(baseDir)
            .arguments(Arrays.asList("package", "-DskipTests=true", "-X"))
            .build();
        
        // Execute
        int exitCode = executor.execute(request);
        
        // Verify
        assertEquals(0, exitCode, "Build should succeed");
        assertTrue(Files.exists(baseDir.resolve("target/my-app-1.0.jar")),
            "JAR file should exist");
    }
}
```

### 3. Key API Mapping

| maven-verifier Concept | maven-executor Equivalent |
|------------------------|---------------------------|
| `new Verifier(baseDir)` | `ExecutorRequest.builder().cwd(baseDir).build()` |
| `verifier.addCliArgument(arg)` | Add to `arguments` list in ExecutorRequest |
| `verifier.setMavenDebug(true)` | Add `-X` to arguments |
| `verifier.setAutoclean(false)` | Manage manually or via arguments |
| `verifier.setForkJvm(true)` | Use `ForkedExecutor` |
| `verifier.setForkJvm(false)` | Use `EmbeddedExecutor` |
| `verifier.execute()` | `executor.execute(request)` |
| `verifier.verifyErrorFreeLog()` | Check `exitCode == 0` |
| `verifier.verifyFilePresent(path)` | Use `Files.exists(Paths.get(...))` |
| `verifier.verifyTextInLog(text)` | Capture and parse executor output |

### 4. Environment Variables

**Before:**
```java
verifier.setEnvironmentVariable("JAVA_HOME", "/path/to/java");
verifier.setEnvironmentVariable("MAVEN_OPTS", "-Xmx1024m");
```

**After:**
```java
Map<String, String> env = new HashMap<>();
env.put("JAVA_HOME", "/path/to/java");
env.put("MAVEN_OPTS", "-Xmx1024m");

ExecutorRequest request = ExecutorRequest.builder()
    .cwd(baseDir)
    .arguments(args)
    .environmentVariables(env)
    .build();
```

### 5. Verification Helpers

maven-verifier included many helper methods like `verifyFilePresent()`, `verifyTextInLog()`, etc. These are not part of maven-executor's core responsibility. Instead:

**Extract verification to separate utilities:**
```java
public class MavenTestUtils {
    public static void assertFileExists(Path base, String relativePath) {
        Path file = base.resolve(relativePath);
        assertTrue(Files.exists(file), 
            "Expected file does not exist: " + file);
    }
    
    public static void assertLogContains(String log, String expectedText) {
        assertTrue(log.contains(expectedText),
            "Log does not contain expected text: " + expectedText);
    }
    
    public static void assertErrorFreeLog(String log) {
        assertFalse(log.contains("[ERROR]"),
            "Log contains errors");
    }
}
```

### 6. Settings and Local Repository

**Before:**
```java
verifier.setLocalRepo("/custom/repo");
verifier.setUserSettingsFile("/path/to/settings.xml");
```

**After:**
```java
ExecutorRequest request = ExecutorRequest.builder()
    .cwd(baseDir)
    .arguments(Arrays.asList(
        "package",
        "-Dmaven.repo.local=/custom/repo",
        "-s", "/path/to/settings.xml"
    ))
    .build();
```

## Migration Checklist

- [ ] Review all usages of `Verifier` class in your codebase
- [ ] Update POM dependencies to use maven-executor
- [ ] Replace Verifier instantiation with ExecutorRequest builder pattern
- [ ] Convert `addCliArgument()` calls to arguments list
- [ ] Choose between ForkedExecutor and EmbeddedExecutor
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

For gradual migration, you can create an adapter:

```java
public class MavenExecutorAdapter {
    private final Path baseDir;
    private final List<String> arguments = new ArrayList<>();
    private final Map<String, String> env = new HashMap<>();
    private Executor executor = new ForkedExecutor();
    
    public MavenExecutorAdapter(String baseDir) {
        this.baseDir = Paths.get(baseDir);
    }
    
    public void addCliArgument(String arg) {
        arguments.add(arg);
    }
    
    public void setEnvironmentVariable(String key, String value) {
        env.put(key, value);
    }
    
    public void setForkJvm(boolean fork) {
        executor = fork ? new ForkedExecutor() : new EmbeddedExecutor();
    }
    
    public void execute() throws Exception {
        ExecutorRequest request = ExecutorRequest.builder()
            .cwd(baseDir)
            .arguments(arguments)
            .environmentVariables(env)
            .build();
        
        int exitCode = executor.execute(request);
        if (exitCode != 0) {
            throw new Exception("Maven execution failed with exit code: " + exitCode);
        }
    }
    
    // Add other adapter methods as needed
}
```

## Resources

- [maven-executor source](https://github.com/apache/maven/tree/master/impl/maven-executor)
- [Maven 4 IT Verifier implementation](https://github.com/apache/maven/blob/master/its/core-it-support/maven-it-helper/src/main/java/org/apache/maven/it/Verifier.java)
- [Issue #186 discussion](https://github.com/apache/maven-verifier/issues/186)

## Support & Questions

For migration questions or issues:
- Post to [Maven Dev Mailing List](https://maven.apache.org/mailing-lists.html)
- Open issues on [maven-executor GitHub](https://github.com/apache/maven/issues)
