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

import org.eclipse.lsp4j.CompletionOptions;
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
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarLibrary;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPInlineDSLExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPInlineDSLLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@link LanguageServer} implementation for Legend.
 */
public class LegendLanguageServer implements LanguageServer, LanguageClientAware
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendLanguageServer.class);

    private static final int UNINITIALIZED = 0;
    private static final int INITIALIZING = 1;
    private static final int INITIALIZED = 2;
    private static final int SHUTTING_DOWN = 3;
    private static final int SHUT_DOWN = 4;

    private final LegendTextDocumentService textDocumentService;
    private final LegendWorkspaceService workspaceService;
    private final AtomicReference<LanguageClient> languageClient = new AtomicReference<>(null);
    private final AtomicInteger state = new AtomicInteger(UNINITIALIZED);
    private final boolean async;
    private final Executor executor;
    private final LegendLSPGrammarLibrary grammars;
    private final LegendLSPInlineDSLLibrary inlineDSLs;
    private final LegendServerGlobalState globalState = new LegendServerGlobalState(this);

    private final Set<String> rootFolders = new HashSet<>();

    private LegendLanguageServer(boolean async, Executor executor, LegendLSPGrammarLibrary grammars, LegendLSPInlineDSLLibrary inlineDSLs)
    {
        this.textDocumentService = new LegendTextDocumentService(this);
        this.workspaceService = new LegendWorkspaceService(this);
        this.async = async;
        this.executor = executor;
        this.grammars = grammars;
        this.inlineDSLs = inlineDSLs;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams initializeParams)
    {
        LOGGER.info("Initialize server requested: {}", initializeParams);
        int currentState = this.state.get();
        if (currentState >= INITIALIZING)
        {
            String message = getCannotInitializeMessage(currentState);
            LOGGER.error(message);
            throw newResponseErrorException(ResponseErrorCode.RequestFailed, message);
        }
        return supplyPossiblyAsync_internal(() -> doInitialize(initializeParams));
    }

    @Override
    public void initialized(InitializedParams params)
    {
        checkReady();
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
        return supplyPossiblyAsync_internal(() ->
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
    public TextDocumentService getTextDocumentService()
    {
        return this.textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService()
    {
        return this.workspaceService;
    }

    @Override
    public void connect(LanguageClient languageClient)
    {
        Objects.requireNonNull(languageClient, "language client may not be null");
        checkNotShutDown();
        LOGGER.info("Connecting language client");
        if (!this.languageClient.compareAndSet(null, languageClient))
        {
            if (languageClient == this.languageClient.get())
            {
                LOGGER.warn("Language client is already connected");
            }
            else
            {
                String message = "Already connected to a language client";
                LOGGER.error(message);
                throw newResponseErrorException(ResponseErrorCode.RequestFailed, message);
            }
        }

        if (this.async)
        {
            runAsync(() -> trySetWorkspaceFoldersFromClient(5L, TimeUnit.SECONDS));
        }
        else
        {
            trySetWorkspaceFoldersFromClient(1L, TimeUnit.SECONDS);
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

    <T> CompletableFuture<T> supplyPossiblyAsync(Supplier<T> supplier)
    {
        checkReady();
        return supplyPossiblyAsync_internal(supplier);
    }

    CompletableFuture<?> runPossiblyAsync(Runnable runnable)
    {
        checkReady();
        return runPossiblyAsync_internal(runnable);
    }

    LegendServerGlobalState getGlobalState()
    {
        checkReady();
        return this.globalState;
    }

    LanguageClient getLanguageClient()
    {
        checkNotShutDown();
        return this.languageClient.get();
    }

    LegendLSPGrammarLibrary getGrammarLibrary()
    {
        checkNotShutDown();
        return this.grammars;
    }

    LegendLSPGrammarExtension getGrammarExtension(String grammar)
    {
        checkNotShutDown();
        return this.grammars.getExtension(grammar);
    }

    LegendLSPInlineDSLLibrary getInlineDSLLibrary()
    {
        checkNotShutDown();
        return this.inlineDSLs;
    }

    private boolean trySetWorkspaceFoldersFromClient(long timeout, TimeUnit unit)
    {
        LanguageClient client = this.languageClient.get();
        if (client == null)
        {
            return false;
        }

        List<WorkspaceFolder> folders;
        try
        {
            folders = client.workspaceFolders().get(timeout, unit);
        }
        catch (ExecutionException e)
        {
            LOGGER.error("Error getting workspace folders from the client", e.getCause());
            return false;
        }
        catch (InterruptedException e)
        {
            LOGGER.warn("Interrupted while waiting for workspace folders from the client", e);
            return false;
        }
        catch (TimeoutException e)
        {
            LOGGER.warn("Timed out waiting for workspace folders from the client", e);
            return false;
        }
        catch (UnsupportedOperationException e)
        {
            String message = e.getMessage();
            if (message != null)
            {
                LOGGER.warn("Client does not support getting workspace folders: {}", message);
            }
            else
            {
                LOGGER.warn("Client does not support getting workspace folders");
            }
            return false;
        }
        catch (Exception e)
        {
            LOGGER.error("Error getting workspace folders from the client", e);
            return false;
        }
        setWorkspaceFolders(folders);
        return true;
    }

    void setWorkspaceFolders(Iterable<? extends WorkspaceFolder> folders)
    {
        List<String> addedFolders;
        List<String> removedFolders;
        synchronized (this.rootFolders)
        {
            Set<String> newRootFolders = new HashSet<>();
            folders.forEach(ws -> newRootFolders.add(ws.getUri()));

            addedFolders = newRootFolders.stream().filter(f -> !this.rootFolders.contains(f)).collect(Collectors.toList());
            removedFolders = this.rootFolders.stream().filter(f -> !newRootFolders.contains(f)).collect(Collectors.toList());

            this.rootFolders.clear();
            this.rootFolders.addAll(newRootFolders);
        }

        updateStateWithFolderChanges(addedFolders, removedFolders);
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
        boolean added;
        synchronized (this.rootFolders)
        {
            added = this.rootFolders.add(folderUri);
        }
        if (added)
        {
            LOGGER.info("Added root folder: {}", folderUri);
            runPossiblyAsync_internal(() -> this.globalState.addFolder(folderUri));
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

    private void updateStateWithFolderChanges(List<String> addedFolders, List<String> removedFolders)
    {
        if ((addedFolders != null) && !addedFolders.isEmpty())
        {
            LOGGER.info("Added root folders: {}", addedFolders);
        }
        if ((removedFolders != null) && !removedFolders.isEmpty())
        {
            LOGGER.info("Removed root folders: {}", removedFolders);
        }
        runPossiblyAsync_internal(() ->
        {
           if (addedFolders != null)
           {
               addedFolders.forEach(this.globalState::addFolder);
           }
           if (removedFolders != null)
           {
               removedFolders.forEach(this.globalState::removeFolder);
           }
        });
    }

    boolean forEachRootFolder(Consumer<? super String> consumer)
    {
        synchronized (this.rootFolders)
        {
            if (this.rootFolders.isEmpty())
            {
                boolean setFromClient = trySetWorkspaceFoldersFromClient(500, TimeUnit.MILLISECONDS);
                if (!setFromClient)
                {
                    LOGGER.warn("Could not get workspace folders from client");
                    return false;
                }
            }
            this.rootFolders.forEach(consumer);
            return true;
        }
    }

    void logToClient(String message)
    {
        logToClient(MessageType.Log, message);
    }

    void logInfoToClient(String message)
    {
        logToClient(MessageType.Info, message);
    }

    void logWarningToClient(String message)
    {
        logToClient(MessageType.Warning, message);
    }

    void logErrorToClient(String message)
    {
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

    private <T> CompletableFuture<T> supplyPossiblyAsync_internal(Supplier<T> supplier)
    {
        return this.async ?
                supplyAsync(supplier) :
                CompletableFuture.completedFuture(supplier.get());
    }

    private CompletableFuture<?> runPossiblyAsync_internal(Runnable runnable)
    {
        if (this.async)
        {
            return runAsync(runnable);
        }

        runnable.run();
        return CompletableFuture.completedFuture(null);
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier)
    {
        return (this.executor == null) ?
                CompletableFuture.supplyAsync(supplier) :
                CompletableFuture.supplyAsync(supplier, this.executor);
    }

    private CompletableFuture<?> runAsync(Runnable runnable)
    {
        return (this.executor == null) ?
                CompletableFuture.runAsync(runnable) :
                CompletableFuture.runAsync(runnable, this.executor);
    }

    private InitializeResult doInitialize(InitializeParams initializeParams)
    {
        LOGGER.info("Initializing server");
        if (!this.state.compareAndSet(UNINITIALIZED, INITIALIZING))
        {
            String message = getCannotInitializeMessage(this.state.get());
            LOGGER.warn(message);
            throw newResponseErrorException(ResponseErrorCode.RequestFailed, message);
        }

        logToClient("Initializing server");
        List<WorkspaceFolder> workspaceFolders = initializeParams.getWorkspaceFolders();
        if (workspaceFolders != null)
        {
            setWorkspaceFolders(workspaceFolders);
        }
        InitializeResult result = new InitializeResult(getServerCapabilities());
        if (!this.state.compareAndSet(INITIALIZING, INITIALIZED))
        {
            int currentState = this.state.get();
            String message;
            switch (currentState)
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
                    message = "Server entered unexpected state during initialization: " + getStateDescription(currentState);
                }
            }
            LOGGER.warn(message);
            throw newResponseErrorException(ResponseErrorCode.RequestFailed, message);
        }
        LOGGER.info("Server initialized");
        logToClient("Server initialized");
        return result;
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
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        capabilities.setSemanticTokensProvider(new SemanticTokensWithRegistrationOptions(new SemanticTokensLegend(Collections.singletonList(SemanticTokenTypes.Keyword), Collections.emptyList()), false, true));
        capabilities.setWorkspace(getWorkspaceServerCapabilities());
        capabilities.setCompletionProvider(new CompletionOptions(true, List.of()));
        return capabilities;
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

    private void doShutdown()
    {
        LOGGER.info("Starting shut down process");
        int currentState;
        while ((currentState = this.state.get()) < SHUTTING_DOWN)
        {
            if (this.state.compareAndSet(currentState, SHUTTING_DOWN))
            {
                LOGGER.info("Shutting down from state: {}", getStateDescription(currentState));
                logInfoToClient("Server shutting down");
                this.state.set(SHUT_DOWN);
                LOGGER.info("Server shut down");
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

    private ResponseErrorException newResponseErrorException(ResponseErrorCode code, String message)
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

    /**
     * Builder for {@link LegendLanguageServer}
     */
    public static class Builder
    {
        private boolean async = true;
        private Executor executor;
        private final LegendLSPGrammarLibrary.Builder grammars = LegendLSPGrammarLibrary.builder();
        private final LegendLSPInlineDSLLibrary.Builder inlineDSLs = LegendLSPInlineDSLLibrary.builder();

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
         * Add the inline DSL extension to the builder.
         *
         * @param inlineDSL inline DSL extension to add
         * @return this builder
         * @see LegendLSPInlineDSLLibrary.Builder#addExtension
         */
        public Builder withInlineDSL(LegendLSPInlineDSLExtension inlineDSL)
        {
            this.inlineDSLs.addExtension(inlineDSL);
            return this;
        }

        /**
         * Add all the given inline DSL extensions to the builder.
         *
         * @param inlineDSLs inline DSL extensions to add
         * @return this builder
         * @see LegendLSPInlineDSLLibrary.Builder#addExtensions
         */
        public Builder withInlineDSLs(LegendLSPInlineDSLExtension... inlineDSLs)
        {
            this.inlineDSLs.addExtensions(inlineDSLs);
            return this;
        }

        /**
         * Add all the given inline DSL extensions to the builder.
         *
         * @param inlineDSLs inline DSL extensions to add
         * @return this builder
         * @see LegendLSPInlineDSLLibrary.Builder#addExtensions
         */
        public Builder withInlineDSLs(Iterable<? extends LegendLSPInlineDSLExtension> inlineDSLs)
        {
            this.inlineDSLs.addExtensions(inlineDSLs);
            return this;
        }

        /**
         * Builder the Legend language server.
         *
         * @return server
         */
        public LegendLanguageServer build()
        {
            return new LegendLanguageServer(this.async, this.executor, this.grammars.build(), this.inlineDSLs.build());
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
                .withGrammars(ServiceLoader.load(LegendLSPGrammarExtension.class))
                .withInlineDSLs(ServiceLoader.load(LegendLSPInlineDSLExtension.class))
                .build();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
        LOGGER.debug("Server launched");
    }
}
