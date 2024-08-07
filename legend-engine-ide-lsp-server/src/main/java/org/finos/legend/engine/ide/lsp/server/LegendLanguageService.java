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

import java.io.File;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.finos.legend.engine.ide.lsp.extension.LegendEntity;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.features.LegendSDLCFeature;
import org.finos.legend.engine.ide.lsp.extension.features.LegendTDSRequestHandler;
import org.finos.legend.engine.ide.lsp.extension.features.LegendVirtualFileSystemContentInitializer;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTest;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestExecutionResult;
import org.finos.legend.engine.ide.lsp.server.request.LegendEntitiesRequest;
import org.finos.legend.engine.ide.lsp.server.request.LegendJsonToPureRequest;
import org.finos.legend.engine.ide.lsp.server.service.ExecuteTestRequest;
import org.finos.legend.engine.ide.lsp.server.service.FunctionTDSRequest;
import org.finos.legend.engine.ide.lsp.server.service.LegendLanguageServiceContract;

public class LegendLanguageService implements LegendLanguageServiceContract
{
    private static final String LEGEND_VIRTUAL_FS_SCHEME = "legend-vfs:/";
    private static final String JSON_ENTITY_DIRECTORY = "/src/main/legend/";

    private final LegendLanguageServer server;

    public LegendLanguageService(LegendLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public CompletableFuture<LegendExecutionResult> legendTDSRequest(FunctionTDSRequest request)
    {
        return this.server.supplyPossiblyAsync(() -> this.server.runAndFireEvent("TDSRequest", () ->
                {
                    LegendExecutionResult result;
                    LegendServerGlobalState globalState = this.server.getGlobalState();
                    String uri = request.getUri();
                    int sectionNum = request.getSectionNum();
                    String entity = request.getEntity();
                    DocumentState docState = globalState.getDocumentState(uri);
                    if (docState == null)
                    {
                        this.server.logWarningToClient("Cannot get TDS request result for " + uri + ": not open in language server");
                        return LegendExecutionResult.errorResult(new Exception("Cannot get TDS request result for " + uri + ": not open in language server"), "", entity, null);
                    }

                    try
                    {
                        LegendTDSRequestHandler handler = globalState.findFeatureThatImplements(LegendTDSRequestHandler.class).findAny().orElseThrow(() -> new RuntimeException("Could not execute legend TDS request for entity " + entity + " in section " + sectionNum + " of " + uri + ": no extension found"));
                        SectionState sectionState = docState.getSectionState(sectionNum);
                        result = handler.executeLegendTDSRequest(sectionState, entity, request.getRequest(), request.getInputParameters());
                    }
                    catch (Throwable e)
                    {
                        this.server.logInfoToClient(e.getMessage());
                        String message = "TDS request execution for entity " + entity + " in section " + sectionNum + " of " + uri + " failed.";
                        result = LegendExecutionResult.errorResult(new Exception(message, e), message, entity, null);
                    }
                    this.server.logInfoToClient(result.getMessage());
                    return result;
                }, Map.of("function", request.getEntity()))
        );
    }

    @Override
    public CompletableFuture<String> replClasspath()
    {
        return this.server.supplyPossiblyAsync(() ->
        {
            String classpath = System.getProperty("java.class.path");
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader instanceof URLClassLoader)
            {
                URLClassLoader urlClassLoader = (URLClassLoader) contextClassLoader;

                if ("legend-lsp".equals(urlClassLoader.getName()))
                {
                    classpath = classpath + File.pathSeparator + Arrays.stream(urlClassLoader.getURLs())
                            .map(x ->
                            {
                                try
                                {
                                    return Path.of(x.toURI()).toAbsolutePath().toString();
                                }
                                catch (Exception e)
                                {
                                    throw new RuntimeException(e);
                                }
                            })
                            .collect(Collectors.joining(File.pathSeparator));
                }
            }

            return classpath;
        });
    }

    @Override
    public CompletableFuture<List<LegendTest>> testCases()
    {
        return this.server.supplyPossiblyAsync(() ->
        {
            List<LegendTest> commands = new ArrayList<>();

            this.server.getGlobalState().forEachDocumentState(docState ->
            {
                docState.forEachSectionState(sectionState ->
                {
                    LegendLSPGrammarExtension extension = sectionState.getExtension();
                    if (extension != null)
                    {
                        commands.addAll(extension.testCases(sectionState));
                    }
                });
            });

            return commands;
        });
    }

    @Override
    public CompletableFuture<List<LegendTestExecutionResult>> executeTests(ExecuteTestRequest rq)
    {
        return this.server.supplyPossiblyAsync(() -> this.server.runAndFireEvent("executeTests", () ->
                {
                    LegendServerGlobalState.LegendServerDocumentState documentState = this.server.getGlobalState().getDocumentState(rq.getLocation().getDocumentId());
                    SectionState sectionStateAtLine = documentState.getSectionStateAtLine(rq.getLocation().getTextInterval().getStart().getLine());
                    LegendLSPGrammarExtension extension = sectionStateAtLine.getExtension();
                    return extension.executeTests(sectionStateAtLine, rq.getLocation(), rq.getTestId(), new HashSet<>(rq.getExcludedTestIds()));
                }, Map.of("testId", rq.getTestId()))
        );
    }

