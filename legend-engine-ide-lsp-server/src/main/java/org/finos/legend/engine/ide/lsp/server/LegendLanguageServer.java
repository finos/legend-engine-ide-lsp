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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationPatternKind;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.NotebookDocumentSyncRegistrationOptions;
import org.eclipse.lsp4j.NotebookSelector;
import org.eclipse.lsp4j.NotebookSelectorCell;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.NotebookDocumentService;
import org.finos.legend.engine.ide.lsp.classpath.ClasspathFactory;
import org.finos.legend.engine.ide.lsp.classpath.ClasspathUsingMavenFactory;
import org.finos.legend.engine.ide.lsp.classpath.EmbeddedClasspathFactory;
import org.finos.legend.engine.ide.lsp.extension.Constants;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPFeature;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarLibrary;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.features.LegendUsageEventConsumer;
import org.finos.legend.engine.ide.lsp.extension.state.CancellationToken;
import org.finos.legend.engine.ide.lsp.server.request.LegendJsonToPureRequest;
import org.finos.legend.engine.ide.lsp.server.service.LegendLanguageServiceContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link LanguageServer} implementation for Legend.
 */
public class LegendLanguageServer implements LegendLanguageServerContract
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendLanguageServer.class);
    private static final Logger LOGGER_CLIENT = LoggerFactory.getLogger(LegendLanguageServer.class.getName() + ".client");

    public static final String LEGEND_CLIENT_COMMAND_ID = "legend.client.command";

    private static final int UNINITIALIZED = 0;
    private static final int INITIALIZING = 1;
    private static final int INITIALIZED = 2;
    private static final int SHUTTING_DOWN = 3;
    private static final int SHUT_DOWN = 4;

    private static final Properties VERSIONS = loadVersions();
    private final CountDownLatch postInitializationLatch = new CountDownLatch(1);
    private final LegendTextDocumentService textDocumentService;
    private final LegendWorkspaceService workspaceService;
    private final LegendNotebookDocumentService notebookService;
    private final LegendLanguageService legendLanguageService;
    private final AtomicReference<LanguageClient> languageClient = new AtomicReference<>(null);
    private final AtomicInteger state = new AtomicInteger(UNINITIALIZED);
    private final ClasspathFactory classpathFactory;
    private final ExtensionsGuard extensionGuard;
    private final Executor executor;
    private final boolean async;
    private final LegendServerGlobalState globalState = new LegendServerGlobalState(this);
    private final AtomicInteger progressId = new AtomicInteger();
    private final Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();
    final Set<String> rootFolders = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, String> settings = new ConcurrentHashMap<>();

    private LegendLanguageServer(boolean async, Executor executor, ClasspathFactory classpathFactory, LegendLSPGrammarLibrary grammars, Collection<LegendLSPFeature> features)
    {
        this.textDocumentService = new LegendTextDocumentService(this);
        this.workspaceService = new LegendWorkspaceService(this);
        this.notebookService = new LegendNotebookDocumentService(this);
        this.legendLanguageService = new LegendLanguageService(this);
        this.extensionGuard = new ExtensionsGuard(this, grammars, features);
        this.async = async;
        this.executor = executor;
        this.classpathFactory = Objects.requireNonNull(classpathFactory, "missing classpath factory");
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams initializeParams)
    {
        LOGGER.debug("Initialize server requested: {}", initializeParams);
        if (this.state.get() >= INITIALIZING)
        {
            String message = getCannotInitializeMessage(this.state.get());
            LOGGER.error(message);
            throw newResponseErrorException(ResponseErrorCode.RequestFailed, message);
        }

        LOGGER.info("Initializing server (versions {})", VERSIONS);
        LOGGER.debug("Initialize params: {}", initializeParams);
        if (!this.state.compareAndSet(UNINITIALIZED, INITIALIZING))
        {
            String message = getCannotInitializeMessage(this.state.get());
            LOGGER.warn(message);
            logWarningToClient(message);
            throw newResponseErrorException(ResponseErrorCode.RequestFailed, message);
        }

        logInfoToClient("Initializing server");
        List<WorkspaceFolder> workspaceFolders = initializeParams.getWorkspaceFolders();

        InitializeResult result = new InitializeResult(getServerCapabilities(), new ServerInfo("Legend Language Server", getProjectVersion()));
        CompletableFuture<InitializeResult> completableFuture = CompletableFuture.completedFuture(result);
        CompletableFuture<InitializeResult> initFuture = completableFuture;

        if (workspaceFolders != null)
        {
            initFuture = setWorkspaceFolders(workspaceFolders).thenComposeAsync(x -> completableFuture);
        }

        if (!this.state.compareAndSet(INITIALIZING, INITIALIZED))
        {
            String message;
            switch (this.state.get())
            {
                case SHUTTING_DOWN:
                {
                    message = "Server began shutting down during initialization";
                    break;
                }
                case SHUT_DOWN:
                {
                    message = "Server shut down during initialization";
                    break;
                }
                default:
                {
                    message = "Server entered unexpected state during initialization: " + getStateDescription(this.state.get());
                }
            }
            LOGGER.warn(message);
            logWarningToClient(message);
            throw newResponseErrorException(ResponseErrorCode.RequestFailed, message);
        }
        LOGGER.info("Server initialized");
        logInfoToClient("Server initialized");
        return initFuture;
    }

    public <T> T runAndFireEvent(String eventType, Supplier<T> action)
    {
        return runAndFireEvent(eventType, action, Map.of());
    }

    public <T> T runAndFireEvent(String eventType, Supplier<T> action, Map<String, Object> metadata)
    {
        Instant start = Instant.now();
        Exception exception = null;
        try
        {
            return action.get();
        }
        catch (Exception e)
        {
            exception = e;
            throw e;
        }
        finally
        {
            this.fireEvent(eventType, start, metadata, exception);
        }
    }

    public void fireEvent(String eventType, Instant start, Map<String, Object> metadata, Throwable throwable)
    {
        JsonElement metadataAsTree = this.gson.toJsonTree(metadata);
        Map<String, Object> finalMetadata = (Map<String, Object>) this.gson.fromJson(metadataAsTree, TypeToken.getParameterized(Map.class, String.class, Object.class));
        if (throwable != null)
        {
            finalMetadata.put("error", true);
            finalMetadata.put("errorMessage", throwable.getMessage());
        }

        LegendUsageEventConsumer.LegendUsageEvent event = LegendUsageEventConsumer.event(eventType, start, Instant.now(), finalMetadata);

        this.globalState.findFeatureThatImplements(LegendUsageEventConsumer.class).forEach(x ->
        {
            try
            {
                x.consume(event);
            }
            catch (Exception e)
            {
                LOGGER.error("Failed to dispatch consume on {}", x.description(), e);
            }
        });
    }

    public String getProjectVersion()
    {
        return VERSIONS.getProperty("project.version", "-1");
    }

    public CountDownLatch getPostInitializationLatch()
    {
        return this.postInitializationLatch;
    }

    @Override
    public void initialized(InitializedParams params)
    {
        checkReady();
        this.initializeSettings()
                .thenRun(this::initializeEngineServerUrl)
                .thenCompose(_void -> this.initializeExtensions())
                .thenRun(() -> this.logInfoToClient("Extension finished post-initialization"));
    }

    private CompletableFuture<Void> initializeSettings()
    {
        String section = "legend";

        ConfigurationItem legend = new ConfigurationItem();
        legend.setSection(section);

        ConfigurationParams configurationParams = new ConfigurationParams(List.of(legend));
        return this.getLanguageClient().configuration(configurationParams).thenAccept(x ->
        {
            this.flattenObject(section, x.get(0), this.settings);
        });
    }

    private void flattenObject(String current, Object x, Map<String, String> result)
    {
        if (x instanceof JsonPrimitive)
        {
            String value = this.extractValueAs(x, String.class);
            if (value != null && !value.isBlank())
            {
                result.put(current, value);
            }
        }
        else if (x instanceof JsonObject)
        {
            JsonObject object = (JsonObject) x;
            object.keySet().forEach(k -> this.flattenObject(current + "." + k, object.get(k), result));
        }
    }

    private void initializeEngineServerUrl()
    {
        String url = getSetting(Constants.LEGEND_ENGINE_SERVER_CONFIG_PATH);
        if (url != null && !url.isEmpty())
        {
            this.logInfoToClient("Using server URL: " + url);
            System.setProperty("legend.engine.server.url", url);
        }
        else
        {
            this.logWarningToClient("No server URL found.  Some functionality won't work");
            System.clearProperty("legend.engine.server.url");
        }
    }

    private CompletableFuture<Void> initializeExtensions()
    {
        Instant start = Instant.now();
        logInfoToClient("Initializing extensions");

        this.classpathFactory.initialize(this);

        return this.classpathFactory.create(Collections.unmodifiableSet(this.rootFolders))
                .thenAccept(this.extensionGuard::initialize)
                // trigger parsing/compilation/execution
                .thenRun(this.extensionGuard.wrapOnClasspath(() -> this.globalState.getAvailableGrammarExtensions().forEach(x -> x.startup(this.globalState))))
                .thenRun(this.extensionGuard.wrapOnClasspath(this.legendLanguageService::loadVirtualFileSystemContent))
                .thenRun(this.extensionGuard.wrapOnClasspath(this::reprocessDocuments))
                .thenRun(this.extensionGuard.wrapOnClasspath(() -> this.globalState.forEachDocumentState(this.textDocumentService::getLegendDiagnostics)))
                // tell client to refresh base on diagnostics discovered
                .thenRun(() ->
                {
                    LanguageClient languageClient = this.getLanguageClient();
                    languageClient.refreshCodeLenses();
                    languageClient.refreshDiagnostics();
                    languageClient.refreshInlayHints();
                    languageClient.refreshInlineValues();
                    languageClient.refreshSemanticTokens();
                })
                .thenCompose(x -> this.applyWorkspaceEdits())
                .whenComplete((v, t) ->
                {
                    this.fireEvent("initialize", start, Collections.emptyMap(), t);
                    this.postInitializationLatch.countDown();
                })
                .exceptionally(x ->
                {
                    LOGGER.error("Failed during post-initialization", x);
                    logErrorToClient("Failed during post-initialization: " + x.getMessage());
                    this.postInitializationLatch.countDown();
                    return null;
                });
    }

    private CompletableFuture<Void> applyWorkspaceEdits()
    {
        return CompletableFuture.allOf(
                this.convertingJsonEntitiesToPureFiles()
        );
    }

    private CompletableFuture<Void> convertingJsonEntitiesToPureFiles()
    {
        List<String> jsonUris = new ArrayList<>();

        for (String rootFolder : this.rootFolders)
        {
            Path rootPath = Paths.get(URI.create(rootFolder));
            try (Stream<Path> stream = Files.find(rootPath, Integer.MAX_VALUE,
                    (path, attr) -> attr.isRegularFile() && path.getFileName().toString().endsWith(".json"),
                    FileVisitOption.FOLLOW_LINKS))
            {
                stream.map(Path::toUri).map(URI::toString).forEach(jsonUris::add);
            }
            catch (IOException e)
            {
                LOGGER.error("Failed to scan for json files", e);
            }
        }

        if (jsonUris.isEmpty())
        {
            return CompletableFuture.completedFuture(null);
        }
        else
        {
            return this.legendLanguageService.jsonEntitiesToPureTextWorkspaceEdits(new LegendJsonToPureRequest(jsonUris))
                    .thenAccept(x ->
                            {
                                if (x.isApplied())
                                {
                                    LOGGER.info("Applied suggested json to pure edits");
                                }
                                else
                                {
                                    LOGGER.warn("Failed to apply suggested json to pure edits: {}", x);
                                }
                            }
                    );
        }
    }

    public ForkJoinPool getForkJoinPool()
    {
        return this.extensionGuard.getForkJoinPool();
    }

    public String getSetting(String settingKey)
    {
        return this.settings.get(settingKey);
    }

    private void reprocessDocuments()
    {
        this.globalState.forEachDocumentState(x -> ((LegendServerGlobalState.LegendServerDocumentState) x).recreateSectionStates());
        this.globalState.clearProperties();
    }

    @Override
    public CompletableFuture<Object> shutdown()
    {
        LOGGER.info("Shutdown requested");
        int currentState = this.state.get();
        if (currentState >= SHUTTING_DOWN)
        {
            LOGGER.warn("Server already {}", getStateDescription(currentState));
            return CompletableFuture.completedFuture(null);
        }
        return this.supplyPossiblyAsync_internal(() ->
        {
            doShutdown();
            return null;
        });
    }

    @Override
    public void exit()
    {
        LOGGER.info("Server exiting");
        System.exit(0);
    }

    @Override
    public LegendTextDocumentService getTextDocumentService()
    {
        return this.textDocumentService;
    }

    @Override
    public LegendWorkspaceService getWorkspaceService()
    {
        return this.workspaceService;
    }

    @Override
    public NotebookDocumentService getNotebookDocumentService()
    {
        return this.notebookService;
    }

    @Override
    public LegendLanguageServiceContract getLegendLanguageService()
    {
        return this.legendLanguageService;
    }

    @Override
    public void connect(LanguageClient languageClient)
    {
        checkNotShutDown();
        LOGGER.info("Connecting language client");
        if (this.languageClient.compareAndSet(null, languageClient))
        {
            logInfoToClient("Language client connected to server");
        }
        else
        {
            if (languageClient == this.languageClient.get())
            {
                LOGGER.warn("Language client is already connected");
            }
            else
            {
                String message = "Already connected to a language client";
                LOGGER.error(message);
                if (languageClient != null)
                {
                    languageClient.logMessage(new MessageParams(MessageType.Warning, "Cannot connect client to server: server already connected to a different client"));
                }
                throw newResponseErrorException(ResponseErrorCode.RequestFailed, message);
            }
        }
    }

    /**
     * Whether the server is uninitialized. This is true from the time the server starts, until initialization begins.
     * During this time, most ordinary calls to the server will throw a {@link ResponseErrorException} with the error
     * code {@link ResponseErrorCode#ServerNotInitialized}. The only calls that can be made in this state are to
     * initialize or shut down the server or to check the server state.
     *
     * @return whether the server is uninitialized
     */
    public boolean isUninitialized()
    {
        return this.state.get() == UNINITIALIZED;
    }

    /**
     * Whether the server is initialized. This is true from the time that server initialization completes until server
     * shut down begins. If true, the server is ready for general use. Note, however, that once the server is
     * initialized, it is an error to initialize it again.
     *
     * @return whether the server is initialized
     */
    public boolean isInitialized()
    {
        return this.state.get() == INITIALIZED;
    }

    /**
     * Whether the server has shut down. This is true from the time that server shut down completes. Once a server has
     * shut down, no more can be done with it.
     *
     * @return whether the server has shut down
     */
    public boolean isShutDown()
    {
        return this.state.get() == SHUT_DOWN;
    }

    void checkReady()
    {
        int currentState = this.state.get();
        switch (currentState)
        {
            case INITIALIZED:
            {
                // Server is ready
                return;
            }
            case UNINITIALIZED:
            case INITIALIZING:
            {
                throw newResponseErrorException(ResponseErrorCode.ServerNotInitialized, "Server is not initialized");
            }
            case SHUTTING_DOWN:
            {
                throw newResponseErrorException(ResponseErrorCode.RequestFailed, "Server is shutting down");
            }
            case SHUT_DOWN:
            {
                throw newResponseErrorException(ResponseErrorCode.RequestFailed, "Server has shut down");
            }
            default:
            {
                String message = "Unexpected server state: " + getStateDescription(currentState);
                LOGGER.warn(message);
                throw newResponseErrorException(ResponseErrorCode.InternalError, message);
            }
        }
    }

    void checkNotShutDown()
    {
        int currentState = this.state.get();
        switch (currentState)
        {
            case SHUTTING_DOWN:
            {
                throw newResponseErrorException(ResponseErrorCode.RequestFailed, "Server is shutting down");
            }
            case SHUT_DOWN:
            {
                throw newResponseErrorException(ResponseErrorCode.RequestFailed, "Server has shut down");
            }
            default:
            {
                // not a shut-down state
            }
        }
    }

    public <T> CompletableFuture<T> supplyPossiblyAsync(Supplier<T> supplier)
    {
        checkReady();
        return this.supplyPossiblyAsync_internal(this.extensionGuard.wrapOnClasspath(supplier));
    }

    void runPossiblyAsync(Runnable runnable)
    {
        checkReady();
        this.runPossiblyAsync_internal(this.extensionGuard.wrapOnClasspath(runnable));
    }

    private CompletableFuture<?> runPossiblyAsync_internal(Runnable work)
    {
        return this.supplyPossiblyAsync_internal(() ->
                {
                    work.run();
                    return null;
                }
        );
    }

    private <T> CompletableFuture<T> supplyPossiblyAsync_internal(Supplier<T> work)
    {
        if (this.async)
        {
            return (this.executor == null) ?
                    CompletableFuture.supplyAsync(work) :
                    CompletableFuture.supplyAsync(work, this.executor);
        }

        return CompletableFuture.completedFuture(work.get());
    }

    public LegendServerGlobalState getGlobalState()
    {
        checkReady();
        return this.globalState;
    }

    public LanguageClient getLanguageClient()
    {
        checkNotShutDown();
        return this.languageClient.get();
    }

    LegendLSPGrammarLibrary getGrammarLibrary()
    {
        checkNotShutDown();
        return this.extensionGuard.getGrammars();
    }

    Collection<LegendLSPFeature> getFeatures()
    {
        checkNotShutDown();
        return this.extensionGuard.getFeatures();
    }

    LegendLSPGrammarExtension getGrammarExtension(String grammar)
    {
        checkNotShutDown();
        return this.extensionGuard.getGrammars().getExtension(grammar);
    }

    CompletableFuture<?> setWorkspaceFolders(Iterable<? extends WorkspaceFolder> folders)
    {
        List<String> addedFolders;
        List<String> removedFolders;
        synchronized (this.rootFolders)
        {
            Set<String> newRootFolders = new HashSet<>();
            folders.forEach(ws -> newRootFolders.add(ws.getUri()));
            logInfoToClient("Setting workspace folders: " + newRootFolders);

            addedFolders = newRootFolders.stream().filter(f -> !this.rootFolders.contains(f)).collect(Collectors.toList());
            removedFolders = this.rootFolders.stream().filter(f -> !newRootFolders.contains(f)).collect(Collectors.toList());

            this.rootFolders.clear();
            this.rootFolders.addAll(newRootFolders);
        }

        return updateStateWithFolderChanges(addedFolders, removedFolders);
    }

    void addRootFolderFromFile(String fileUri)
    {
        // TODO find a more principled way to find the root
        String srcMainPure = "/src/main/pure/";
        int index = fileUri.indexOf(srcMainPure);
        if (index != -1)
        {
            String folderUri = fileUri.substring(0, index + srcMainPure.length());
            addRootFolder(folderUri);
        }
    }

    void addRootFolder(String folderUri)
    {
        if (this.rootFolders.add(folderUri))
        {
            String message = "Added root folder: " + folderUri;
            LOGGER.info(message);
            logInfoToClient(message);
            this.runPossiblyAsync_internal(() -> this.globalState.addFolder(folderUri));
        }
    }

    void updateWorkspaceFolders(Iterable<? extends WorkspaceFolder> foldersToAdd, Iterable<? extends WorkspaceFolder> foldersToRemove)
    {
        if ((foldersToAdd != null) || (foldersToRemove != null))
        {
            List<String> addedFolders = new ArrayList<>();
            List<String> removedFolders = new ArrayList<>();
            synchronized (this.rootFolders)
            {
                if (foldersToAdd != null)
                {
                    foldersToAdd.forEach(ws ->
                    {
                        if (this.rootFolders.add(ws.getUri()))
                        {
                            addedFolders.add(ws.getUri());
                        }
                    });
                }
                if (foldersToRemove != null)
                {
                    foldersToRemove.forEach(ws ->
                    {
                        if (this.rootFolders.remove(ws.getUri()))
                        {
                            removedFolders.add(ws.getUri());
                        }
                    });
                }
            }
            updateStateWithFolderChanges(addedFolders, removedFolders);
        }
    }

    private CompletableFuture<?> updateStateWithFolderChanges(Collection<String> addedFolders, Collection<String> removedFolders)
    {
        if ((addedFolders != null) && !addedFolders.isEmpty())
        {
            String message = "Added root folders: " + addedFolders;
            logInfoToClient(message);
        }
        if ((removedFolders != null) && !removedFolders.isEmpty())
        {
            String message = "Removed root folders: " + removedFolders;
            logInfoToClient(message);
        }
        return this.runPossiblyAsync_internal(this.extensionGuard.wrapOnClasspath(() ->
        {
            if (addedFolders != null)
            {
                addedFolders.forEach(this.globalState::addFolder);
            }
            if (removedFolders != null)
            {
                removedFolders.forEach(this.globalState::removeFolder);
            }
        }));
    }

    void logToClient(String message)
    {
        LOGGER_CLIENT.debug(message);
        logToClient(MessageType.Log, message);
    }

    public void logInfoToClient(String message)
    {
        LOGGER_CLIENT.info(message);
        logToClient(MessageType.Info, message);
    }

    void logWarningToClient(String message)
    {
        LOGGER_CLIENT.warn(message);
        logToClient(MessageType.Warning, message);
    }

    public void logErrorToClient(String message, Throwable e)
    {
        LOGGER_CLIENT.error(message, e);
        logToClient(MessageType.Error, message + " - " + e.getMessage());
    }

    public void logErrorToClient(String message)
    {
        LOGGER_CLIENT.error(message);
        logToClient(MessageType.Error, message);
    }

    void logToClient(MessageType messageType, String message)
    {
        LanguageClient client = this.languageClient.get();
        if (client != null)
        {
            client.logMessage(new MessageParams(messageType, message));
        }
    }

    void showInfoToClient(String message)
    {
        showToClient(MessageType.Info, message);
    }

    void showWarningToClient(String message)
    {
        showToClient(MessageType.Warning, message);
    }

    void showErrorToClient(String message)
    {
        showToClient(MessageType.Error, message);
    }

    void showToClient(MessageType messageType, String message)
    {
        LanguageClient client = this.languageClient.get();
        if (client != null)
        {
            client.showMessage(new MessageParams(messageType, message));
        }
    }

    Either<String, Integer> newProgressToken()
    {
        return Either.forRight(this.progressId.getAndIncrement());
    }

    public Either<String, Integer> possiblyNewProgressToken(Either<String, Integer> token)
    {
        return (token == null) ? newProgressToken() : token;
    }

    void notifyBegin(Either<String, Integer> token)
    {
        notifyBegin(token, null);
    }

    public void notifyBegin(Either<String, Integer> token, String message)
    {
        notifyBegin(token, message, message);
    }

    void notifyBegin(Either<String, Integer> token, String message, String title)
    {
        WorkDoneProgressBegin begin = new WorkDoneProgressBegin();
        if (message != null)
        {
            begin.setMessage(message);
        }
        if (title != null)
        {
            begin.setTitle(title);
        }
        notifyProgress(token, begin);
    }

    public void notifyProgress(Either<String, Integer> token, String message)
    {
        WorkDoneProgressReport report = new WorkDoneProgressReport();
        if (message != null)
        {
            report.setMessage(message);
        }
        notifyProgress(token, report);
    }

    void notifyResult(Either<String, Integer> token, LegendExecutionResult result)
    {
        notifyResults(token, Collections.singletonList(result));
    }

    void notifyResults(Either<String, Integer> token, Iterable<? extends LegendExecutionResult> results)
    {
        LanguageClient client = this.languageClient.get();
        if (client != null)
        {
            client.notifyProgress(new ProgressParams(token, Either.forRight(results)));
        }
    }

    void notifyEnd(Either<String, Integer> token)
    {
        notifyEnd(token, null);
    }

    void notifyEnd(Either<String, Integer> token, String message)
    {
        WorkDoneProgressEnd end = new WorkDoneProgressEnd();
        if (message != null)
        {
            end.setMessage(message);
        }
        notifyProgress(token, end);
    }

    private void notifyProgress(Either<String, Integer> token, WorkDoneProgressNotification progress)
    {
        LanguageClient client = this.languageClient.get();
        if (client != null)
        {
            client.notifyProgress(new ProgressParams(token, Either.forLeft(progress)));
        }
    }

    public <T> T extractValueAs(Object value, Class<T> cls)
    {
        return extractValueAs(value, TypeToken.get(cls));
    }

    @SuppressWarnings("unchecked")
    public <T> T extractValueAs(Object value, TypeToken<T> cls)
    {
        if (value == null)
        {
            return null;
        }
        if (cls.getRawType().isInstance(value))
        {
            return (T) value;
        }
        if (value instanceof JsonElement)
        {
            return this.gson.fromJson((JsonElement) value, cls);
        }
        if (value instanceof String)
        {
            return this.gson.fromJson((String) value, cls);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> extractValueAsMap(Object value, Class<K> keyType, Class<V> valueType)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Map)
        {
            return (Map<K, V>) value;
        }
        if (value instanceof JsonElement)
        {
            return this.gson.fromJson((JsonElement) value, (TypeToken<Map<K, V>>) TypeToken.getParameterized(Map.class, keyType, valueType));
        }
        if (value instanceof String)
        {
            return this.gson.fromJson((String) value, (TypeToken<Map<K, V>>) TypeToken.getParameterized(Map.class, keyType, valueType));
        }
        return null;
    }

    private String getCannotInitializeMessage(int currentState)
    {
        switch (currentState)
        {
            case INITIALIZING:
            {
                return "Server is currently initializing";
            }
            case INITIALIZED:
            {
                return "Server is already initialized";
            }
            case SHUTTING_DOWN:
            {
                return "Server is shutting down";
            }
            case SHUT_DOWN:
            {
                return "Server has shut down";
            }
            default:
            {
                return "Cannot initialize server in state: " + getStateDescription(currentState);
            }
        }
    }

    private ServerCapabilities getServerCapabilities()
    {
        ServerCapabilities capabilities = new ServerCapabilities();
        // TextDocumentSyncOptions
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental);
        capabilities.setSemanticTokensProvider(new SemanticTokensWithRegistrationOptions(new SemanticTokensLegend(Collections.singletonList(SemanticTokenTypes.Keyword), Collections.emptyList()), false, true));
        capabilities.setWorkspace(getWorkspaceServerCapabilities());
        capabilities.setCompletionProvider(new CompletionOptions(false, List.of()));
        capabilities.setCodeLensProvider(getCodeLensOptions());
        capabilities.setExecuteCommandProvider(getExecuteCommandOptions());
        capabilities.setDefinitionProvider(true);
        capabilities.setReferencesProvider(true);
        capabilities.setDiagnosticProvider(getDiagnosticRegistrationOptions());
        capabilities.setDocumentSymbolProvider(true);
        capabilities.setWorkspaceSymbolProvider(true);
        capabilities.setNotebookDocumentSync(getNotebookDocumentSyncRegistrationOptions());
        return capabilities;
    }

    private NotebookDocumentSyncRegistrationOptions getNotebookDocumentSyncRegistrationOptions()
    {
        NotebookSelector notebookSelector = new NotebookSelector();
        notebookSelector.setNotebook("legend-book");
        notebookSelector.setCells(List.of(new NotebookSelectorCell("legend")));
        return new NotebookDocumentSyncRegistrationOptions(List.of(notebookSelector), true);
    }

    private DiagnosticRegistrationOptions getDiagnosticRegistrationOptions()
    {
        DiagnosticRegistrationOptions diagnosticProvider = new DiagnosticRegistrationOptions();
        diagnosticProvider.setId("Legend");
        diagnosticProvider.setIdentifier("Legend");
        diagnosticProvider.setWorkspaceDiagnostics(true);
        diagnosticProvider.setInterFileDependencies(true);
        return diagnosticProvider;
    }

    private WorkspaceServerCapabilities getWorkspaceServerCapabilities()
    {
        WorkspaceServerCapabilities capabilities = new WorkspaceServerCapabilities();
        capabilities.setWorkspaceFolders(getWorkspaceFolderOptions());
        capabilities.setFileOperations(getFileOperationsServerCapabilities());
        return capabilities;
    }

    private WorkspaceFoldersOptions getWorkspaceFolderOptions()
    {
        WorkspaceFoldersOptions folderOptions = new WorkspaceFoldersOptions();
        folderOptions.setSupported(true);
        folderOptions.setChangeNotifications(true);
        return folderOptions;
    }

    private FileOperationsServerCapabilities getFileOperationsServerCapabilities()
    {
        FileOperationOptions fileOperationOptions = new FileOperationOptions(Collections.singletonList(newFileOperationFilter("**/*.pure")));
        FileOperationsServerCapabilities fileOpsServerCapabilities = new FileOperationsServerCapabilities();
        fileOpsServerCapabilities.setDidCreate(fileOperationOptions);
        fileOpsServerCapabilities.setDidDelete(fileOperationOptions);
        fileOpsServerCapabilities.setDidRename(fileOperationOptions);
        return fileOpsServerCapabilities;
    }

    private FileOperationFilter newFileOperationFilter(String glob)
    {
        return newFileOperationFilter(glob, FileOperationPatternKind.File, "file");
    }

    private FileOperationFilter newFileOperationFilter(String glob, String kind, String scheme)
    {
        FileOperationPattern pattern = new FileOperationPattern(Objects.requireNonNull(glob, "glob is required"));
        if (kind != null)
        {
            pattern.setMatches(kind);
        }
        FileOperationFilter filter = new FileOperationFilter(pattern);
        if (scheme != null)
        {
            filter.setScheme(scheme);
        }
        return filter;
    }

    private CodeLensOptions getCodeLensOptions()
    {
        return new CodeLensOptions();
    }

    private ExecuteCommandOptions getExecuteCommandOptions()
    {
        ExecuteCommandOptions options = new ExecuteCommandOptions(this.workspaceService.getCommandIds());
        options.setWorkDoneProgress(true);
        return options;
    }

    private void doShutdown()
    {
        LOGGER.info("Starting shut down process");
        this.getForkJoinPool().shutdown();
        int currentState;
        while ((currentState = this.state.get()) < SHUTTING_DOWN)
        {
            if (this.state.compareAndSet(currentState, SHUTTING_DOWN))
            {
                LOGGER.info("Shutting down from state: {}", getStateDescription(currentState));
                logInfoToClient("Server shutting down");
                this.state.set(SHUT_DOWN);
                logInfoToClient("Server shut down");
                return;
            }
        }
        if ((currentState == SHUTTING_DOWN) || (currentState == SHUT_DOWN))
        {
            LOGGER.info("Server already {}", getStateDescription(currentState));
        }
        else
        {
            LOGGER.warn("Server in unexpected shut down state: {}", getStateDescription(currentState));
        }
    }

    ResponseErrorException newResponseErrorException(ResponseErrorCode code, String message)
    {
        return new ResponseErrorException(new ResponseError(code, message, null));
    }

    private static String getStateDescription(int state)
    {
        switch (state)
        {
            case UNINITIALIZED:
            {
                return "uninitialized";
            }
            case INITIALIZING:
            {
                return "initializing";
            }
            case INITIALIZED:
            {
                return "initialized";
            }
            case SHUTTING_DOWN:
            {
                return "shutting down";
            }
            case SHUT_DOWN:
            {
                return "shut down";
            }
            default:
            {
                return "unknown";
            }
        }
    }

    /**
     * Get a {@link LegendLanguageServer} builder.
     *
     * @return builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    <T> CompletableFuture<T> completableFutureWithCancelSupport(CompletableFuture<T> completableFuture, CancellationToken token)
    {
        CompletableFuture<T> withCancelSupport = new CompletableFuture<>()
        {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning)
            {
                LOGGER.debug("Cancelling request: {}", token.getId());
                token.cancel();
                return completableFuture.cancel(mayInterruptIfRunning);
            }
        };

        completableFuture.whenComplete((x, e) ->
        {
            token.close();

            // if is cancelled, trigger LSP cancel flow, and avoid noise with other type of errors
            if (e != null && token.isCancelled())
            {
                withCancelSupport.completeExceptionally(new CancellationException());
            }
            else if (e != null)
            {
                withCancelSupport.completeExceptionally(e);
            }
            else
            {
                withCancelSupport.complete(x);
            }
        });

        return withCancelSupport;
    }

    /**
     * Builder for {@link LegendLanguageServer}
     */
    public static class Builder
    {
        private boolean async = true;
        private Executor executor;
        private ClasspathFactory classpathFactory = new EmbeddedClasspathFactory();
        private final LegendLSPGrammarLibrary.Builder grammars = LegendLSPGrammarLibrary.builder();
        private final Collection<LegendLSPFeature> features = new ArrayList<>();

        private Builder()
        {
        }

        /**
         * Set the server to perform operations synchronously.
         *
         * @return this builder
         */
        public Builder synchronous()
        {
            this.async = false;
            this.executor = null;
            return this;
        }

        public Builder classpathFromMaven(File defaultPom)
        {
            this.classpathFactory = new ClasspathUsingMavenFactory(defaultPom);
            return this;
        }

        /**
         * Set the server to perform operations asynchronously.
         *
         * @return this builder
         */
        public Builder asynchronous()
        {
            return asynchronous(null);
        }

        /**
         * Set the server to perform operations asynchronously. If {@code executor} is non-null, then it will be used
         * for asynchronous execution; otherwise a default executor (possibly the common pool) will be used.
         *
         * @param executor optional executor
         * @return this builder
         */
        public Builder asynchronous(Executor executor)
        {
            this.async = true;
            this.executor = executor;
            return this;
        }

        /**
         * Add the grammar extension to the builder.
         *
         * @param grammar grammar extension to add
         * @return this builder
         * @see LegendLSPGrammarLibrary.Builder#addExtension
         */
        public Builder withGrammar(LegendLSPGrammarExtension grammar)
        {
            this.grammars.addExtension(grammar);
            return this;
        }

        public Builder withFeature(LegendLSPFeature feature)
        {
            this.features.add(feature);
            return this;
        }

        /**
         * Add all the given grammar extensions to the builder.
         *
         * @param grammars grammar extensions to add
         * @return this builder
         * @see LegendLSPGrammarLibrary.Builder#addExtensions
         */
        public Builder withGrammars(LegendLSPGrammarExtension... grammars)
        {
            this.grammars.addExtensions(grammars);
            return this;
        }

        /**
         * Add all the given grammar extensions to the builder.
         *
         * @param grammars grammar extensions to add
         * @return this builder
         * @see LegendLSPGrammarLibrary.Builder#addExtensions
         */
        public Builder withGrammars(Iterable<? extends LegendLSPGrammarExtension> grammars)
        {
            this.grammars.addExtensions(grammars);
            return this;
        }

        /**
         * Builder the Legend language server.
         *
         * @return server
         */
        public LegendLanguageServer build()
        {
            return new LegendLanguageServer(this.async, this.executor, this.classpathFactory, this.grammars.build(), this.features);
        }

    }

    /**
     * Launch the server.
     *
     * @param args server arguments
     */
    public static void main(String[] args)
    {
        LOGGER.info("Launching server");
        LegendLanguageServer server = LegendLanguageServer.builder()
                .classpathFromMaven(new File(args[0]))
                .build();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
        LOGGER.debug("Server launched");
    }

    private static Properties loadVersions()
    {
        Properties properties = new Properties();

        try (InputStream stream = LegendLanguageServer.class.getClassLoader().getResourceAsStream("versions.properties"))
        {
            if (stream != null)
            {
                properties.load(stream);
                return properties;
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Unable to load versions", e);
        }
        return properties;
    }

    @Override
    public void setTrace(SetTraceParams params)
    {
        // not supported, but default method throws, and  pollutes the logs, so ignoring the call
    }
}
