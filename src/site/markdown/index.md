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

# About Apache Maven Verifier Component

<div class="alert alert-warning" role="alert">
  <h4>⚠️ Deprecation Notice</h4>
  <p><strong>This project is deprecated and will be replaced by <a href="https://github.com/apache/maven/tree/master/impl/maven-executor">maven-executor</a>.</strong></p>
  <ul>
    <li><strong>New projects:</strong> Please use maven-executor instead</li>
    <li><strong>Existing projects:</strong> Please plan migration to maven-executor</li>
    <li>See <a href="https://github.com/apache/maven-verifier/blob/master/MIGRATION.md">Migration Guide</a></li>
    <li>See <a href="https://github.com/apache/maven-verifier/issues/186">Issue #186</a> for discussion</li>
  </ul>
</div>

Provides a test harness for Maven integration tests. This is a library which can be used in Java-based integration tests. Look at the [Getting Started guide](./getting-started.html) for more information on how to use it.

More complex example usages can be found in the the [different integration tests](https://github.com/apache/maven-integration-testing/tree/master/core-it-suite/src/test/java/org/apache/maven/it) of [Maven Core Integration Tests](https://github.com/apache/maven-integration-testing). 
