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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.maven.shared.utils.io.FileUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VerifierLocalRepositoryTest
{
    private static File testDirectory;
    private static File settingsFile;

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();
    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    @BeforeClass
    public static void createTestDirectory() throws IOException
    {
        Class<?> thisClass = VerifierLocalRepositoryTest.class;
        String testDirectoryParent = thisClass.getProtectionDomain().getCodeSource().getLocation().getPath();
        testDirectory = new File( testDirectoryParent, thisClass.getSimpleName() + ".tmp" );
        assertTrue( testDirectory.mkdir() );
        settingsFile = new File( testDirectory, "settings.xml" );
        assertTrue( settingsFile.createNewFile() );
    }

    @Before
    public void clearDefaultProperties() {
        System.clearProperty( "maven.repo.local" );
        System.clearProperty( "user.home" );
    }

    private static void fillSettingsFile( String content ) throws FileNotFoundException
    {
        try ( PrintStream stream = new PrintStream( new FileOutputStream( settingsFile ) ) )
        {
            stream.println( content );
        }
    }

    @AfterClass
    public static void deleteTestDirectory() throws IOException
    {
        FileUtils.deleteDirectory( testDirectory );
        testDirectory = null;
        settingsFile = null;
    }

    @Test( expected = VerificationException.class )
    public void testNotSetUserHomeAbsent() throws VerificationException, IOException
    {
        fillSettingsFile( "<settings></settings>" );
        new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
    }

    @Test
    public void testNotSet() throws VerificationException, IOException
    {
        File userHome = new File( testDirectory, "defaultUserHome" );
        System.setProperty( "user.home", userHome.getPath() );
        fillSettingsFile( "<settings></settings>" );
        Verifier verifier = new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
        assertEquals( new File( userHome, ".m2/repository" ).getPath(), verifier.getLocalRepository() );
    }

    @Test
    public void testSetByProperty() throws VerificationException, IOException
    {
        String repositoryPath = new File( testDirectory, "propertyRepository" ).getPath();
        System.setProperty( "maven.repo.local", repositoryPath );
        fillSettingsFile( "<settings></settings>" );
        Verifier verifier = new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
        assertEquals( repositoryPath, verifier.getLocalRepository() );
    }

    @Test
    public void testNoPlaceholders() throws VerificationException, IOException
    {
        String repositoryPath = new File( testDirectory, "noPlaceholdersRepository" ).getPath();
        fillSettingsFile( "<settings>\n" +
            "  <localRepository>" + repositoryPath + "</localRepository>\n" +
            "</settings>" );
        Verifier verifier = new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
        assertEquals( repositoryPath, verifier.getLocalRepository() );
    }

    @Test
    public void testEnvPlaceholder() throws VerificationException, IOException
    {
        String repositoryPath = new File( testDirectory, "envPlaceholderRepository" ).getPath();
        environmentVariables.set( "TEST_ENV_VAR", repositoryPath );
        fillSettingsFile( "<settings>\n" +
            "  <localRepository>${env.TEST_ENV_VAR}</localRepository>\n" +
            "</settings>" );
        Verifier verifier = new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
        assertEquals( repositoryPath, verifier.getLocalRepository() );
    }

    @Test( expected = VerificationException.class )
    public void testAbsentEnvPlaceholder() throws VerificationException, IOException
    {
        environmentVariables.clear( "TEST_ENV_VAR" );
        fillSettingsFile( "<settings>\n" +
            "  <localRepository>${env.TEST_ENV_VAR}</localRepository>\n" +
            "</settings>" );
        new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
    }

    @Test( expected = VerificationException.class )
    public void testProjectPlaceholder() throws VerificationException, IOException
    {
        fillSettingsFile( "<settings>\n" +
            "  <localRepository>${project.properties.repo}</localRepository>\n" +
            "</settings>" );
        new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
    }

    @Test
    @Ignore( "TODO: implement settings placeholder handling" )
    public void testSettingsPlaceholder() throws VerificationException, IOException
    {
        String repositoryPath = new File( testDirectory, "settingsPlaceholderRepository" ).getPath();
        fillSettingsFile( "<settings>\n" +
            "  <localRepository>${settings.properties.repo}</localRepository>\n" +
            "  <properties>\n" +
            "    <repo>" + repositoryPath + "</repo>\n" +
            "  </properties>\n" +
            "</settings>" );
        Verifier verifier = new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
        assertEquals( repositoryPath, verifier.getLocalRepository() );
    }

    @Test
    public void testSystemPropertyPlaceholder() throws VerificationException, IOException
    {
        String repositoryPath = new File( testDirectory, "systemPropertyPlaceholderRepository" ).getPath();
        System.setProperty( "test_sys_prop", repositoryPath );
        fillSettingsFile( "<settings>\n" +
            "  <localRepository>${test_sys_prop}</localRepository>\n" +
            "</settings>" );
        Verifier verifier = new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
        assertEquals( repositoryPath, verifier.getLocalRepository() );
    }

    @Test( expected = VerificationException.class )
    public void testAbsentSystemPropertyPlaceholder() throws VerificationException, IOException
    {
        System.clearProperty( "test_sys_prop" );
        fillSettingsFile( "<settings>\n" +
            "  <localRepository>${test_sys_prop}</localRepository>\n" +
            "</settings>" );
        new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
    }

    @Test
    public void testCommonValue() throws VerificationException, IOException
    {
        File userHome = new File( testDirectory, "userHome" );
        System.setProperty( "user.home", userHome.getPath() );
        fillSettingsFile( "<settings>\n" +
            "  <localRepository>${user.home}/.m2/repository</localRepository>\n" +
            "</settings>" );
        Verifier verifier = new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
        assertEquals( new File( userHome, ".m2/repository" ).getPath(), verifier.getLocalRepository() );
    }

    @Test
    public void testTwoPlaceholders() throws VerificationException, IOException
    {
        File repository = new File( testDirectory, "twoPlaceholdersRepository" );
        System.setProperty( "test_sys_prop_1", repository.getParent() );
        System.setProperty( "test_sys_prop_2", repository.getName() );
        fillSettingsFile( "<settings>\n" +
            "  <localRepository>${test_sys_prop_1}/${test_sys_prop_2}</localRepository>\n" +
            "</settings>" );
        Verifier verifier = new Verifier( testDirectory.getPath(), settingsFile.getAbsolutePath() );
        assertEquals( repository.getPath(), verifier.getLocalRepository() );
    }
}
