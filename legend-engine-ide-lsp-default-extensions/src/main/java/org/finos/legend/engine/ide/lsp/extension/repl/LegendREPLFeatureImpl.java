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

import org.finos.legend.engine.ide.lsp.extension.features.LegendREPLFeature;
import org.finos.legend.engine.plan.execution.stores.relational.AlloyH2Server;
import org.finos.legend.engine.repl.relational.client.RClient;
import org.h2.tools.Server;

public class LegendREPLFeatureImpl implements LegendREPLFeature
{
    @Override
    public String description()
    {
        return "Legend REPL";
    }

    @Override
    public void startREPL()
    {
        Server server = null;
        try
        {
            server = AlloyH2Server.startServer(1975);
            RClient.main(new String[0]);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            if (server != null)
            {
                server.stop();
            }
        }
    }
}
