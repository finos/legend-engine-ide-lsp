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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.PreviousResultId;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.finos.legend.engine.ide.lsp.commands.CommandExecutionHandler;
import org.finos.legend.engine.ide.lsp.commands.LegendCommandExecutionHandler;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.utils.LegendToLSPUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegendWorkspaceService implements WorkspaceService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendWorkspaceService.class);

    private final LegendLanguageServer server;
    private final Map<String, CommandExecutionHandler> commandExecutionHandlers = new HashMap<>();

    LegendWorkspaceService(LegendLanguageServer server)
    {
        this.server = server;
        this.addCommandExecutionHandler(new LegendCommandExecutionHandler(server));
    }

    private void addCommandExecutionHandler(CommandExecutionHandler legendCommandExecutionHandler)
    {
        this.commandExecutionHandlers.put(legendCommandExecutionHandler.getCommandId(), legendCommandExecutionHandler);
    }

    List<String> getCommandIds()
    {
        return List.copyOf(this.commandExecutionHandlers.keySet());
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params)
    {
        LOGGER.debug("Execute command: {} {}", params.getCommand(), params.getArguments());
        return this.server.supplyPossiblyAsync(() ->
        {
            try
            {
                return doExecuteCommand(params);
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

    private Object doExecuteCommand(ExecuteCommandParams params)
    {
        String command = params.getCommand();
        Either<String, Integer> progressToken = this.server.possiblyNewProgressToken(params.getWorkDoneToken());
        Iterable<? extends LegendExecutionResult> results;
        try
        {
            this.server.logInfoToClient("Execute command: " + command);

            CommandExecutionHandler handler = this.commandExecutionHandlers.get(command);
            if (handler != null)
            {
                results = handler.executeCommand(progressToken, params);
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
            this.server.logInfoToClient("Execute command finished: " + command);
            this.server.notifyEnd(progressToken);
        }
        return results;
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
        });
    }

    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params)
    {
        // todo - retrigger classpath / classloader init?

        LOGGER.debug("Did change workspace folders: {}", params);
        WorkspaceFoldersChangeEvent event = params.getEvent();
        this.server.updateWorkspaceFolders(event.getAdded(), event.getRemoved());
    }

    @Override
    public void didCreateFiles(CreateFilesParams params)
    {
        // todo - retrigger classpath / classloader init?

        LOGGER.debug("Did create files: {}", params);
        this.server.runPossiblyAsync(() ->
        {
            LegendServerGlobalState globalState = this.server.getGlobalState();
            params.getFiles().forEach(f -> fileCreated(globalState, f.getUri()));
        });
    }

    @Override
    public void didRenameFiles(RenameFilesParams params)
    {
        LOGGER.debug("Did rename files: {}", params);
        this.server.runPossiblyAsync(() ->
        {
            LegendServerGlobalState globalState = this.server.getGlobalState();
            params.getFiles().forEach(f -> fileRenamed(globalState, f.getOldUri(), f.getNewUri()));
        });
    }

    @Override
    public void didDeleteFiles(DeleteFilesParams params)
    {
        LOGGER.debug("Did delete files: {}", params);
        this.server.runPossiblyAsync(() ->
        {
            LegendServerGlobalState globalState = this.server.getGlobalState();
            params.getFiles().forEach(f -> fileDeleted(globalState, f.getUri()));
        });
    }

    private void fileChanged(LegendServerGlobalState globalState, String uri)
    {
        LOGGER.debug("Changed {}", uri);
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
        globalState.clearProperties();
    }

    private void fileCreated(LegendServerGlobalState globalState, String uri)
    {
        LOGGER.debug("Created {}", uri);
        LegendServerGlobalState.LegendServerDocumentState docState = globalState.getOrCreateDocState(uri);
        docState.initialize();
        this.server.addRootFolderFromFile(uri);
        globalState.clearProperties();
    }

    private void fileDeleted(LegendServerGlobalState globalState, String uri)
    {
        LOGGER.debug("Deleted {}", uri);
        globalState.deleteDocState(uri);
    }

    private void fileRenamed(LegendServerGlobalState globalState, String oldUri, String newUri)
    {
        LOGGER.debug("Renamed {} to {}", oldUri, newUri);
        globalState.renameDoc(oldUri, newUri);
        this.server.addRootFolderFromFile(newUri);
        globalState.clearProperties();
    }

    @Override
    public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params)
    {
        return this.server.getGlobalState().collectFromEachDocumentSectionState((doc, sec) ->
        {
            List<WorkspaceSymbol> symbols = new ArrayList<>();
            if (sec.getExtension() != null)
            {
                sec.getExtension()
                        .getDeclarations(sec)
                        .forEach(declaration -> toWorkspaceSymbol(params, symbols, doc, declaration, null));
            }
            return symbols;
        }).thenApply(Either::forRight);
    }

    private static void toWorkspaceSymbol(WorkspaceSymbolParams params, List<WorkspaceSymbol> symbols, DocumentState doc, LegendDeclaration declaration, WorkspaceSymbol parent)
    {
        if (params.getQuery().isEmpty() || declaration.getIdentifier().toLowerCase().contains(params.getQuery().toLowerCase()))
        {
            Range range = LegendToLSPUtilities.toRange(declaration.getLocation().getTextInterval());
            Range selectionRange = declaration.hasCoreLocation() ? LegendToLSPUtilities.toRange(declaration.getCoreLocation().getTextInterval()) : range;

            String name = declaration.getIdentifier();
            String containerName = null;
            SymbolKind kind = LegendToLSPUtilities.getSymbolKind(declaration);
            Either<Location, WorkspaceSymbolLocation> location = Either.forLeft(new Location(doc.getDocumentId(), selectionRange));
            if (parent != null)
            {
                containerName = parent.getName();
                name = parent.getName() + "." + name;
                if (parent.getKind().equals(SymbolKind.Enum))
                {
                    kind = SymbolKind.EnumMember;
                }
            }
            WorkspaceSymbol workspaceSymbol = new WorkspaceSymbol(name, kind, location);
            workspaceSymbol.setContainerName(containerName);
            workspaceSymbol.setData(Map.of("classifier", declaration.getClassifier()));

            symbols.add(workspaceSymbol);

            declaration.getChildren().forEach(x -> toWorkspaceSymbol(params, symbols, doc, x, workspaceSymbol));
        }
    }

    private final AtomicReference<Map<String, Set<LegendDiagnostic>>> previousResultIdToDiagnosticReference = new AtomicReference<>(Map.of());

    @Override
    public CompletableFuture<WorkspaceDiagnosticReport> diagnostic(WorkspaceDiagnosticParams params)
    {
        Map<String, String> previousResultIds = params.getPreviousResultIds()
                .stream()
                .collect(Collectors.toMap(PreviousResultId::getUri, PreviousResultId::getValue));

        Map<String, Set<LegendDiagnostic>> previousResultIdToDiagnostic = this.previousResultIdToDiagnosticReference.get();
        Map<String, Set<LegendDiagnostic>> resultIdToDiagnostic = new ConcurrentHashMap<>();

        return this.server.getGlobalState().collectFromEachDocumentState(d ->
                {
                    String previousResultId = previousResultIds.getOrDefault(d.getDocumentId(), "");
                    Set<LegendDiagnostic> prevDiagnostic = previousResultIdToDiagnostic.get(previousResultId);

                    LegendServerGlobalState.LegendServerDocumentState doc = (LegendServerGlobalState.LegendServerDocumentState) d;
                    Set<LegendDiagnostic> diagnostics = this.server.getTextDocumentService().getLegendDiagnostics(doc);

                    String publishResultId = null;

                    // no previous results
                    if (prevDiagnostic == null)
                    {
                        // there are new diagnostics
                        if (!diagnostics.isEmpty())
                        {
                            publishResultId = UUID.randomUUID().toString();
                            resultIdToDiagnostic.put(publishResultId, diagnostics);
                        }
                    }
                    // there are previous results
                    else
                    {
                        // diagnostics are different between previous and now
                        if (!diagnostics.equals(prevDiagnostic))
                        {
                            publishResultId = UUID.randomUUID().toString();
                            resultIdToDiagnostic.put(publishResultId, diagnostics);
                        }
                        // only track old diagnostics if same as new and non-empty (ie don't track empty ones)
                        // otherwise, next time prevDiagnostic will be null, and only we start tracking again if there are new diagnostics
                        else if (!diagnostics.isEmpty())
                        {
                            resultIdToDiagnostic.put(previousResultId, diagnostics);
                        }
                    }

                    if (publishResultId != null)
                    {
                        WorkspaceFullDocumentDiagnosticReport fullReport = new WorkspaceFullDocumentDiagnosticReport(diagnostics.stream().map(LegendToLSPUtilities::toDiagnostic).collect(Collectors.toList()), doc.getDocumentId(), doc.getVersion());
                        fullReport.setResultId(publishResultId);
                        return List.of(new WorkspaceDocumentDiagnosticReport(fullReport));
                    }

                    return List.of();
                }
        ).thenApply(x ->
        {
            this.previousResultIdToDiagnosticReference.compareAndSet(previousResultIdToDiagnostic, resultIdToDiagnostic);
            return new WorkspaceDiagnosticReport(x);
        });
    }
}
