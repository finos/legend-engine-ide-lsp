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

import java.util.List;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageServer;

public class LegendCancelCommandExecutionHandler implements CommandExecutionHandler
{
    public static final String LEGEND_CANCEL_COMMAND_ID = "legend.cancel.command";

    private final LegendLanguageServer server;

    public LegendCancelCommandExecutionHandler(LegendLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public String getCommandId()
    {
        return LEGEND_CANCEL_COMMAND_ID;
    }

    @Override
    public Iterable<? extends LegendExecutionResult> executeCommand(Either<String, Integer> progressToken, ExecuteCommandParams params)
    {
        String requestId = this.server.extractValueAs(params.getArguments().get(0), String.class);
        this.server.getGlobalState().cancellationToken(requestId).cancel();
        return List.of();
    }
}
