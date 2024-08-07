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

package org.finos.legend.engine.ide.lsp.extension.repl;

import org.eclipse.collections.impl.factory.Lists;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPFeature;
import org.finos.legend.engine.ide.lsp.extension.PlanExecutorConfigurator;
import org.finos.legend.engine.ide.lsp.extension.features.LegendREPLFeature;
import org.finos.legend.engine.ide.lsp.extension.features.LegendVirtualFileSystemContentInitializer;
import org.finos.legend.engine.ide.lsp.extension.sdlc.LegendDependencyManagement;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.repl.autocomplete.CompleterExtension;
import org.finos.legend.engine.repl.client.Client;
import org.finos.legend.engine.repl.relational.RelationalReplExtension;
import org.finos.legend.engine.repl.relational.autocomplete.RelationalCompleterExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class LegendREPLFeatureImpl implements LegendREPLFeature
{
    @Override
    public String description()
    {
        return "Legend REPL";
    }

    @Override
    public void startREPL(Path planExecutorConfigurationJsonPath, List<LegendLSPFeature> features)
    {
        try
        {
            PlanExecutor planExecutor = PlanExecutorConfigurator.create(planExecutorConfigurationJsonPath, features);
            (new Client(Lists.mutable.with(new LSPReplExtension(), new RelationalReplExtension()), Lists.mutable.with(new CompleterExtension[]{new RelationalCompleterExtension()}), planExecutor)).loop();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
