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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class VerifierTest {
    @TempDir
    public Path temporaryDir;

    private void check(String expected, String... lines) {
        assertEquals(expected, ForkedLauncher.extractMavenVersion(Arrays.asList(lines)));
    }

    @Test
    public void testSunBug9009028ForJdk() {
        Properties oldProperties = System.getProperties();
        try {
            final String version = System.getProperty("java.version");
            System.setProperties(null);
            assertEquals(version, System.getProperty("java.version"));
        } finally {
            System.setProperties(oldProperties);
        }
    }

    @Test
    public void testExtractMavenVersion() {
        check("2.0.6", "Maven version: 2.0.6");

        check(
                "2.0.10",
                "Maven version: 2.0.10",
                "Java version: 1.5.0_22",
                "OS name: \"windows 7\" version: \"6.1\" arch: \"x86\" Family: \"windows\"");

        check(
                "3.0",
                "Apache Maven 3.0 (r1004208; 2010-10-04 13:50:56+0200)",
                "Java version: 1.5.0_22",
                "OS name: \"windows 7\" version: \"6.1\" arch: \"x86\" Family: \"windows\"");

        check(
                "3.0.5",
                "Apache Maven 3.0.5 (r01de14724cdef164cd33c7c8c2fe155faf9602da; 2013-02-19 14:51:28+0100)",
                "Java version: 1.7.0_25",
                "OS name: \"linux\" version: \"3.11.0-13-generic\" arch: \"amd64\" Family: \"unix\"");

        check(
                "3.2-SNAPSHOT",
                "Apache Maven with Log4j 2 3.2-SNAPSHOT (bc5e99f9f0aaf7d5b2431ff5d602ca3bb38559d5; 2013-11-25 21:17:33+0100)",
                "Java version: 1.7.0_25",
                "OS name: \"linux\" version: \"3.11.0-13-generic\" arch: \"amd64\" Family: \"unix\"");
    }

    @Test
    public void testFileInJarPresent() throws VerificationException {
        // File file = new File( "src/test/resources/mshared104.jar!fud.xml" );
        Verifier verifier = new Verifier("src/test/resources");
        verifier.verifyFilePresent("mshared104.jar!/pom.xml");
        verifier.verifyFileNotPresent("mshared104.jar!/fud.xml");
        verifier.resetStreams();
    }

    @Test
    public void testStripAnsi() {
        assertEquals(
                "--- plugin:version:goal (id) @ artifactId ---",
                Verifier.stripAnsi("\u001B[1m--- \u001B[0;32mplugin:version:goal\u001B[0;1m (id)\u001B[m @ "
                        + "\u001B[36martifactId\u001B[0;1m ---\u001B[m"));
    }

    @Test
    public void testLoadPropertiesFNFE() {
        VerificationException exception = assertThrows(VerificationException.class, () -> {
            Verifier verifier = new Verifier("src/test/resources");
            try {
                verifier.loadProperties("unknown.properties");
            } finally {
                verifier.resetStreams();
            }
        });
        assertInstanceOf(FileNotFoundException.class, exception.getCause());
    }

    @Test
    public void testDedicatedMavenHome() throws VerificationException, IOException {
        String mavenHome =
                Paths.get("src/test/resources/maven-home").toAbsolutePath().toString();
        Verifier verifier = new Verifier(temporaryDir.toString(), null, false, mavenHome);
        verifier.executeGoal("some-goal");
        verifier.resetStreams();
        Path logFile = Paths.get(verifier.getBasedir(), verifier.getLogFileName());
        ForkedLauncherTest.expectFileLine(logFile, "Hello World from Maven Home");
    }

    @Test
    void testDefaultFilterMap() throws VerificationException {
        Verifier verifier = new Verifier("src/test/resources");
        Map<String, String> filterMap = verifier.newDefaultFilterMap();
        verifier.resetStreams();

        assertEquals(2, filterMap.size());
        assertTrue(filterMap.containsKey("@basedir@"));
        assertTrue(filterMap.containsKey("@baseurl@"));
    }

    @Test
    void testDefaultMavenArgument() throws VerificationException {
        TestVerifier verifier = new TestVerifier("src/test/resources");

        verifier.executeGoal("test");
        verifier.resetStreams();

        assertThat(
                verifier.launcher.cliArgs,
                arrayContaining(
                        "-e",
                        "--batch-mode",
                        "-Dmaven.repo.local=test-local-repo",
                        "org.apache.maven.plugins:maven-clean-plugin:clean",
                        "test"));
    }

    @Test
    void testDefaultMavenArgumentWithExecuteMethod() throws VerificationException {
        TestVerifier verifier = new TestVerifier("src/test/resources");

        verifier.addCliArgument("test");
        verifier.execute();
        verifier.resetStreams();

        assertThat(
                verifier.launcher.cliArgs,
                arrayContaining(
                        "-e",
                        "--batch-mode",
                        "-Dmaven.repo.local=test-local-repo",
                        "org.apache.maven.plugins:maven-clean-plugin:clean",
                        "test"));
    }

    public static Stream<Arguments> argumentsForTest() {
        return Stream.of(
                arguments("test-argument", "test-argument"),
                arguments("test1/${basedir}/test2/${basedir}", "test1/src/test/resources/test2/src/test/resources"),
                arguments("argument with space", "argument with space"));
    }

    @ParameterizedTest
    @MethodSource("argumentsForTest")
    void argumentShouldBePassedAsIs(String inputArgument, String expectedArgument) throws VerificationException {
        TestVerifier verifier = new TestVerifier("src/test/resources");

        verifier.addCliArgument(inputArgument);
        verifier.executeGoal("test");
        verifier.resetStreams();

        assertThat(verifier.launcher.cliArgs, hasItemInArray(expectedArgument));
    }

    @Test
    void addCliArgsShouldAddSeparateArguments() throws VerificationException {
        TestVerifier verifier = new TestVerifier("src/test/resources");

        verifier.addCliArguments("cliArg1", "cliArg2");
        verifier.executeGoal("test");
        verifier.resetStreams();

        assertThat(verifier.launcher.cliArgs, allOf(hasItemInArray("cliArg1"), hasItemInArray("cliArg2")));
    }

    private static class TestMavenLauncher implements MavenLauncher {
        String[] cliArgs;

        @Override
        public int run(String[] cliArgs, Properties systemProperties, String workingDirectory, File logFile)
                throws IOException, LauncherException {
            this.cliArgs = cliArgs;
            return 0;
        }

        @Override
        public String getMavenVersion() throws IOException, LauncherException {
            return null;
        }
    }

    private static class TestVerifier extends Verifier {
        TestMavenLauncher launcher;

        public TestVerifier(String basedir) throws VerificationException {
            super(basedir);
            setLocalRepo("test-local-repo");
            launcher = new TestMavenLauncher();
        }

        @Override
        protected MavenLauncher getMavenLauncher(Map<String, String> envVars) throws LauncherException {
            return launcher;
        }
    }
}
