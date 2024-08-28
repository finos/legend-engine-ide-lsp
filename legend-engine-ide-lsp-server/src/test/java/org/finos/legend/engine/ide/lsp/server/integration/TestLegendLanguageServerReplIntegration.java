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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
// all tests should finish but in case of some uncaught deadlock, timeout whole test
public class TestLegendLanguageServerReplIntegration
{
    @RegisterExtension
    static LegendLanguageServerIntegrationExtension extension = new LegendLanguageServerIntegrationExtension();

    @Test
    void testReplStartWithGivenClasspath(@TempDir Path dir) throws Exception
    {
        String classpath = extension.futureGet(extension.getServer().getLegendLanguageService().replClasspath());

        ProcessBuilder processBuilder = new ProcessBuilder(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "org.finos.legend.engine.ide.lsp.server.LegendREPLTerminal",
                "",
                dir.toString()
        );
        processBuilder.environment().put("CLASSPATH", classpath);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = null;
        try
        {
            process = processBuilder.start();
            Assertions.assertTrue(process.isAlive());
            read(process.getInputStream(), "Ready!");
        }
        finally
        {
            if (process != null)
            {
                process.destroy();
                process.onExit().join();
            }
        }
    }

    private static void read(InputStream replOutputConsole, String untilToken) throws IOException
    {
        StringBuilder output = new StringBuilder();
        while (!output.toString().contains(untilToken))
        {
            int read = replOutputConsole.read();
            if (read != -1)
            {
                System.err.print((char) read);
                output.append((char) read);
            }
            else
            {
                Assertions.fail("Did not found token and stream closed...");
            }

        }
    }
}
