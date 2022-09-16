<!--
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
-->

# Getting Started

## Overview

Using the `Verifier` consists out of three different phases

1. Configure
2. Run
3. Verify

## Configure

The `Verifier` instance is constructed in the simplest case with just one argument taking the path name of the base directory containing the `pom.xml` of the project you want to build.

```
String baseDir = ...
Verifier verifier = new Verifier( baseDir );
```

The configuration can be further tweaked with additional setter methods and/or constructors taking more parameters.

### System Properties

The following system properties are evaluated. None of them are required and in most cases the same behavior can also be achieved through constructor or setter method programmatically.

| System Property | Description | Default Value |
| --- | --- |
| `verifier.forkMode` | The following values are supported: <br/>`auto` uses the forked launcher when environment variables are set<br/>`embedder` always uses the embedded launcher<br/>any other value leads to always using the forked launcher | `auto` |
| `maven.home` | The directory containing the Maven executable in `bin/mvn` | not set |
| `user.home` | Set by JRE, used for determining Maven default local repository path or the fallback Maven executable | always set by JRE |
| `maven.bootclasspath` | Only relevant if Maven home could be determined and the embedded launcher is being used. Determines the classpath of the launcher. May contain multiple paths separated by the system specific path separator. | not set (using all JARs below `<Maven Home>/boot` as class path) |
| `classworlds.conf` | Only relevant if Maven home could be determined and the embedded launcher is being used. The configuration file used by [Plexus Classworlds Loader][plexus-classwords]. | `<Maven Home>/bin/m2.conf`
| `maven.repo.local` | Contains the path of the local Maven repository | Either repository path set in `settings.xml` or `<User Home>/.m2/repository` |
| `maven.repo.local.layout` | Layout of the local Maven repository. Either `legacy` or `default` | `default` |

### Finding Maven Executable

The following mechanism determines the binary which is used for executing Maven. This is 

- either the embedded launcher which uses
    -  the `org.apache.maven.cli.MavenCli` class loaded from the context class loader (in case Maven Home could not be determined) or 
    -  the [Plexus Classworlds Loader][plexus-classwords] (in case Maven Home could be determined)
-  or the forked launcher

Whether the embedded or the forked launcher are used depends on the field `forkJvm` set through the constructor or `setForkJvm` or as fallback on the value of system property `verifier.forkMode`.

### Determining Maven Home Directory

The following directories are considered as potential Maven home directory (relevant for both forked launcher and embedded launcher with  [Plexus Classworlds Loader][plexus-classwords]). The first existing directory from the list is used.

1. Maven Home path given in the constructor
2. System property `maven.home`

### Setting Maven Home for Embedded Launcher

In order to pass `Maven Home` used for executing project itself to tests execution, `maven-surefire` can be configured like:

```
  <plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
      <systemPropertyVariables>
        <maven.home>${maven.home}</maven.home>
      </systemPropertyVariables>
    </configuration>
  </plugin>
```

### Class Path For Embedded Launcher

In case when `Maven Home Directory` can not be determined, to use the embedded launcher it is important that some artifacts are in the class path. 
For the Context Class Loader case this would mean the following dependencies are needed at least (for Maven 3.8.4):

```
<!-- embedder for testing Embedded3xLauncher with classpath -->
<dependency>
  <groupId>org.apache.maven</groupId>
  <artifactId>maven-embedder</artifactId>
  <version>3.8.4</version>
  <scope>test</scope>
</dependency>
<!-- START transitive dependencies of embedder -->
<dependency>
  <groupId>org.slf4j</groupId>
  <artifactId>slf4j-simple</artifactId>
  <version>1.7.32</version>
  <scope>test</scope>
</dependency>
<!-- required due to https://issues.apache.org/jira/browse/MNG-6561 -->
<dependency>
  <groupId>org.apache.maven</groupId>
  <artifactId>maven-compat</artifactId>
  <version>3.8.4</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.apache.maven.resolver</groupId>
  <artifactId>maven-resolver-connector-basic</artifactId>
  <version>1.6.3</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.apache.maven.resolver</groupId>
  <artifactId>maven-resolver-transport-http</artifactId>
  <version>1.6.3</version>
  <scope>test</scope>
</dependency>
```

## Run

Calling `executeGoals` runs Maven with the given goals or phases and optionally some additional environment variables. It throws a `VerificationException` in case the execution is not successful (e.g. binary not found or exit code > 0). It is either using a forked JVM or is executed in the same JVM depending on the configuration.

```
verifier.executeGoals( "package" );
```

## Verify

After executing the Maven goals there are several methods starting with prefix `verify` which allow you to check for the build result, check the log for certain contents and the existence of generated artifacts.

The main method `verify(boolean)` takes into consideration a file named `expected-results.txt` being located in the base directory. Each line consists of a file path (optionally prefixed by `!`) and it is automatically verified that the file exists or is missing (in case the path starts with `!`).

```
verifier.verify( true ); // if true, throws an exception in case of errors in the build log
```

[plexus-classwords]: https://codehaus-plexus.github.io/plexus-classworlds/launcher.html
