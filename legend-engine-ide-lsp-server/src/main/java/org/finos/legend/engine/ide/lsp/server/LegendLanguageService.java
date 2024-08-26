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

import org.eclipse.lsp4j.*;
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
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.server.request.LegendEntitiesRequest;
import org.finos.legend.engine.ide.lsp.server.request.LegendJsonToPureRequest;
import org.finos.legend.engine.ide.lsp.server.request.LegendWriteEntityRequest;
import org.finos.legend.engine.ide.lsp.server.service.ExecuteTestRequest;
import org.finos.legend.engine.ide.lsp.server.service.FunctionTDSRequest;
import org.finos.legend.engine.ide.lsp.server.service.LegendLanguageServiceContract;
import org.finos.legend.engine.ide.lsp.utils.LegendToLSPUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class LegendLanguageService implements LegendLanguageServiceContract
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendLanguageService.class);

    private static final String LEGEND_VIRTUAL_FS_SCHEME = "legend-vfs:/";
    private static final String JSON_ENTITY_DIRECTORY = "/src/main/legend/";
    public static final String PURE_FILE_DIRECTORY = "/src/main/pure/";

    private final LegendLanguageServer server;
    private volatile LegendSDLCFeature sdlcHandler;

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
        try
        {
            return this.server.supplyPossiblyAsync(() ->
            {
                try
                {
                    // Wait for post-initialization to complete before collecting the classpath for REPL
                    server.getPostInitializationLatch().await();
                    return null;
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }).thenComposeAsync(x ->
                    this.server.supplyPossiblyAsync(() ->
                    {
                        String classpath = System.getProperty("java.class.path");
                        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                        if (contextClassLoader instanceof URLClassLoader)
                        {
                            URLClassLoader urlClassLoader = (URLClassLoader) contextClassLoader;
                            if ("legend-lsp".equals(urlClassLoader.getName()))
                            {
                                classpath = classpath + File.pathSeparator + Arrays.stream(urlClassLoader.getURLs())
                                        .map(url ->
                                        {
                                            try
                                            {
                                                return Path.of(url.toURI()).toAbsolutePath().toString();
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
                    }));
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
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
                LegendServerGlobalState globalState = this.server.getGlobalState();
                Optional<LegendVirtualFileSystemContentInitializer.LegendVirtualFile> legendVirtualFile = globalState.findFeatureThatImplements(LegendVirtualFileSystemContentInitializer.class)
                        .map(LegendVirtualFileSystemContentInitializer::getVirtualFilePureGrammars)
                        .flatMap(List::stream)
                        .filter(x -> (LEGEND_VIRTUAL_FS_SCHEME + x.getPath()).equals(uri))
                        .findAny();

                if (legendVirtualFile.isPresent())
                {
                    return legendVirtualFile.get().getContent();
                }
                else
                {
                    throw new IllegalArgumentException("Provided URI does not exists on Legend Virtual Filesystem: " + uri);
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
                .filter(virtualFile -> virtualFile.getPath().toString().endsWith(".pure"))
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
                        .filter(Objects::nonNull)
                        .forEach(collectEntities);
            }

            return entities;
        });
    }

    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> jsonEntitiesToPureTextWorkspaceEdits(LegendJsonToPureRequest request)
    {
        return this.server.supplyPossiblyAsync(() ->
        {
            LegendSDLCFeature handler = this.getSDLCHandler();

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
                                .replace(JSON_ENTITY_DIRECTORY, PURE_FILE_DIRECTORY)
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
                        this.server.logErrorToClient("Failed to convert JSON to pure for: " + jsonFileUri, e);
                    }
                }
            }

            WorkspaceEdit workspaceEdit = new WorkspaceEdit(edits);
            ApplyWorkspaceEditParams applyWorkspaceEditParams = new ApplyWorkspaceEditParams(workspaceEdit, "Convert JSON protocol files to Legend language text");
            return this.server.getLanguageClient().applyEdit(applyWorkspaceEditParams);
        }).thenCompose(x -> x);
    }

    @Override
    public CompletableFuture<Void> oneEntityPerFileRefactoring()
    {
        CompletableFuture<ApplyWorkspaceEditParams> oneEntityPerFileRefactoring = this.server.supplyPossiblyAsync(() ->
                {
                    LegendSDLCFeature handler = this.getSDLCHandler();

                    Set<Path> existingDocumentIdsToDelete = new TreeSet<>();
                    Map<Path, String> editContentPerFile = new TreeMap<>();

                    Map<Path, DocumentState> docStatesToRefactor = new TreeMap<>();

                    this.server.getGlobalState().forEachDocumentState(documentState ->
                            {
                                if (!documentState.getDocumentId().startsWith(LEGEND_VIRTUAL_FS_SCHEME))
                                {
                                    docStatesToRefactor.put(Path.of(URI.create(documentState.getDocumentId())), documentState);
                                }
                            }
                    );

                    for (Path documentStateToRefactorPath : docStatesToRefactor.keySet())
                    {
                        DocumentState documentState = docStatesToRefactor.get(documentStateToRefactorPath);

                        Path root;

                        int indexOfPureFileDir = documentStateToRefactorPath.toUri().toString().indexOf(PURE_FILE_DIRECTORY);
                        if (indexOfPureFileDir >= 0)
                        {
                            root = Path.of(URI.create(documentStateToRefactorPath.toUri().toString().substring(0, indexOfPureFileDir + PURE_FILE_DIRECTORY.length())));
                        }
                        else
                        {
                            root = Path.of(URI.create(this.server.rootFolders.iterator().next()));
                        }

                        Map<Path, String> editContentForDocState = handler.convertToOneElementPerFile(root, documentState);
                        // if result is single entity and on same file as input, don't edit it
                        if (editContentForDocState.size() == 1)
                        {
                            Path onlyPath = editContentForDocState.keySet().iterator().next();
                            if (!onlyPath.equals(Path.of(URI.create(documentState.getDocumentId()))))
                            {
                                existingDocumentIdsToDelete.add(documentStateToRefactorPath);
                                editContentPerFile.putAll(editContentForDocState);
                            }
                            else
                            {
                                this.server.logInfoToClient("One entity per file refactoring - skipping file as content is up-to-date: " + documentState.getDocumentId());
                            }
                        }
                        else
                        {
                            existingDocumentIdsToDelete.add(documentStateToRefactorPath);
                            editContentPerFile.putAll(editContentForDocState);
                        }
                    }

                    List<Either<TextDocumentEdit, ResourceOperation>> edits = new ArrayList<>();

                    for (Path path : editContentPerFile.keySet())
                    {
                        String newContent = editContentPerFile.get(path);
                        String documentId = path.toUri().toString();

                        LegendServerGlobalState.LegendServerDocumentState documentState = (LegendServerGlobalState.LegendServerDocumentState) docStatesToRefactor.get(path);
                        if (documentState != null)
                        {
                            this.server.logInfoToClient("One entity per file refactoring - updating existing file: " + documentId);
                            // update pure content of existing file
                            VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier(documentId, documentState.getVersion());
                            TextEdit addPureContent = new TextEdit(
                                    new Range(
                                            new Position(0, 0),
                                            new Position(documentState.getLineCount() + 1, 0)
                                    ), newContent);
                            edits.add(Either.forLeft(new TextDocumentEdit(textDocument, List.of(addPureContent))));
                        }
                        else
                        {
                            this.server.logInfoToClient("One entity per file refactoring - creating new file: " + documentId);
                            // create new file
                            edits.add(Either.forRight(new CreateFile(documentId, new CreateFileOptions(null, true))));

                            // update content on it
                            VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier(documentId, null);
                            TextEdit addPureContent = new TextEdit(
                                    new Range(
                                            new Position(0, 0),
                                            new Position(0, 0)
                                    ), newContent);
                            edits.add(Either.forLeft(new TextDocumentEdit(textDocument, List.of(addPureContent))));
                        }

                        existingDocumentIdsToDelete.remove(path);
                    }

                    for (Path documentId : existingDocumentIdsToDelete)
                    {
                        this.server.logInfoToClient("One entity per file refactoring - deleting existing file: " + documentId);
                        edits.add(Either.forRight(new DeleteFile(documentId.toUri().toString())));
                    }

                    WorkspaceEdit workspaceEdit = new WorkspaceEdit(edits);
                    return new ApplyWorkspaceEditParams(workspaceEdit, "One entity per file refactoring");
                }
        );

        return this.handleEdits(oneEntityPerFileRefactoring);
    }

    private LegendSDLCFeature getSDLCHandler()
    {
        if (this.sdlcHandler == null)
        {
            this.sdlcHandler = this.server.getGlobalState().findFeatureThatImplements(LegendSDLCFeature.class).findAny().orElseThrow(() -> new RuntimeException("Could not find feature to convert json to pure files"));
        }

        return this.sdlcHandler;
    }

    public CompletableFuture<Void> writeEntity(LegendWriteEntityRequest request)
    {
        CompletableFuture<ApplyWorkspaceEditParams> editElementFuture = this.entities(new LegendEntitiesRequest())
                .thenApply(entities ->
                {
                    LegendSDLCFeature handler = this.getSDLCHandler();

                    Map.Entry<String, String> pathToText = handler.contentToPureText(request.getContent());

                    String path = pathToText.getKey();
                    String pureText = pathToText.getValue();

                    LegendEntity entity = entities.stream().filter(x -> x.getPath().equals(path)).findAny().orElseThrow(() -> new RuntimeException("Element not found in project: " + path));
                    TextLocation location = entity.getLocation();

                    LegendServerGlobalState.LegendServerDocumentState documentState = this.server.getGlobalState().getDocumentState(location.getDocumentId());

                    VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier(location.getDocumentId(), documentState.getVersion());
                    TextEdit addPureContent = new TextEdit(
                            LegendToLSPUtilities.toRange(location.getTextInterval()),
                            pureText
                    );

                    Either<TextDocumentEdit, ResourceOperation> edit = Either.forLeft(new TextDocumentEdit(textDocument, List.of(addPureContent)));
                    WorkspaceEdit workspaceEdit = new WorkspaceEdit(List.of(edit));
                    return new ApplyWorkspaceEditParams(workspaceEdit, "Edit element: " + path);
                });

        return this.handleEdits(editElementFuture);
    }

    private CompletableFuture<Void> handleEdits(CompletableFuture<ApplyWorkspaceEditParams> paramsCompletableFuture)
    {
        return paramsCompletableFuture.thenCompose(workspaceEditParams ->
                {
                    CompletableFuture<ApplyWorkspaceEditResponse> responseFuture;

                    if (workspaceEditParams.getEdit().getDocumentChanges().isEmpty())
                    {
                        responseFuture = CompletableFuture.completedFuture(new ApplyWorkspaceEditResponse(true));
                    }
                    else
                    {
                        responseFuture = this.server.getLanguageClient().applyEdit(workspaceEditParams);
                    }

                    return responseFuture.thenAccept(response ->
                    {
                        if (response.isApplied())
                        {
                            this.server.showInfoToClient(workspaceEditParams.getLabel() + " completed successfully");
                        }
                        else
                        {
                            this.server.showErrorToClient(workspaceEditParams.getLabel() + " failed: " + response.getFailureReason());
                        }
                    });

                })
                .exceptionally(e ->
                {
                    LOGGER.error("Error while applying edits", e);
                    this.server.showErrorToClient("Error while applying edits failed: " + e.getMessage());
                    return null;
                });
    }
}
