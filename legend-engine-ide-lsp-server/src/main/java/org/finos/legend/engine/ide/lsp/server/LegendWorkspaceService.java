// Copyright 2023 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.engine.ide.lsp.server;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LegendWorkspaceService implements WorkspaceService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendWorkspaceService.class);

    private final LegendLanguageServer server;

    LegendWorkspaceService(LegendLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params)
    {
        LOGGER.debug("Execute command: {} {}", params.getCommand(), params.getArguments());
        return this.server.supplyPossiblyAsync(() ->
        {
            try
            {
                doExecuteCommand(params);
                return "";
            }
            catch (Exception e)
            {
                String message = e.getMessage();
                this.server.logErrorToClient((message == null) ? ("Error executing command: " + params.getCommand()) : message);
                LOGGER.error("Error executing command: {} {}", params.getCommand(), params.getArguments(), e);
                throw e;
            }
        });
    }

    private void doExecuteCommand(ExecuteCommandParams params)
    {
        Either<String, Integer> progressToken = this.server.possiblyNewProgressToken(params.getWorkDoneToken());
        try
        {
            this.server.logInfoToClient("Execute command: " + params.getCommand());
            String command = params.getCommand();
            List<Object> args = params.getArguments();
            if (LegendLanguageServer.LEGEND_COMMAND_ID.equals(command))
            {
                String uri = this.server.extractValueAs(args.get(0), String.class);
                int sectionNum = this.server.extractValueAs(args.get(1), Integer.class);
                String entity = this.server.extractValueAs(args.get(2), String.class);
                String id = this.server.extractValueAs(args.get(3), String.class);
                Map<String, String> executableArgs = ((args.size() < 5) || (args.get(4) == null)) ? Collections.emptyMap() : this.server.extractValueAsMap(args.get(4), String.class, String.class);
                this.server.notifyBegin(progressToken, entity);

                LegendServerGlobalState globalState = this.server.getGlobalState();
                synchronized (globalState)
                {
                    DocumentState docState = globalState.getDocumentState(uri);
                    if (docState == null)
                    {
                        throw new RuntimeException("Unknown document: " + uri);
                    }

                    SectionState sectionState = docState.getSectionState(sectionNum);
                    LegendLSPGrammarExtension extension = sectionState.getExtension();
                    if (extension == null)
                    {
                        throw new RuntimeException("Could not execute command " + id + " for entity " + entity + " in section " + sectionNum + " of " + uri + ": no extension found");
                    }
                    Iterable<? extends LegendExecutionResult> results;

                    try
                    {
                        results = extension.execute(sectionState, entity, id, executableArgs);
                    }
                    catch (Throwable e)
                    {
                        String message = "Command execution " + id + " for entity " + entity + " in section " + sectionNum + " of " + uri + " failed.";
                        results = Collections.singletonList(LegendExecutionResult.errorResult(new Exception(message, e), message, entity));
                    }
                    this.server.notifyResults(progressToken, results);
                    results.forEach(result ->
                    {
                        switch (result.getType())
                        {
                            case SUCCESS:
                            {
                                String message = result.getMessage();
                                String logMessage = result.getLogMessage();
                                if ((logMessage == null) || logMessage.equals(message))
                                {
                                    this.server.logInfoToClient(message);
                                }
                                else
                                {
                                    this.server.showInfoToClient(message);
                                    this.server.logInfoToClient(logMessage);
                                }
                                break;
                            }
                            case FAILURE:
                            case WARNING:
                            {
                                this.server.showWarningToClient(result.getMessage());
                                this.server.logWarningToClient(result.getLogMessage(true));
                                break;
                            }
                            case ERROR:
                            {
                                this.server.showErrorToClient(result.getMessage());
                                this.server.logErrorToClient(result.getLogMessage(true));
                                break;
                            }
                            default:
                            {
                                LOGGER.warn("Unhandled result type: {}", result.getType());
                                this.server.showInfoToClient(result.getMessage());
                                this.server.logInfoToClient(result.getLogMessage(true));
                            }
                        }
                    });
                }
            }
            else
            {
                throw this.server.newResponseErrorException(ResponseErrorCode.InvalidParams, "Unknown command: " + command);
            }
        }
        catch (Exception e)
        {
            this.server.logErrorToClient("Error during command execution" + ((e.getMessage() == null) ? "" : (": " + e.getMessage())));
            throw e;
        }
        finally
        {
            this.server.logInfoToClient("Execute command finished: " + params.getCommand());
            this.server.notifyEnd(progressToken);
        }
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params)
    {
        this.server.checkReady();
        LOGGER.debug("Did change configuration: {}", params);
        // todo maybe server can have a map of configs to consumers of these properties
        // todo if default pom changes? this.server.initializeExtensions();
        // todo this.server.setEngineServerUrl();
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
    {
        // todo - retrigger classpath / classloader init?
        // todo this.server.initializeExtensions();

        LOGGER.debug("Did change watched files: {}", params);
        this.server.runPossiblyAsync(() ->
        {
            LegendServerGlobalState globalState = this.server.getGlobalState();
            synchronized (globalState)
            {
                params.getChanges().forEach(fileEvent ->
                {
                    String uri = fileEvent.getUri();
                    switch (fileEvent.getType())
                    {
                        case Changed:
                        {
                            fileChanged(globalState, uri);
                            break;
                        }
                        case Created:
                        {
                            fileCreated(globalState, uri);
                            break;
                        }
                        case Deleted:
                        {
                            fileDeleted(globalState, uri);
                            break;
                        }
                        default:
                        {
                            LOGGER.warn("Unhandled change type for {}: {}", uri, fileEvent.getType());
                        }
                    }
                });
            }
        });
    }

    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params)
    {
        // todo - retrigger classpath / classloader init?

        LOGGER.debug("Did change workspace folders: {}", params);
        synchronized (this.server.getGlobalState())
        {
            WorkspaceFoldersChangeEvent event = params.getEvent();
            this.server.updateWorkspaceFolders(event.getAdded(), event.getRemoved());
        }
    }

    @Override
    public void didCreateFiles(CreateFilesParams params)
    {
        // todo - retrigger classpath / classloader init?

        LOGGER.debug("Did create files: {}", params);
        this.server.runPossiblyAsync(() ->
        {
            LegendServerGlobalState globalState = this.server.getGlobalState();
            synchronized (globalState)
            {
                params.getFiles().forEach(f -> fileCreated(globalState, f.getUri()));
            }
        });
    }

    @Override
    public void didRenameFiles(RenameFilesParams params)
    {
        LOGGER.debug("Did rename files: {}", params);
        this.server.runPossiblyAsync(() ->
        {
            LegendServerGlobalState globalState = this.server.getGlobalState();
            synchronized (globalState)
            {
                params.getFiles().forEach(f -> fileRenamed(globalState, f.getOldUri(), f.getNewUri()));
            }
        });
    }

    @Override
    public void didDeleteFiles(DeleteFilesParams params)
    {
        LOGGER.debug("Did delete files: {}", params);
        this.server.runPossiblyAsync(() ->
        {
            LegendServerGlobalState globalState = this.server.getGlobalState();
            synchronized (globalState)
            {
                params.getFiles().forEach(f -> fileDeleted(globalState, f.getUri()));
            }
        });
    }

    private void fileChanged(LegendServerGlobalState globalState, String uri)
    {
        LOGGER.debug("Changed {}", uri);
        globalState.clearProperties();
        LegendServerGlobalState.LegendServerDocumentState docState = globalState.getOrCreateDocState(uri);
        if (docState.isOpen())
        {
            String message = "Open file has changed: " + uri;
            LOGGER.warn(message);
            this.server.logWarningToClient(message);
        }
        else
        {
            docState.loadText();
        }
        this.server.addRootFolderFromFile(uri);
    }

    private void fileCreated(LegendServerGlobalState globalState, String uri)
    {
        LOGGER.debug("Created {}", uri);
        globalState.clearProperties();
        LegendServerGlobalState.LegendServerDocumentState docState = globalState.getOrCreateDocState(uri);
        docState.initialize();
        this.server.addRootFolderFromFile(uri);
    }

    private void fileDeleted(LegendServerGlobalState globalState, String uri)
    {
        LOGGER.debug("Deleted {}", uri);
        globalState.clearProperties();
        globalState.deleteDocState(uri);
    }

    private void fileRenamed(LegendServerGlobalState globalState, String oldUri, String newUri)
    {
        LOGGER.debug("Renamed {} to {}", oldUri, newUri);
        globalState.clearProperties();
        globalState.renameDoc(oldUri, newUri);
        this.server.addRootFolderFromFile(newUri);
    }
}
