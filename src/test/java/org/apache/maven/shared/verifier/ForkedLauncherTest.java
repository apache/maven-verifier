/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.shared.verifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

public class ForkedLauncherTest {
    @TempDir
    private Path temporaryDir;

    private ForkedLauncher launcher;

    private final String workingDir =
            Paths.get("src/test/resources/wrapper-project").toAbsolutePath().toString();

    @Test
    public void mvnw() throws Exception {
        launcher = new ForkedLauncher(".", Collections.emptyMap(), false, true);
        Path logFile = temporaryDir.resolve("build.log");

        int exitCode = launcher.run(new String[0], new Properties(), workingDir, logFile.toFile());

        // most likely this contains the exception in case exitCode != 0
        expectFileLine(logFile, "Hello World");

        assertThat("exit code", exitCode, is(0));
    }

    @Test
    public void mvnwDebug() throws Exception {
        launcher = new ForkedLauncher(".", Collections.emptyMap(), true, true);
        Path logFile = temporaryDir.resolve("build.log");

        int exitCode = launcher.run(new String[0], new Properties(), workingDir, logFile.toFile());

        // most likely this contains the exception in case exitCode != 0
        expectFileLine(logFile, "Hello World");

        assertThat("exit code", exitCode, is(0));
    }

    static void expectFileLine(Path file, String expectedline) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Collection<String> text = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (expectedline.equals(line)) {
                    return;
                }
                text.add(line);
            }

            String message = "%s doesn't contain '%s', was:%n%s";
            fail(String.format(message, file.getFileName(), expectedline, text));
        }
    }
}
