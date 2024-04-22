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

@Timeout(value = 3, unit = TimeUnit.MINUTES)
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

            // load, will create cache files
            extension.getServer().initialized(new InitializedParams());
            extension.waitForAllTaskToComplete();

            extension.getServer().initialized(new InitializedParams());
            extension.waitForAllTaskToComplete();

            Assertions.assertTrue(extension.clientLogged("logMessage - Info - Reusing cached core classpath rather that invoking maven"), "Core classpath should be reused");
        }
        catch (Exception e)
        {
            System.clearProperty("storagePath");
            FileUtils.deleteDirectory(workspaceStoragePath.toFile());
            throw e;
        }
    }
}
