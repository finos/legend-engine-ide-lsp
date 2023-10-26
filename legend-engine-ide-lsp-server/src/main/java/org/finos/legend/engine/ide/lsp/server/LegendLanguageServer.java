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
import org.finos.legend.engine.ide.lsp.extension.DefaultExtensionProvider;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarLibrary;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPInlineDSLExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPInlineDSLLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
        LOGGER.info("Initialize server requested");
        int currentState = this.state.get();
        if (currentState >= INITIALIZING)
        {
            String message = getCannotInitializeMessage(currentState);
            LOGGER.error(message);
            throw newResponseErrorException(ResponseErrorCode.RequestFailed, message);
        }
        return supplyPossiblyAsync_internal(this::doInitialize);
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
        checkNotShutDown();
        return this.textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService()
    {
        checkNotShutDown();
        return this.workspaceService;
    }

    @Override
    public void connect(LanguageClient languageClient)
    {
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

    LegendLSPInlineDSLLibrary getInlineDSLLibrary()
    {
        checkNotShutDown();
        return this.inlineDSLs;
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
        if (!this.async)
        {
            return CompletableFuture.completedFuture(supplier.get());
        }
        if (this.executor == null)
        {
            return CompletableFuture.supplyAsync(supplier);
        }
        return CompletableFuture.supplyAsync(supplier, this.executor);
    }

    private InitializeResult doInitialize()
    {
        LOGGER.info("Initializing server");
        if (!this.state.compareAndSet(UNINITIALIZED, INITIALIZING))
        {
            String message = getCannotInitializeMessage(this.state.get());
            LOGGER.warn(message);
            throw newResponseErrorException(ResponseErrorCode.RequestFailed, message);
        }

        logToClient("Initializing server");
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
                    message = "Server entered unexpected state during initialization: {}" + getStateDescription(currentState);
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
        return capabilities;
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

    public static void main(String[] args) throws Exception
    {
        List<LegendLSPGrammarExtension> defaultExtensions = DefaultExtensionProvider.getDefaultExtensions();
        LegendLanguageServer server = LegendLanguageServer.builder().withGrammars(defaultExtensions).build();
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
    }
}