    @Override
    public CompletableFuture<String> loadLegendVirtualFile(String uri)
    {
        return this.server.supplyPossiblyAsync(() ->
        {
            if (uri.startsWith(LEGEND_VIRTUAL_FS_SCHEME))
            {
                LegendServerGlobalState.LegendServerDocumentState documentState = this.server.getGlobalState().getDocumentState(uri);
                if (documentState == null)
                {
                    throw new IllegalArgumentException("Provided URI does not exists on Legend Virtual Filesystem: " + uri);
                }
                else
                {
                    return documentState.getText();
                }
            }
            else
            {
                throw new IllegalArgumentException("Provided URI not managed by Legend Virtual Filesystem: " + uri);
            }
        });
    }

    protected void loadVirtualFileSystemContent()
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();

        globalState.removeFolder(LEGEND_VIRTUAL_FS_SCHEME);

        globalState.findFeatureThatImplements(LegendVirtualFileSystemContentInitializer.class)
                .map(LegendVirtualFileSystemContentInitializer::getVirtualFilePureGrammars)
                .flatMap(List::stream)
                .forEach(virtualFile ->
                {
                    String uri = LEGEND_VIRTUAL_FS_SCHEME + virtualFile.getPath();
                    LegendServerGlobalState.LegendServerDocumentState dependenciesDocument = globalState.getOrCreateDocState(uri);
                    dependenciesDocument.save(virtualFile.getContent());
                });
    }

    @Override
    public CompletableFuture<List<LegendEntity>> entities(LegendEntitiesRequest request)
    {
        return this.server.supplyPossiblyAsync(() ->
        {
            List<LegendEntity> entities = new ArrayList<>();

            Consumer<DocumentState> collectEntities = docState -> docState.forEachSectionState(sectionState ->
            {
                LegendLSPGrammarExtension extension = sectionState.getExtension();
                if (extension != null)
                {
                    extension.getEntities(sectionState).forEach(entities::add);
                }
            });

            if (request.getTextDocuments().isEmpty())
            {
                this.server.getGlobalState().forEachDocumentState(collectEntities);
            }
            else
            {
                request.getTextDocuments().stream()
                        .map(TextDocumentIdentifier::getUri)
                        .map(this.server.getGlobalState()::getDocumentState)
                        .forEach(collectEntities);
            }

            return entities;
        });
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> jsonEntitiesToPureTextWorkspaceEdits(LegendJsonToPureRequest request)
    {
        LegendSDLCFeature handler = this.server.getGlobalState().findFeatureThatImplements(LegendSDLCFeature.class).findAny().orElseThrow(() -> new RuntimeException("Could not find feature to convert json to pure files"));

        return this.server.supplyPossiblyAsync(() ->
        {
            List<Either<TextDocumentEdit, ResourceOperation>> edits = new ArrayList<>();

            for (String jsonFileUri : request.getJsonFileUris())
            {
                int indexOfJsonEntityDirectory = jsonFileUri.indexOf(JSON_ENTITY_DIRECTORY);
                if (indexOfJsonEntityDirectory != -1)
                {
                    try
                    {
                        Path path = Paths.get(URI.create(jsonFileUri));
                        List<String> jsonText = Files.readAllLines(path);
                        String pureText = "// Converted by Legend LSP from JSON file: " + jsonFileUri.substring(indexOfJsonEntityDirectory + 1) + "\n"
                                + handler.entityJsonToPureText(String.join("", jsonText));

                        String pureFileUri = jsonFileUri
                                .replace(JSON_ENTITY_DIRECTORY, "/src/main/pure/")
                                .replace(".json", ".pure");

                        // rename json file to pure file
                        edits.add(Either.forRight(new RenameFile(jsonFileUri, pureFileUri)));

                        // add pure content to it
                        VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier(pureFileUri, null);
                        TextEdit addPureContent = new TextEdit(
                                new Range(
                                        new Position(0, 0),
                                        new Position(jsonText.size(), 0)
                                ), pureText);
                        edits.add(Either.forLeft(new TextDocumentEdit(textDocument, List.of(addPureContent))));

                        this.server.logInfoToClient("Converting JSON protocol to Pure text for: "
                                + jsonFileUri + " -> " + pureFileUri);
                    }
                    catch (Exception e)
                    {
                        this.server.logErrorToClient("Failed to convert JSON to pure for: " + jsonFileUri + " - " + e.getMessage());
                    }
                }
            }

            WorkspaceEdit workspaceEdit = new WorkspaceEdit(edits);
            ApplyWorkspaceEditParams applyWorkspaceEditParams = new ApplyWorkspaceEditParams(workspaceEdit, "Convert JSON protocol files to Legend language text");
            return this.server.getLanguageClient().applyEdit(applyWorkspaceEditParams);
        }).thenCompose(x -> x);
    }
}
