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

package org.finos.legend.engine.ide.lsp.server;

import org.apache.commons.io.FileUtils;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPFeature;
import org.finos.legend.engine.ide.lsp.extension.features.LegendREPLFeature;
import org.finos.legend.engine.ide.lsp.extension.features.LegendUsageEventConsumer;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

public class LegendREPLTerminal
{
    public static void main(String... args) throws InterruptedException
    {
        List<LegendLSPFeature> features = new ArrayList<>();
        Instant startTime = Instant.now().minusMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("replId", ManagementFactory.getRuntimeMXBean().getName());

        try
        {
            if (args.length < 1)
            {
                throw new RuntimeException("At least one workspace folder is required.");
            }

            String planExecutorConfigurationProperty = System.getProperty("legend.planExecutor.configuration", "");
            String homeDir = System.getProperty("legend.repl.configuration.homeDir");
            Path planExecutorConfigurationJsonPath = planExecutorConfigurationProperty.isEmpty() ? null : Path.of(planExecutorConfigurationProperty);
            List<String> workspaceFolders = Arrays.asList(args);

            ServiceLoader.load(LegendLSPFeature.class).forEach(features::add);

            Optional<LegendREPLFeature> repl = features
                    .stream()
                    .filter(LegendREPLFeature.class::isInstance)
                    .map(LegendREPLFeature.class::cast)
                    .findAny();


            if (repl.isPresent())
            {
                fireEvent(features, LegendUsageEventConsumer.event("startReplTerminal", startTime, Instant.now(), metadata));
                repl.get().startREPL(planExecutorConfigurationJsonPath, features, workspaceFolders, homeDir != null ? Paths.get(homeDir) : FileUtils.getUserDirectory().toPath().resolve(".legend/repl"));
                fireEvent(features, LegendUsageEventConsumer.event("closeReplTerminal", startTime, Instant.now(), metadata));
            }
            else
            {
                throw new IllegalStateException("No REPL feature found on current configuration.  Verify classpath...");
            }
        }
        catch (Exception e)
        {
            System.out.println("An error has occurred and cannot start the REPL terminal:");
            e.printStackTrace(System.out);
            System.out.println("The terminal will close itself in 60 seconds");
            metadata.put("error", true);
            metadata.put("errorMessage", e.getMessage());
            fireEvent(features, LegendUsageEventConsumer.event("errorReplTerminal", startTime, Instant.now(), metadata));
            Thread.sleep(60_000);
            System.exit(1);
        }

        System.exit(0);
    }

    private static void fireEvent(List<LegendLSPFeature> features, LegendUsageEventConsumer.LegendUsageEvent event)
    {
        features
                .stream()
                .filter(LegendUsageEventConsumer.class::isInstance)
                .map(LegendUsageEventConsumer.class::cast)
                .forEach(x -> x.consume(event));
    }
}
