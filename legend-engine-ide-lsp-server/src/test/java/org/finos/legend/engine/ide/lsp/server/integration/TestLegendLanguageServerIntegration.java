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

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PreviousResultId;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.utils.LegendToLSPUtilities;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
// all tests should finish but in case of some uncaught deadlock, timeout whole test
public class TestLegendLanguageServerIntegration
{
    @RegisterExtension
    static LegendLanguageServerIntegrationExtension extension = new LegendLanguageServerIntegrationExtension();

    @Test
    void testUnknownGrammar() throws Exception
    {
        String content = "###HelloGrammar\n" +
                "Hello abc::abc\n" +
                "{\n" +
                "  abc: 1\n" +
                "}\n";

        Path pureFile = extension.addToWorkspace("hello.pure", content);

        DocumentDiagnosticReport diagnosticReport = extension.futureGet(extension.getServer().getTextDocumentService().diagnostic(new DocumentDiagnosticParams(new TextDocumentIdentifier(pureFile.toUri().toString()))));
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
        Path file1Path = extension.addToWorkspace("file1.pure", "###Pure\n" +
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

        Path file2Path = extension.addToWorkspace("file2.pure", "###Pure\n" +
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

        Path enumPath = extension.addToWorkspace("enum.pure", "Enum test::model::TestEnumeration\n" +
                "{\n" +
                "  VAL1, VAL2,\n" +
                "  VAL3, VAL4\n" +
                "}\n");

        List<? extends WorkspaceSymbol> symbols = extension.futureGet(extension.getServer().getWorkspaceService().symbol(new WorkspaceSymbolParams(""))).getRight();
        Assertions.assertNotNull(symbols);

        symbols.sort(Comparator.comparing(WorkspaceSymbol::getName));

        List<WorkspaceSymbol> expected = List.of(
                createWorkspaceSymbol("abc::abc", SymbolKind.Class, TextLocation.newTextSource(file1Path.toUri().toString(), 1, 0, 4, 0), null, "meta::pure::metamodel::type::Class"),
                createWorkspaceSymbol("abc::abc.abc", SymbolKind.Field, TextLocation.newTextSource(file1Path.toUri().toString(), 3, 2, 3, 16), "abc::abc", "meta::pure::metamodel::function::property::Property"),
                createWorkspaceSymbol("abc::abc2", SymbolKind.Class, TextLocation.newTextSource(file1Path.toUri().toString(), 5, 0, 8, 0), null, "meta::pure::metamodel::type::Class"),
                createWorkspaceSymbol("abc::abc2.abc", SymbolKind.Field, TextLocation.newTextSource(file1Path.toUri().toString(), 7, 2, 7, 16), "abc::abc2", "meta::pure::metamodel::function::property::Property"),
                createWorkspaceSymbol("abc::abc3", SymbolKind.Class, TextLocation.newTextSource(file1Path.toUri().toString(), 9, 0, 12, 0), null, "meta::pure::metamodel::type::Class"),
                createWorkspaceSymbol("abc::abc3.abc", SymbolKind.Field, TextLocation.newTextSource(file1Path.toUri().toString(), 11, 2, 11, 16), "abc::abc3", "meta::pure::metamodel::function::property::Property"),

                createWorkspaceSymbol("test::model::TestEnumeration", SymbolKind.Enum, TextLocation.newTextSource(enumPath.toUri().toString(), 0, 0, 4, 0), null, "meta::pure::metamodel::type::Enumeration"),
                createWorkspaceSymbol("test::model::TestEnumeration.VAL1", SymbolKind.EnumMember, TextLocation.newTextSource(enumPath.toUri().toString(), 2, 2, 2, 5), "test::model::TestEnumeration", "test::model::TestEnumeration"),
                createWorkspaceSymbol("test::model::TestEnumeration.VAL2", SymbolKind.EnumMember, TextLocation.newTextSource(enumPath.toUri().toString(), 2, 8, 2, 11), "test::model::TestEnumeration", "test::model::TestEnumeration"),
                createWorkspaceSymbol("test::model::TestEnumeration.VAL3", SymbolKind.EnumMember, TextLocation.newTextSource(enumPath.toUri().toString(), 3, 2, 3, 5), "test::model::TestEnumeration", "test::model::TestEnumeration"),
                createWorkspaceSymbol("test::model::TestEnumeration.VAL4", SymbolKind.EnumMember, TextLocation.newTextSource(enumPath.toUri().toString(), 3, 8, 3, 11), "test::model::TestEnumeration", "test::model::TestEnumeration"),

                createWorkspaceSymbol("vscodelsp::test::dependency::Employee", SymbolKind.Class, TextLocation.newTextSource("legend-vfs:/dependencies.pure", 2, 0, 6, 0), null, "meta::pure::metamodel::type::Class"),
                createWorkspaceSymbol("vscodelsp::test::dependency::Employee.foobar1", SymbolKind.Field, TextLocation.newTextSource("legend-vfs:/dependencies.pure", 4, 2, 4, 19), "vscodelsp::test::dependency::Employee", "meta::pure::metamodel::function::property::Property"),
                createWorkspaceSymbol("vscodelsp::test::dependency::Employee.foobar2", SymbolKind.Field, TextLocation.newTextSource("legend-vfs:/dependencies.pure", 5, 2, 5, 19), "vscodelsp::test::dependency::Employee", "meta::pure::metamodel::function::property::Property"),

                createWorkspaceSymbol("xyz::abc", SymbolKind.Class, TextLocation.newTextSource(file2Path.toUri().toString(), 1, 0, 4, 0), null, "meta::pure::metamodel::type::Class"),
                createWorkspaceSymbol("xyz::abc.abc", SymbolKind.Field, TextLocation.newTextSource(file2Path.toUri().toString(), 3, 2, 3, 16), "xyz::abc", "meta::pure::metamodel::function::property::Property"),
                createWorkspaceSymbol("xyz::abc2", SymbolKind.Class, TextLocation.newTextSource(file2Path.toUri().toString(), 5, 0, 8, 0), null, "meta::pure::metamodel::type::Class"),
                createWorkspaceSymbol("xyz::abc2.abc", SymbolKind.Field, TextLocation.newTextSource(file2Path.toUri().toString(), 7, 2, 7, 16), "xyz::abc2", "meta::pure::metamodel::function::property::Property"),
                createWorkspaceSymbol("xyz::abc3", SymbolKind.Class, TextLocation.newTextSource(file2Path.toUri().toString(), 9, 0, 12, 0), null, "meta::pure::metamodel::type::Class"),
                createWorkspaceSymbol("xyz::abc3.abc", SymbolKind.Field, TextLocation.newTextSource(file2Path.toUri().toString(), 11, 2, 11, 16), "xyz::abc3", "meta::pure::metamodel::function::property::Property")
        );

        for (int i = 0; i < Math.max(symbols.size(), expected.size()); i++)
        {
            WorkspaceSymbol expectedSymbol = null;
            if (expected.size() > i)
            {
                expectedSymbol = expected.get(i);
            }

            WorkspaceSymbol actualSymbol = null;
            if (symbols.size() > i)
            {
                actualSymbol = symbols.get(i);
            }

            Assertions.assertEquals(expectedSymbol, actualSymbol, String.format("Symbol at %d are not equal", i));
        }


        List<? extends WorkspaceSymbol> symbolsFiltered1 = extension.futureGet(extension.getServer().getWorkspaceService().symbol(new WorkspaceSymbolParams("xyz"))).getRight();
        Set<String> symbolNamesFiltered1 = symbolsFiltered1.stream().map(WorkspaceSymbol::getName).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("xyz::abc", "xyz::abc2", "xyz::abc3"), symbolNamesFiltered1);

        List<? extends WorkspaceSymbol> symbolsFiltered2 = extension.futureGet(extension.getServer().getWorkspaceService().symbol(new WorkspaceSymbolParams("abc2"))).getRight();
        Set<String> symbolNamesFiltered2 = symbolsFiltered2.stream().map(WorkspaceSymbol::getName).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("abc::abc2", "xyz::abc2"), symbolNamesFiltered2);
    }

