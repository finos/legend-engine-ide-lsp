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

package org.finos.legend.engine.ide.lsp.server.integration;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.FileDelete;
import org.eclipse.lsp4j.FullDocumentDiagnosticReport;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.finos.legend.engine.ide.lsp.DummyLanguageClient;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageServer;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageServerContract;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

public class LegendLanguageServerIntegrationExtension implements
        BeforeAllCallback, AfterAllCallback, AfterEachCallback, TestWatcher
{
    /**
     * Rather than blocking indefinitely for an async task to complete, the test cases should wait this
     * amount of seconds.  This will allow to exceptionally finish the test cases when thread deadlocks occur.
     * <pl/>
     * For local development and debugging, this can be increased to prevent false timeouts
     */
    private static final long MAYBE_DEADLOCK_TIMEOUT_SECONDS = 30L;
    /**
     * this phaser is used to track all async task and LSP JRPC messages,
     * allowing the test cases to wait until these have been completed before further assertions
     */
    private Phaser phaser;
    private LSPAsyncTaskTrackingExecutor executorService;
    private DummyLanguageClient client;
    private Future<Void> clientFuture;
    private LegendLanguageServerContract server;
    private Future<Void> serverFuture;
    private Path workspaceFolderPath;

    @Override
    public void afterAll(ExtensionContext context) throws Exception
    {
        clientFuture.cancel(true);
        serverFuture.cancel(true);
        executorService.shutdown();
        Assertions.assertTrue(executorService.awaitTermination(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS), () -> "Not all tasks completed during shutdown: " + executorService);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception
    {
        List<FileDelete> deletes = new ArrayList<>();
        // delete all pure files after each test
        try (Stream<Path> stream = Files.find(workspaceFolderPath, Integer.MAX_VALUE,
                (path, attr) -> attr.isRegularFile() && path.getFileName().toString().endsWith(".pure"),
                FileVisitOption.FOLLOW_LINKS))
        {
            stream.forEach(x ->
            {
                try
                {
                    Files.delete(x);
                    deletes.add(new FileDelete(x.toUri().toString()));
                }
                catch (IOException ignore)
                {
                    // ignore
                }
            });
        }

        server.getWorkspaceService().didDeleteFiles(new DeleteFilesParams(deletes));
        waitForAllTaskToComplete();
        client.clientLog.clear();
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception
    {
        workspaceFolderPath = Files.createTempDirectory("legend-integration");
        client = new DummyLanguageClient();
        // default to one party member, the one arriving and waiting for all tasks and messages
        phaser = new Phaser(1);
        executorService = new LSPAsyncTaskTrackingExecutor(phaser);
        LSPMessageTrackerFactory lspMessageTrackerFactory = new LSPMessageTrackerFactory(phaser);

        Path pomPath = this.workspaceFolderPath.resolve("pom.xml");
        try (InputStream pom = TestLegendLanguageServerIntegration.class.getClassLoader().getResourceAsStream("integrationTestPom.xml"))
        {
            Files.copy(Objects.requireNonNull(pom, "missing integration test pom"), pomPath);
        }

        PipedInputStream clientInput = new PipedInputStream();
        PipedOutputStream serverOutput = new PipedOutputStream(clientInput);

        PipedInputStream serverInput = new PipedInputStream();
        PipedOutputStream clientOutput = new PipedOutputStream(serverInput);

        Launcher<LegendLanguageServerContract> clientLauncher = new LSPLauncher.Builder<LegendLanguageServerContract>()
                .setLocalService(client)
                .setInput(clientInput)
                .setOutput(clientOutput)
                .setExecutorService(executorService)
                .setRemoteInterface(LegendLanguageServerContract.class)
                .wrapMessages(lspMessageTrackerFactory.create())
                .create();

        clientFuture = clientLauncher.startListening();
        server = clientLauncher.getRemoteProxy();

        LegendLanguageServer serverImpl = LegendLanguageServer.builder().asynchronous(executorService).classpathFromMaven(pomPath.toFile()).build();
        Launcher<LanguageClient> serverLauncher = LSPLauncher.createServerLauncher(
                serverImpl,
                serverInput,
                serverOutput,
                executorService,
                lspMessageTrackerFactory.create()
        );
        serverFuture = serverLauncher.startListening();
        serverImpl.connect(serverLauncher.getRemoteProxy());


        InitializeParams initializeParams = new InitializeParams();
        initializeParams.setWorkspaceFolders(List.of(new WorkspaceFolder(workspaceFolderPath.toUri().toString())));
        initializeParams.setCapabilities(new ClientCapabilities());
        server.initialize(initializeParams).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        server.initialized(new InitializedParams());
        waitForAllTaskToComplete();

        GlobalState globalState = serverImpl.getGlobalState();
        Assertions.assertFalse(globalState.getAvailableGrammarExtensions().isEmpty(), "No grammar extensions discovered during initialization.  Check logs for errors (maybe corrupted pom, failures during maven execution)");
    }

    public void waitForAllTaskToComplete() throws InterruptedException, TimeoutException
    {
        try
        {
            phaser.awaitAdvanceInterruptibly(phaser.arrive(), MAYBE_DEADLOCK_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);
        }
        catch (TimeoutException e)
        {
            // catch to add phaser state, and rethrow TimeoutException to allow test watcher to print thread dump...
            throw new TimeoutException("Not all parties arrived to phaser? " + phaser);
        }
    }

    public Path addToWorkspace(String relativePath, String content) throws Exception
    {
        Path pureFile = workspaceFolderPath.resolve(relativePath);
        Files.writeString(pureFile, content);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(pureFile.toUri().toString(), "legend", 0, content)));
        waitForAllTaskToComplete();
        return pureFile;
    }

    public void changeWorkspaceFile(Path pureFile, String content) throws Exception
    {
        server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(pureFile.toUri().toString(), 1), List.of(new TextDocumentContentChangeEvent(content))));
        waitForAllTaskToComplete();
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause)
    {
        this.threadDumpIfTimeoutException(cause);
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause)
    {
        this.threadDumpIfTimeoutException(cause);
    }

    private void threadDumpIfTimeoutException(Throwable e)
    {
        if (e instanceof TimeoutException)
        {
            String threadDump = Stream.of(ManagementFactory.getThreadMXBean().dumpAllThreads(true, true))
                    .map(ThreadInfo::toString)
                    .collect(Collectors.joining("\n"));
            System.out.println("###############################################################");
            System.out.println("FUTURE TASK TIMEOUT - THERE COULD BE A THREAD DEADLOCK - THREAD DUMP");
            System.out.println();
            System.out.println(threadDump);
            System.out.println();
            System.out.println("###############################################################");
        }
    }

    public LegendLanguageServerContract getServer()
    {
        return this.server;
    }

    public <T> T futureGet(Future<T> future) throws ExecutionException, InterruptedException, TimeoutException
    {
        return future.get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public <T> T futureGet(CompletableFuture<Object> future, TypeToken<T> token) throws ExecutionException, InterruptedException, TimeoutException
    {
        Gson gson = new MessageJsonHandler(Map.of()).getGson();
        return futureGet(future.thenApply(x -> gson.fromJson(gson.toJsonTree(x), token)));
    }

    public void assertWorkspaceParseAndCompiles() throws Exception
    {
        WorkspaceDiagnosticReport workspaceDiagnosticReport = this.futureGet(this.getServer().getWorkspaceService().diagnostic(new WorkspaceDiagnosticParams(List.of())));
        List<Diagnostic> diagnostics = workspaceDiagnosticReport.getItems().stream()
                .map(WorkspaceDocumentDiagnosticReport::getWorkspaceFullDocumentDiagnosticReport)
                .map(FullDocumentDiagnosticReport::getItems)
                .flatMap(List::stream)
                .collect(Collectors.toList());
        Assertions.assertTrue(diagnostics.isEmpty(), "Expected no diagnostics, got: " + diagnostics);
    }
}
