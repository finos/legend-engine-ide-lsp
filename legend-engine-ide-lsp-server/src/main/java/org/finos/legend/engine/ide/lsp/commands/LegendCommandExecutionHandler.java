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

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageServer;

public class LegendCommandExecutionHandler implements CommandExecutionHandler
{
    public static final String LEGEND_COMMAND_ID = "legend.command";

    private final LegendLanguageServer server;

    public LegendCommandExecutionHandler(LegendLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public String getCommandId()
    {
        return LEGEND_COMMAND_ID;
    }

    @Override
    public Iterable<? extends LegendExecutionResult> executeCommand(Either<String, Integer> progressToken, ExecuteCommandParams params)
    {
        List<Object> args = params.getArguments();

        if (args.get(0) instanceof JsonObject)
        {
            return this.executeCommandWithTextLocation(progressToken, args);
        }
        else
        {
            return this.executeCommandWithDocumentAndSection(progressToken, args);
        }
    }

    private Iterable<? extends LegendExecutionResult> executeCommandWithDocumentAndSection(Either<String, Integer> progressToken, List<Object> args)
    {
        String uri = this.server.extractValueAs(args.get(0), String.class);
        int section = this.server.extractValueAs(args.get(1), Integer.class);
        String entity = this.server.extractValueAs(args.get(2), String.class);
        String id = this.server.extractValueAs(args.get(3), String.class);
        Map<String, String> executableArgs = ((args.size() < 5) || (args.get(4) == null)) ? Collections.emptyMap() : this.server.extractValueAsMap(args.get(4), String.class, String.class);
        Map<String, Object> inputParameters = ((args.size() < 6) || (args.get(5) == null)) ? Collections.emptyMap() : this.server.extractValueAsMap(args.get(5), String.class, Object.class);

        return this.server.runAndFireEvent(id, () ->
        {
            Iterable<? extends LegendExecutionResult> results;

            this.server.notifyBegin(progressToken, entity);

            GlobalState globalState = this.server.getGlobalState();
            DocumentState docState = globalState.getDocumentState(uri);
            if (docState == null)
            {
                throw new RuntimeException("Unknown document: " + uri);
            }

            SectionState sectionState = docState.getSectionState(section);
            LegendLSPGrammarExtension extension = sectionState.getExtension();
            if (extension == null)
            {
                throw new RuntimeException("Could not execute command " + id + " for entity " + entity + " in section " + sectionState.getSectionNumber() + " of " + uri + ": no extension found");
            }

            try
            {
                results = inputParameters.isEmpty() ? extension.execute(sectionState, entity, id, executableArgs) : extension.execute(sectionState, entity, id, executableArgs, inputParameters);
            }
            catch (Throwable e)
            {
                String message = "Command execution " + id + " for entity " + entity + " in section " + sectionState.getSectionNumber() + " of " + uri + " failed.";
                results = Collections.singletonList(LegendExecutionResult.errorResult(new Exception(message, e), message, entity, null));
            }

            return results;
        });
    }

    private Iterable<? extends LegendExecutionResult> executeCommandWithTextLocation(Either<String, Integer> progressToken, List<Object> args)
    {
        TextLocation textLocation = this.server.extractValueAs(args.get(0), TextLocation.class);
        String id = this.server.extractValueAs(args.get(1), String.class);
        Map<String, String> executableArgs = ((args.size() < 3) || (args.get(2) == null)) ? Collections.emptyMap() : this.server.extractValueAsMap(args.get(2), String.class, String.class);
        Map<String, Object> inputParameters = ((args.size() < 4) || (args.get(3) == null)) ? Collections.emptyMap() : this.server.extractValueAsMap(args.get(3), String.class, Object.class);

        String uri = textLocation.getDocumentId();

        return this.server.runAndFireEvent(id, () ->
        {
            Iterable<? extends LegendExecutionResult> results;

            GlobalState globalState = this.server.getGlobalState();
            DocumentState docState = globalState.getDocumentState(uri);
            if (docState == null)
            {
                throw new RuntimeException("Unknown document: " + uri);
            }

            TextPosition start = textLocation.getTextInterval().getStart();
            SectionState sectionState = docState.getSectionStateAtLine(start.getLine());
            LegendLSPGrammarExtension extension = sectionState.getExtension();
            if (extension == null)
            {
                throw new RuntimeException("Could not execute command " + id + " @ " + textLocation + ": no extension found");
            }

            String entity = extension.getDeclaration(sectionState, start).orElseThrow(() -> new RuntimeException("Could not execute command " + id + " @ " + textLocation + ": no entity found")).getIdentifier();

            this.server.notifyBegin(progressToken, entity);

            try
            {
                results = inputParameters.isEmpty() ? extension.execute(sectionState, entity, id, executableArgs) : extension.execute(sectionState, entity, id, executableArgs, inputParameters);
            }
            catch (Throwable e)
            {
                String message = "Command execution " + id + " for entity " + entity + " in section " + sectionState.getSectionNumber() + " of " + uri + " failed.";
                results = Collections.singletonList(LegendExecutionResult.errorResult(new Exception(message, e), message, entity, null));
            }

            return results;
        });
    }
}