    private WorkspaceSymbol createWorkspaceSymbol(String name, SymbolKind kind, TextLocation textLocation, String containerName, String classifier)
    {
        Location location = new Location(textLocation.getDocumentId(), LegendToLSPUtilities.toRange(textLocation.getTextInterval()));
        WorkspaceSymbol workspaceSymbol = new WorkspaceSymbol(name, kind, Either.forLeft(location), containerName);
        JsonObject data = new JsonObject();
        data.add("classifier", new JsonPrimitive(classifier));
        workspaceSymbol.setData(data);
        return workspaceSymbol;
    }

    // repeat to test for race conditions, thread dead-locks, etc
    @RepeatedTest(value = 10, failureThreshold = 1)
    void testWorkspaceDiagnostic() throws Exception
    {
        // define class
        Path pureFile1 = extension.addToWorkspace("file1.pure", "###Pure\n" +
                "Class abc::abc\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        // extend class
        Path pureFile2 = extension.addToWorkspace("file2.pure", "###Pure\n" +
                "Class xyz::abc extends abc::abc\n" +
                "{\n" +
                "  xyz: String[1];\n" +
                "}\n");

        // no diagnostics as it parses and compiles
        assertDiagnostics(Map.of(), List.of());

        // creates a parse error on file 2, so only that diagnostics comes back
        extension.changeWorkspaceFile(pureFile2, "###Pure\n" +
                "Class xyz::abc extends abc::abc\n" +
                "{\n" +
                "  xyz: String[1\n" +
                "}\n");

        // diagnostics reported on file
        Set<Diagnostic> parseDiagnostic = Set.of(
                new Diagnostic(
                        new Range(new Position(4, 0), new Position(4, 1)),
                        "Unexpected token '}'. Valid alternatives: [']']",
                        DiagnosticSeverity.Error,
                        "Parser"
                )
        );

        List<PreviousResultId> previousResultIds = assertDiagnostics(Map.of(pureFile2, parseDiagnostic), List.of());
        // repeating asking for diagnostics using prev result id yield no result
        assertDiagnostics(Map.of(), previousResultIds);
        // but results are reported if result ids are different
        previousResultIds = assertDiagnostics(Map.of(pureFile2, parseDiagnostic), List.of());

        // fix parser error
        extension.changeWorkspaceFile(pureFile2, "###Pure\n" +
                "Class xyz::abc extends abc::abc\n" +
                "{\n" +
                "  xyz: String[1];\n" +
                "}\n");

        // the document report diagnostics, but with empty items
        previousResultIds = assertDiagnostics(Map.of(pureFile2, Set.of()), previousResultIds);
        // no diagnostics now reported with either prev result id or no ids
        assertDiagnostics(Map.of(), previousResultIds);

        // create compile error on file 2
        extension.changeWorkspaceFile(pureFile1, "###Pure\n" +
                "Class abc::ab\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        // report compile diagnostics on file
        Set<Diagnostic> compileDiagnostic = Set.of(
                new Diagnostic(
                        new Range(new Position(1, 0), new Position(4, 1)),
                        "Can't find type 'abc::abc'",
                        DiagnosticSeverity.Error,
                        "Compiler"
                )
        );
        previousResultIds = assertDiagnostics(Map.of(pureFile2, compileDiagnostic), previousResultIds);
        // repeating call yield no diagnostic reported
        assertDiagnostics(Map.of(), previousResultIds);

        // fix compile error
        extension.changeWorkspaceFile(pureFile2, "###Pure\n" +
                "Class xyz::abc extends abc::ab\n" +
                "{\n" +
                "  xyz: String[1];\n" +
                "}\n");

        // the document report diagnostics, but with empty items
        previousResultIds = assertDiagnostics(Map.of(pureFile2, Set.of()), previousResultIds);
        // no diagnostic if called again
        assertDiagnostics(Map.of(), previousResultIds);

        extension.changeWorkspaceFile(pureFile1, "###Pure\n" +
                "Clas abc::ab\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        extension.changeWorkspaceFile(pureFile2, "###Pure\n" +
                "Clas xyz::abc extends abc::ab\n" +
                "{\n" +
                "  xyz: String[1];\n" +
                "}\n");

        // parse error on both files
        Map<Path, Set<Diagnostic>> expected = Map.of(
                pureFile2, Set.of(new Diagnostic(
                        new Range(new Position(1, 0), new Position(1, 4)),
                        "Unexpected token 'Clas'. Valid alternatives: ['Class', 'Association', 'Profile', 'Enum', 'Measure', 'function', 'native', '^']",
                        DiagnosticSeverity.Error,
                        "Parser"
                )),
                pureFile1, Set.of(new Diagnostic(
                        new Range(new Position(1, 0), new Position(1, 4)),
                        "Unexpected token 'Clas'. Valid alternatives: ['Class', 'Association', 'Profile', 'Enum', 'Measure', 'function', 'native', '^']",
                        DiagnosticSeverity.Error,
                        "Parser"
                )));
        previousResultIds = assertDiagnostics(expected, previousResultIds);
        assertDiagnostics(Map.of(), previousResultIds);

        // fix parse errors
        extension.changeWorkspaceFile(pureFile1, "###Pure\n" +
                "Class abc::ab\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        extension.changeWorkspaceFile(pureFile2, "###Pure\n" +
                "Class xyz::abc extends abc::ab\n" +
                "{\n" +
                "  xyz: String[1];\n" +
                "}\n");

        // report for both files, but empty diagnostics
        previousResultIds = assertDiagnostics(Map.of(pureFile2, Set.of(), pureFile1, Set.of()), previousResultIds);
        // no diagnostics if called again
        assertDiagnostics(Map.of(), previousResultIds);
    }

    private static List<PreviousResultId> assertDiagnostics(Map<Path, Set<Diagnostic>> expected, List<PreviousResultId> ids) throws Exception
    {
        List<WorkspaceDocumentDiagnosticReport> items = extension.futureGet(extension.getServer().getWorkspaceService().diagnostic(new WorkspaceDiagnosticParams(ids))).getItems();

        Map<Path, Set<Diagnostic>> reportsByUri = items
                .stream()
                .collect(Collectors.toMap(
                                x -> Path.of(URI.create(x.getWorkspaceFullDocumentDiagnosticReport().getUri())),
                                x -> new HashSet<>(x.getWorkspaceFullDocumentDiagnosticReport().getItems())
                        )
                );

        Assertions.assertEquals(expected, reportsByUri);

        return items
                .stream()
                .map(x -> new PreviousResultId(x.getWorkspaceFullDocumentDiagnosticReport().getUri(), x.getWorkspaceFullDocumentDiagnosticReport().getResultId()))
                .collect(Collectors.toList());
    }

    @Test
    void testReplStartWithGivenClasspath() throws Exception
    {
        String classpath = extension.futureGet(extension.getServer().getLegendLanguageService().replClasspath());

        ProcessBuilder processBuilder = new ProcessBuilder(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "org.finos.legend.engine.ide.lsp.server.LegendREPLTerminal"
        );
        processBuilder.environment().put("CLASSPATH", classpath);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = null;
        try
        {
            process = processBuilder.start();
            Assertions.assertTrue(process.isAlive());
            read(process.getInputStream(), "Ready!");
        }
        finally
        {
            if (process != null)
            {
                process.destroy();
                process.onExit().join();
            }
        }
    }

    private static void read(InputStream replOutputConsole, String untilToken) throws IOException
    {
        StringBuilder output = new StringBuilder();
        while (!output.toString().contains(untilToken))
        {
            int read = replOutputConsole.read();
            if (read != -1)
            {
                System.err.print((char) read);
                output.append((char) read);
            }
            else
            {
                Assertions.fail("Did not found token and stream closed...");
            }

        }
    }

    @Test
    void codeLensCommandsFunctionActivator() throws Exception
    {
        String code1 = "###Pure\n" +
                "function model::Hello(name: String[1]): String[1]\n" +
                "{\n" +
                "  'Hello World! My name is ' + $name + '.';\n" +
                "}\n" +
                "{\n" +
                "  testSuite_1\n" +
                "  (\n" +
                "    testPass | Hello('John') => 'Hello World! My name is John.';\n" +
                "  )\n" +
                "}\n";

        String code2 = "###Snowflake\n" +
                "SnowflakeApp app::pack::MyApp\n" +
                "{" +
                "   applicationName : 'name';\n" +
                "   function : model::Hello(String[1]):String[1];\n" +
                "   ownership : Deployment { identifier: 'MyAppOwnership'};\n" +
                "}\n";

        extension.addToWorkspace("file1.pure", code1);
        Path path = extension.addToWorkspace("file2.pure", code2);
        extension.assertWorkspaceParseAndCompiles();

        String file = path.toUri().toString();
        List<? extends CodeLens> codeLensWithoutServer = extension.futureGet(extension.getServer().getTextDocumentService().codeLens(new CodeLensParams(new TextDocumentIdentifier(file))));

        Assertions.assertTrue(codeLensWithoutServer.isEmpty(), "Expect empty, got: " + codeLensWithoutServer);

        try
        {
            System.setProperty("legend.engine.server.url", "http://localhost/hello");
            List<? extends CodeLens> codeLensWithServer = extension.futureGet(extension.getServer().getTextDocumentService().codeLens(new CodeLensParams(new TextDocumentIdentifier(file))));

            codeLensWithServer.sort(Comparator.comparing(x -> x.getCommand().getTitle()));

            Assertions.assertEquals(2, codeLensWithServer.size(), "Expect 2 code lends, got: " + codeLensWithoutServer);
            Assertions.assertEquals("Publish to Sandbox", codeLensWithServer.get(0).getCommand().getTitle());
            Assertions.assertEquals("Validate", codeLensWithServer.get(1).getCommand().getTitle());
        }
        finally
        {
            System.clearProperty("legend.engine.server.url");
        }
    }

    @Test
    void virtualFileSystem() throws Exception
    {
        String content = extension.futureGet(extension.getServer().getLegendLanguageService().loadLegendVirtualFile("legend-vfs:/dependencies.pure"));
        Assertions.assertEquals(
                "// READ ONLY (sourced from workspace dependencies)\n\n" +
                        "Class vscodelsp::test::dependency::Employee\n" +
                        "{\n" +
                        "  foobar1: Float[1];\n" +
                        "  foobar2: Float[1];\n" +
                        "}\n",
                content
        );
        ResponseErrorException exception = Assertions.assertThrows(ResponseErrorException.class, () -> extension.futureGet(extension.getServer().getLegendLanguageService().loadLegendVirtualFile("file:/dependencies.pure")));
        Assertions.assertTrue(exception.getResponseError().getData().toString().contains("Provided URI not managed by Legend Virtual Filesystem: " + "file:/dependencies.pure"));
    }
}
