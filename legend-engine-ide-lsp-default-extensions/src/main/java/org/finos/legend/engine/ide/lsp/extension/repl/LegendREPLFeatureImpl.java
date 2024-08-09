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
import org.finos.legend.engine.repl.client.Client;
import org.finos.legend.engine.repl.dataCube.DataCubeReplExtension;
import org.finos.legend.engine.repl.relational.RelationalReplExtension;
import org.finos.legend.engine.repl.relational.autocomplete.RelationalCompleterExtension;

import java.nio.file.Path;
import java.util.List;

public class LegendREPLFeatureImpl implements LegendREPLFeature
{
    @Override
    public String description()
    {
        return "Legend REPL";
    }

    public Client buildREPL(Path planExecutorConfigurationJsonPath, List<LegendLSPFeature> features)
    {
        try
        {
            return new Client(
                    Lists.mutable.with(
                            new LSPReplExtension(),
                            new RelationalReplExtension(),
                            new DataCubeReplExtension()
                    ),
                    Lists.mutable.with(
                            new RelationalCompleterExtension()
                    ),
                    PlanExecutorConfigurator.create(planExecutorConfigurationJsonPath, features)
            );
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void startREPL(Path planExecutorConfigurationJsonPath, List<LegendLSPFeature> features)
    {
        Client client = this.buildREPL(planExecutorConfigurationJsonPath, features);
        client.loop();
        client.forceExit();
    }
}
