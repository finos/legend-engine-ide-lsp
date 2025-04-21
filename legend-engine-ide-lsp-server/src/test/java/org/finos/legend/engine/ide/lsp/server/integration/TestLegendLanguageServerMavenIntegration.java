/*
 * Copyright 2024 Goldman Sachs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.legend.engine.ide.lsp.server.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.InitializedParams;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 5, unit = TimeUnit.MINUTES)
// all tests should finish but in case of some uncaught deadlock, timeout whole test
public class TestLegendLanguageServerMavenIntegration
{
    @RegisterExtension
    static LegendLanguageServerIntegrationExtension extension = new LegendLanguageServerIntegrationExtension();

    @Test
    void testMavenClasspathCache() throws Exception
    {
        Path workspaceStoragePath = Files.createTempDirectory("legend-integration-storage");

        try
        {
            System.setProperty("storagePath", workspaceStoragePath.toString());

            Files.writeString(extension.resolveWorkspacePath("pom.xml"),
                    "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                    "    <modelVersion>4.0.0</modelVersion>\n" +
                    "\n" +
                    "    <groupId>org.finos.legend.engine.ide.lsp</groupId>\n" +
                    "    <artifactId>legend-engine-ide-lsp-server-sample-pom</artifactId>\n" +
                    "    <version>0.0.0-SNAPSHOT</version>\n" +
                    "\n" +
                    "    <properties>\n" +
                    "       <platform.eclipse-collections.version>10.2.0</platform.eclipse-collections.version>\n" +
                    "    </properties>\n" +
                    "    <dependencies>\n" +
                    "        <dependency>\n" +
                    "            <groupId>commons-io</groupId>\n" +
                    "            <artifactId>commons-io</artifactId>\n" +
                    "            <version>2.15.1</version>\n" +
                    "        </dependency>\n" +
                    "        <dependency>\n" +
                    "            <groupId>org.eclipse.collections</groupId>\n" +
                    "            <artifactId>eclipse-collections-api</artifactId>\n" +
                    "            <version>${platform.eclipse-collections.version}</version>\n" +
                    "        </dependency>\n" +
                    "    </dependencies>\n" +
                    "\n" +
                    "</project>");

            extension.clearClientLogMessages();
            extension.getServer().initialized(new InitializedParams());
            extension.waitForAllTaskToComplete();

            Assertions.assertTrue(extension.clientLogged("logMessage - Info - Resolving core classpath invoking maven..."), "Core classpath first loaded from maven");

            extension.clearClientLogMessages();
            extension.getServer().initialized(new InitializedParams());
            extension.waitForAllTaskToComplete();

            Assertions.assertTrue(extension.clientLogged("logMessage - Info - Reusing cached core classpath rather that invoking maven"), "Core classpath should be reused");

            // check file change detection
            Files.writeString(extension.resolveWorkspacePath("pom.xml"),
                    "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                            "    <modelVersion>4.0.0</modelVersion>\n" +
                            "\n" +
                            "    <groupId>org.finos.legend.engine.ide.lsp</groupId>\n" +
                            "    <artifactId>legend-engine-ide-lsp-server-sample-pom</artifactId>\n" +
                            "    <version>0.0.0-SNAPSHOT</version>\n" +
                            "\n" +
                            "    <dependencies>\n" +
                            "        <dependency>\n" +
                            "            <groupId>commons-io</groupId>\n" +
                            "            <artifactId>commons-io</artifactId>\n" +
                            "            <version>2.15.1</version>\n" +
                            "        </dependency>\n" +
                            "    </dependencies>\n" +
                            "\n" +
                            "</project>");

            extension.clearClientLogMessages();
            extension.getServer().initialized(new InitializedParams());
            extension.waitForAllTaskToComplete();

            Assertions.assertTrue(extension.clientLogged("logMessage - Info - Cached for core classpath is stale..."), "Core classpath should be not be reused");
        }
        catch (Exception e)
        {
            System.clearProperty("storagePath");
            FileUtils.deleteDirectory(workspaceStoragePath.toFile());
            throw e;
        }
    }
}
