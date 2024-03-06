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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;
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
import org.finos.legend.engine.ide.lsp.extension.agGrid.FunctionTDSRequest;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSSort;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSSortOrder;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSRequest;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSGroupBy;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSAggregation;
import org.finos.legend.engine.ide.lsp.extension.agGrid.Filter;
import org.finos.legend.engine.ide.lsp.extension.agGrid.FilterOperation;
import org.finos.legend.engine.ide.lsp.extension.agGrid.ColumnType;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageServer;
import org.finos.legend.engine.ide.lsp.server.LegendTextDocumentService;
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
    private static final long MAYBE_DEADLOCK_TIMEOUT_SECONDS = 30L;
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
    private static LegendTextDocumentService textDocumentService;

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

        textDocumentService = serverImpl.getTextDocumentService();

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

    private static class TDSRow
    {
        private final Object values;

        public TDSRow(Object values)
        {
            this.values = values;
        }

        public Object getValues()
        {
            return this.values;
        }
    }

    private static class TabularDataSet
    {
        private final List<String> columns;
        private final List<TDSRow> rows;

        public TabularDataSet(List<String> columns, List<TDSRow> rows)
        {
            this.columns = columns;
            this.rows = rows;
        }

        public List<String> getColumns()
        {
            return this.columns;
        }

        public List<TDSRow> getRows()
        {
            return this.rows;
        }
    }

    private static class TDSResult
    {
        private final TabularDataSet result;

        public TDSResult(TabularDataSet result)
        {
            this.result = result;
        }

        public TabularDataSet getResult()
        {
            return this.result;
        }
    }

    private TabularDataSet getTabularDataSet(Object result)
    {
        if (result instanceof LegendExecutionResult)
        {
            Gson gson = new Gson();
            return gson.fromJson(((LegendExecutionResult) result).getMessage(), TDSResult.class).getResult();
        }
        return null;
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

    @Test
    void testFunction() throws Exception
    {
        Path pureFile1 = workspaceFolderPath.resolve("file1.pure");

        addToWorkspace(pureFile1, "Class model::Person\n" +
                "{\n" +
                "  firstName: String[1];\n" +
                "  lastName: String[1];\n" +
                "}\n" +
                "Class model::Firm\n" +
                "{\n" +
                "  legalName: String[1];\n" +
                "  employees: model::Person[*];\n" +
                "}\n" +
                "function model1::testReturnTDS(): meta::pure::tds::TabularDataSet[1]\n" +
                "{\n" +
                "  model::Firm.all()->project([x | $x.legalName,x | $x.employees.firstName, x |$x.employees.lastName], ['Legal Name', 'Employees/ First Name', 'Employees/ Last Name'])->from(execution::RelationalMapping, execution::Runtime);\n" +
                "}\n" +
                "\n" +
                "###Mapping\n" +
                "Mapping execution::RelationalMapping\n" +
                "(\n" +
                "  *model::Person: Relational\n" +
                "  {\n" +
                "    ~primaryKey\n" +
                "    (\n" +
                "      [store::TestDB]PersonTable.id\n" +
                "    )\n" +
                "    ~mainTable [store::TestDB]PersonTable\n" +
                "    firstName: [store::TestDB]PersonTable.firstName,\n" +
                "    lastName: [store::TestDB]PersonTable.lastName\n" +
                "  }\n" +
                "  *model::Firm: Relational\n" +
                "  {\n" +
                "    ~primaryKey\n" +
                "    (\n" +
                "      [store::TestDB]FirmTable.id\n" +
                "    )\n" +
                "    ~mainTable [store::TestDB]FirmTable\n" +
                "    legalName: [store::TestDB]FirmTable.legal_name,\n" +
                "    employees[model_Person]: [store::TestDB]@FirmPerson\n" +
                "  }\n" +
                ")\n" +
                "\n" +
                "###Runtime\n" +
                "Runtime execution::Runtime\n" +
                "{\n" +
                "  mappings:\n" +
                "  [\n" +
                "    execution::RelationalMapping\n" +
                "  ];\n" +
                "  connections:\n" +
                "  [\n" +
                "    store::TestDB:\n" +
                "    [\n" +
                "      connection_1:\n" +
                "      #{\n" +
                "        RelationalDatabaseConnection\n" +
                "        {\n" +
                "          store: store::TestDB;\n" +
                "          type: H2;\n" +
                "          specification: LocalH2\n" +
                "          {\n" +
                "            testDataSetupSqls: [\n" +
                "              'Drop table if exists FirmTable;\\nDrop table if exists PersonTable;\\nCreate Table FirmTable(id INT, Legal_Name VARCHAR(200));\\nCreate Table PersonTable(id INT, firm_id INT, lastName VARCHAR(200), firstName VARCHAR(200));\\nInsert into FirmTable (id, Legal_Name) values (1, \\'FirmA\\');\\nInsert into FirmTable (id, Legal_Name) values (2, \\'Apple\\');\\nInsert into FirmTable (id, Legal_Name) values (3, \\'FirmB\\');\\nInsert into PersonTable (id, firm_id, lastName, firstName) values (1, 1, \\'John\\', \\'Doe\\');\\nInsert into PersonTable (id, firm_id, lastName, firstName) values (2, 2, \\'Tim\\', \\'Smith\\');\\nInsert into PersonTable (id, firm_id, lastName, firstName) values (3, 3, \\'Nicole\\', \\'Doe\\');\\n\\n'\n" +
                "              ];\n" +
                "          };\n" +
                "          auth: DefaultH2;\n" +
                "        }\n" +
                "      }#\n" +
                "    ]\n" +
                "  ];\n" +
                "}\n" +
                "\n" +
                "###Relational\n" +
                "Database store::TestDB\n" +
                "(\n" +
                "  Table FirmTable\n" +
                "  (\n" +
                "    id INTEGER PRIMARY KEY,\n" +
                "    legal_name VARCHAR(200)\n" +
                "  )\n" +
                "  Table PersonTable\n" +
                "  (\n" +
                "    id INTEGER PRIMARY KEY,\n" +
                "    firm_id INTEGER,\n" +
                "    firstName VARCHAR(200),\n" +
                "    lastName VARCHAR(200)\n" +
                "  )\n" +
                "\n" +
                "  Join FirmPerson(PersonTable.firm_id = FirmTable.id)\n" +
                ")\n" +
                "\n");

        String uri = pureFile1.toUri().toString();
        int sectionNum = 0;
        String entity = "model1::testReturnTDS__TabularDataSet_1_";

        List<TDSSort> sort = new ArrayList<>();
        List<Filter> filter = new ArrayList<>();
        List<String> columns = List.of("Legal Name", "Employees/ First Name", "Employees/ Last Name");
        List<String> groupByColumns = new ArrayList<>();
        List<String> groupKeys = new ArrayList<>();
        List<TDSAggregation> aggregations = new ArrayList<>();
        TDSGroupBy groupBy = new TDSGroupBy(groupByColumns, groupKeys, aggregations);
        TDSRequest request = new TDSRequest(0, 0, columns, filter, sort, groupBy);
        FunctionTDSRequest functionTDSRequest = new FunctionTDSRequest(uri, sectionNum, entity, request, Collections.emptyMap());

        // No push down operations
        Object resultObject = textDocumentService.legendTDSRequest(functionTDSRequest).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        TabularDataSet result = getTabularDataSet(resultObject);
        Assertions.assertEquals(result.getColumns().size(), 3);
        Assertions.assertEquals(result.getRows().size(), 3);
        Assertions.assertEquals(result.getRows().get(0).getValues(), List.of("FirmA", "Doe", "John"));
        Assertions.assertEquals(result.getRows().get(1).getValues(), List.of("Apple", "Smith", "Tim"));
        Assertions.assertEquals(result.getRows().get(2).getValues(), List.of("FirmB", "Doe", "Nicole"));

        // Sort operation on first row
        sort.add(new TDSSort("Legal Name", TDSSortOrder.ASCENDING));
        resultObject = textDocumentService.legendTDSRequest(functionTDSRequest).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        result = getTabularDataSet(resultObject);
        Assertions.assertEquals(result.getColumns().size(), 3);
        Assertions.assertEquals(result.getRows().size(), 3);
        Assertions.assertEquals(result.getRows().get(0).getValues(), List.of("Apple", "Smith", "Tim"));
        Assertions.assertEquals(result.getRows().get(1).getValues(), List.of("FirmA", "Doe", "John"));
        Assertions.assertEquals(result.getRows().get(2).getValues(), List.of("FirmB", "Doe", "Nicole"));

        // Filter operation on second row
        sort.clear();
        filter.add(new Filter("Employees/ First Name", ColumnType.String, FilterOperation.EQUALS, "Doe"));
        resultObject = textDocumentService.legendTDSRequest(functionTDSRequest).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        result = getTabularDataSet(resultObject);
        Assertions.assertEquals(result.getColumns().size(), 3);
        Assertions.assertEquals(result.getRows().size(), 2);
        Assertions.assertEquals(result.getRows().get(0).getValues(), List.of("FirmA", "Doe", "John"));
        Assertions.assertEquals(result.getRows().get(1).getValues(), List.of("FirmB", "Doe", "Nicole"));

        // Groupby operation
        filter.clear();
        groupByColumns.add("Legal Name");
        resultObject = textDocumentService.legendTDSRequest(functionTDSRequest).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        result = getTabularDataSet(resultObject);
        Assertions.assertEquals(result.getColumns().size(), 1);
        Assertions.assertEquals(result.getRows().size(), 3);
        Assertions.assertEquals(result.getRows().get(0).getValues(), List.of("Apple"));
        Assertions.assertEquals(result.getRows().get(1).getValues(), List.of("FirmA"));
        Assertions.assertEquals(result.getRows().get(2).getValues(), List.of("FirmB"));

        // Expand groupBy
        groupKeys.add("Apple");
        resultObject = textDocumentService.legendTDSRequest(functionTDSRequest).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        result = getTabularDataSet(resultObject);
        Assertions.assertEquals(result.getColumns().size(), 3);
        Assertions.assertEquals(result.getRows().size(), 1);
        Assertions.assertEquals(result.getRows().get(0).getValues(), List.of("Apple", "Smith", "Tim"));
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
        Assertions.assertEquals(
                Set.of(pureFile1.toUri().toString(), pureFile2.toUri().toString()),
                items.stream()
                        .map(WorkspaceDocumentDiagnosticReport::getWorkspaceFullDocumentDiagnosticReport)
                        .map(WorkspaceFullDocumentDiagnosticReport::getUri)
                        .collect(Collectors.toSet())
        );

        List<Diagnostic> diagnostics = items.stream()
                .map(WorkspaceDocumentDiagnosticReport::getWorkspaceFullDocumentDiagnosticReport)
                .map(WorkspaceFullDocumentDiagnosticReport::getItems)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        Assertions.assertTrue(diagnostics.isEmpty(), "Expected no diagnostic but got: " + diagnostics
                .stream()
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
        Assertions.assertEquals(2, itemsAfterChange.size());
        itemsAfterChange.sort(Comparator.comparing(x -> x.getWorkspaceFullDocumentDiagnosticReport().getUri()));

        WorkspaceFullDocumentDiagnosticReport diagnosticReport1 = itemsAfterChange.get(0).getWorkspaceFullDocumentDiagnosticReport();
        Assertions.assertNotNull(diagnosticReport1);
        Assertions.assertEquals(pureFile1.toUri().toString(), diagnosticReport1.getUri());
        Assertions.assertTrue(diagnosticReport1.getItems().isEmpty());

        WorkspaceFullDocumentDiagnosticReport diagnosticReport2 = itemsAfterChange.get(1).getWorkspaceFullDocumentDiagnosticReport();
        Assertions.assertNotNull(diagnosticReport2);
        Assertions.assertEquals(pureFile2.toUri().toString(), diagnosticReport2.getUri());
        Assertions.assertEquals(1, diagnosticReport2.getItems().size());
        Assertions.assertEquals("Compiler", diagnosticReport2.getItems().get(0).getSource());
        Assertions.assertEquals("Can't find type 'abc::abc'", diagnosticReport2.getItems().get(0).getMessage());

        // revert rename on extended class, should fix the compile failure
        changeWorkspaceFile(pureFile1, "###Pure\n" +
                "Class abc::abc\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        // no diagnostics
        List<WorkspaceDocumentDiagnosticReport> itemsAfterFix = server.getWorkspaceService().diagnostic(new WorkspaceDiagnosticParams(List.of())).get(MAYBE_DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS).getItems();
        List<Diagnostic> diagnosticsFixed = itemsAfterFix.stream()
                .map(WorkspaceDocumentDiagnosticReport::getWorkspaceFullDocumentDiagnosticReport)
                .map(WorkspaceFullDocumentDiagnosticReport::getItems)
                .flatMap(List::stream)
                .collect(Collectors.toList());
        Assertions.assertTrue(diagnosticsFixed.isEmpty());
    }
}
