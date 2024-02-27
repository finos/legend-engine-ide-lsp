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

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.FileDelete;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.finos.legend.engine.ide.lsp.DummyLanguageClient;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@Timeout(value = 3, unit = TimeUnit.MINUTES) // all tests should finish but in case of some uncaught deadlock, timeout whole test
@ExtendWith(ThreadDumpOnTimeoutExceptionTestWatcher.class)
public class TestLegendLanguageServerIntegration
{
    /**
     * Rather than blocking indefinitely for an async task to complete, the test cases should wait this
     * amount of seconds.  This will allow to exceptionally finish the test cases when thread deadlocks occur.
     * <pl/>
     * For local development and debugging, this can be increased to prevent false timeouts
     */
    private static final long MAYBE_DEADLOCK_TIMEOUT_SECONDS = 15L; //
    /**
     * this phaser is used to track all async task and LSP JRPC messages,
     * allowing the test cases to wait until these have been completed before further assertions
     */
    private static Phaser phaser;
    private static LSPAsyncTaskTrackingExecutor executorService;
    private static DummyLanguageClient client;
    private static Future<Void> clientFuture;
    private static LanguageServer server;
    private static Future<Void> serverFuture;
    private static Path workspaceFolderPath;

    @BeforeAll
    static void beforeAll(@TempDir Path tempDir) throws Exception
    {
        client = new DummyLanguageClient();
        workspaceFolderPath = tempDir;
        // default to one party member, the one arriving and waiting for all tasks and messages
        phaser = new Phaser(1);
        executorService = new LSPAsyncTaskTrackingExecutor(phaser);
        LSPMessageTrackerFactory lspMessageTrackerFactory = new LSPMessageTrackerFactory(phaser);

        Path pomPath = tempDir.resolve("pom.xml");
        try (InputStream pom = TestLegendLanguageServerIntegration.class.getClassLoader().getResourceAsStream("integrationTestPom.xml"))
        {
            Files.copy(Objects.requireNonNull(pom, "missing integration test pom"), pomPath);
        }

        PipedInputStream clientInput = new PipedInputStream();
        PipedOutputStream serverOutput = new PipedOutputStream(clientInput);

        PipedInputStream serverInput = new PipedInputStream();
        PipedOutputStream clientOutput = new PipedOutputStream(serverInput);

        Launcher<LanguageServer> clientLauncher = LSPLauncher.createClientLauncher(
                client,
                clientInput,
                clientOutput,
                executorService,
                lspMessageTrackerFactory.create()
        );
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

    @AfterEach
    void tearDown() throws Exception
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

    @AfterAll
    static void afterAll() throws Exception
    {
        clientFuture.cancel(true);
        serverFuture.cancel(true);
        executorService.shutdown();
        Assertions.assertTrue(executorService.awaitTermination(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS), () -> "Not all tasks completed during shutdown: " + executorService);
    }

    private static void addToWorkspace(Path pureFile, String content) throws Exception
    {
        Files.writeString(pureFile, content);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(pureFile.toUri().toString(), "legend", 0, content)));
        waitForAllTaskToComplete();
    }

    private static void waitForAllTaskToComplete() throws InterruptedException, TimeoutException
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

    private static void changeWorkspaceFile(Path pureFile, String content) throws Exception
    {
        server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(pureFile.toUri().toString(), 1), List.of(new TextDocumentContentChangeEvent(content))));
        waitForAllTaskToComplete();
    }

    @Test
    void testUnknownGrammar() throws Exception
    {
        Path pureFile = workspaceFolderPath.resolve("hello.pure");

        String content = "###HelloGrammar\n" +
                "Hello abc::abc\n" +
                "{\n" +
                "  abc: 1\n" +
                "}\n";

        addToWorkspace(pureFile, content);

        DocumentDiagnosticReport diagnosticReport = server.getTextDocumentService().diagnostic(new DocumentDiagnosticParams(new TextDocumentIdentifier(pureFile.toUri().toString()))).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Assertions.assertNotNull(diagnosticReport.getRelatedFullDocumentDiagnosticReport());
        Assertions.assertEquals("full", diagnosticReport.getRelatedFullDocumentDiagnosticReport().getKind());
        Assertions.assertEquals(1, diagnosticReport.getRelatedFullDocumentDiagnosticReport().getItems().size());
        Diagnostic diagnostic = diagnosticReport.getRelatedFullDocumentDiagnosticReport().getItems().get(0);
        Assertions.assertEquals("Parser", diagnostic.getSource());
        Assertions.assertTrue(diagnostic.getMessage().startsWith("Unknown grammar: HelloGrammar"));
    }

    // repeat to test for race conditions, thread dead-locks, etc
    @RepeatedTest(value = 10, failureThreshold = 1)
    void testWorkspaceSymbols() throws Exception
    {
        Path pureFile1 = workspaceFolderPath.resolve("file1.pure");
        Path pureFile2 = workspaceFolderPath.resolve("file2.pure");

        addToWorkspace(pureFile1, "###Pure\n" +
                "Class abc::abc\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n" +
                "Class abc::abc2\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n" +
                "Class abc::abc3\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        addToWorkspace(pureFile2, "###Pure\n" +
                "Class xyz::abc\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n" +
                "Class xyz::abc2\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n" +
                "Class xyz::abc3\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        List<? extends WorkspaceSymbol> symbols = server.getWorkspaceService().symbol(new WorkspaceSymbolParams("")).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS).getRight();
        Assertions.assertNotNull(symbols);

        Set<String> symbolNames = symbols.stream().map(WorkspaceSymbol::getName).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("abc::abc", "abc::abc2", "abc::abc3", "xyz::abc", "xyz::abc2", "xyz::abc3"), symbolNames);

        List<? extends WorkspaceSymbol> symbolsFiltered1 = server.getWorkspaceService().symbol(new WorkspaceSymbolParams("xyz")).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS).getRight();
        Set<String> symbolNamesFiltered1 = symbolsFiltered1.stream().map(WorkspaceSymbol::getName).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("xyz::abc", "xyz::abc2", "xyz::abc3"), symbolNamesFiltered1);

        List<? extends WorkspaceSymbol> symbolsFiltered2 = server.getWorkspaceService().symbol(new WorkspaceSymbolParams("abc2")).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS).getRight();
        Set<String> symbolNamesFiltered2 = symbolsFiltered2.stream().map(WorkspaceSymbol::getName).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("abc::abc2", "xyz::abc2"), symbolNamesFiltered2);
    }

    // repeat to test for race conditions, thread dead-locks, etc
    @RepeatedTest(value = 10, failureThreshold = 1)
    void testWorkspaceDiagnostic() throws Exception
    {
        Path pureFile1 = workspaceFolderPath.resolve("file1.pure");
        Path pureFile2 = workspaceFolderPath.resolve("file2.pure");

        // define class
        addToWorkspace(pureFile1, "###Pure\n" +
                "Class abc::abc\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        // extend class
        addToWorkspace(pureFile2, "###Pure\n" +
                "Class xyz::abc extends abc::abc\n" +
                "{\n" +
                "  xyz: String[1];\n" +
                "}\n");


        // no diagnostics
        List<WorkspaceDocumentDiagnosticReport> items = server.getWorkspaceService().diagnostic(new WorkspaceDiagnosticParams(List.of())).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS).getItems();
        Assertions.assertTrue(items.isEmpty(), "Expected no diagnostic but got: " + items.stream()
                .map(WorkspaceDocumentDiagnosticReport::getWorkspaceFullDocumentDiagnosticReport)
                .map(WorkspaceFullDocumentDiagnosticReport::getItems)
                .flatMap(List::stream)
                .map(Diagnostic::getMessage)
                .collect(Collectors.joining())
        );

        // rename extended class, should lead to compile failure
        changeWorkspaceFile(pureFile1, "###Pure\n" +
                "Class abc::abcNewName\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        List<WorkspaceDocumentDiagnosticReport> itemsAfterChange = server.getWorkspaceService().diagnostic(new WorkspaceDiagnosticParams(List.of())).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS).getItems();
        Assertions.assertEquals(1, itemsAfterChange.size());
        WorkspaceFullDocumentDiagnosticReport diagnosticReport = itemsAfterChange.get(0).getWorkspaceFullDocumentDiagnosticReport();
        Assertions.assertNotNull(diagnosticReport);
        Assertions.assertEquals(pureFile2.toUri().toString(), diagnosticReport.getUri());
        Assertions.assertEquals(1, diagnosticReport.getItems().size());
        Assertions.assertEquals("Compiler", diagnosticReport.getItems().get(0).getSource());
        Assertions.assertEquals("Can't find type 'abc::abc'", diagnosticReport.getItems().get(0).getMessage());

        // revert rename on extended class, should fix the compile failure
        changeWorkspaceFile(pureFile1, "###Pure\n" +
                "Class abc::abc\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        // no diagnostics
        List<WorkspaceDocumentDiagnosticReport> itemsAfterFix = server.getWorkspaceService().diagnostic(new WorkspaceDiagnosticParams(List.of())).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS).getItems();
        Assertions.assertTrue(itemsAfterFix.isEmpty());
    }
}