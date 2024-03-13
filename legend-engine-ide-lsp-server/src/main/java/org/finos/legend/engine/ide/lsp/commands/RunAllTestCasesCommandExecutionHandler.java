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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageServer;

public class RunAllTestCasesCommandExecutionHandler implements CommandExecutionHandler
{
    public static final String RUN_ALL_TESTS_COMMAND = "legend.runAllTests.command";

    private final LegendLanguageServer server;

    public RunAllTestCasesCommandExecutionHandler(LegendLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public String getCommandId()
    {
        return RUN_ALL_TESTS_COMMAND;
    }

    @Override
    public Iterable<? extends LegendExecutionResult> executeCommand(Either<String, Integer> progressToken, ExecuteCommandParams params)
    {
        this.server.notifyBegin(progressToken, "Running all tests cases");

        List<Stream<? extends LegendExecutionResult>> streams = new ArrayList<>();

        this.server.getGlobalState().forEachDocumentState(docState ->
        {
            docState.forEachSectionState(sectionState ->
            {
                LegendLSPGrammarExtension extension = sectionState.getExtension();
                if (extension == null)
                {
                    return;
                }
                streams.add(extension.executeAllTestCases(sectionState));
            });
        });

        Stream<? extends LegendExecutionResult> legendExecutionResultStream = streams.stream().flatMap(Function.identity());
        return legendExecutionResultStream.collect(Collectors.toList());
    }
}
