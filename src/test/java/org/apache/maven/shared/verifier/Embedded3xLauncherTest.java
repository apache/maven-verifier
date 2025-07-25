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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class Embedded3xLauncherTest {
    @TempDir
    private Path temporaryDir;

    private final String workingDir =
            Paths.get("src/test/resources").toAbsolutePath().toString();

    @Test
    public void testWithClasspath() throws Exception {
        MavenLauncher launcher = Embedded3xLauncher.createFromClasspath();
        runLauncher(launcher);
    }

    @Test
    public void testWithMavenHome() throws Exception {
        MavenLauncher launcher = Embedded3xLauncher.createFromMavenHome(System.getProperty("maven.home"), null, null);
        runLauncher(launcher);
    }

    private void runLauncher(MavenLauncher launcher) throws Exception {
        Path logFile = temporaryDir.resolve("build.log");

        int exitCode = launcher.run(new String[] {"clean"}, new Properties(), workingDir, logFile.toFile());

        assertThat(
                "exit code unexpected, build log: " + System.lineSeparator() + new String(Files.readAllBytes(logFile)),
                exitCode,
                is(0));
    }
}
