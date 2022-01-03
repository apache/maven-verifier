package org.apache.maven.it;

import static org.hamcrest.MatcherAssert.assertThat;

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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ForkedLauncherTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    
    private ForkedLauncher launcher;
    
    private final String workingDir = Paths.get( "src/test/resources/wrapper-project" ).toAbsolutePath().toString();
    
    @Test
    public void mvnw() throws Exception
    {
        launcher = new ForkedLauncher( ".", Collections.<String, String>emptyMap(), false, true );
        File logFile = temporaryFolder.newFile( "build.log" );

        int exitCode = launcher.run( new String[0], new Properties(), workingDir, logFile );

        // most likely this contains the exception in case exitCode != 0
        expectFileLine( logFile, "Hello World" );

        assertThat( "exit code", exitCode, is ( 0 ) );
    }

    @Test
    public void mvnwDebug() throws Exception
    {
        launcher = new ForkedLauncher( ".", Collections.<String, String>emptyMap(), true, true );
        File logFile = temporaryFolder.newFile( "build.log" );

        int exitCode = launcher.run( new String[0], new Properties(), workingDir, logFile );

        // most likely this contains the exception in case exitCode != 0
        expectFileLine( logFile, "Hello World" );

        assertThat( "exit code", exitCode , is ( 0 ) );
    }

    static void expectFileLine( File file, String expectedline ) throws IOException
    {
        try ( FileReader fr = new FileReader( file );
              BufferedReader br = new BufferedReader( fr ) )
        {
            Collection<String> text = new ArrayList<>();
            String line;
            while ( ( line = br.readLine() ) != null )
            {
                if ( expectedline.equals( line ) )
                {
                    return;
                }
                text.add( line );
            }

            String message = "%s doesn't contain '%s', was:\n%s";
            fail( String.format( message, file.getName(), expectedline, text ) );
        }
    }

}
