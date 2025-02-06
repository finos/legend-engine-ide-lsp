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

package org.finos.legend.engine.ide.lsp.commands;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.CancellationToken;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageServer;

public class LegendCommandExecutionHandler implements CommandExecutionHandler
{
    public static final String LEGEND_COMMAND_ID = "legend.command";

    private final LegendCommandV2ExecutionHandler impl;

    public LegendCommandExecutionHandler(LegendLanguageServer server)
    {
        this.impl = new LegendCommandV2ExecutionHandler(server);
    }

    @Override
    public String getCommandId()
    {
        return LEGEND_COMMAND_ID;
    }

    @Override
    public Iterable<? extends LegendExecutionResult> executeCommand(ExecuteCommandParams params, CancellationToken cancellationToken)
    {
        params.getArguments().add(0, cancellationToken.getId());
        return this.impl.executeCommand(params, cancellationToken);
    }
}
