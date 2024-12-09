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
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.javacrumbs.jsonunit.JsonAssert;
import net.javacrumbs.jsonunit.core.Configuration;
import net.javacrumbs.jsonunit.core.Option;
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
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.finos.legend.engine.ide.lsp.extension.LegendEntity;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.server.request.LegendEntitiesRequest;
import org.finos.legend.engine.ide.lsp.utils.LegendToLSPUtilities;
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

                createWorkspaceSymbol("vscodelsp::test::dependency::Employee", SymbolKind.Class, TextLocation.newTextSource("legend-vfs:/dependencies.pure", 3, 0, 7, 0), null, "meta::pure::metamodel::type::Class"),
                createWorkspaceSymbol("vscodelsp::test::dependency::Employee.foobar1", SymbolKind.Field, TextLocation.newTextSource("legend-vfs:/dependencies.pure", 5, 2, 5, 19), "vscodelsp::test::dependency::Employee", "meta::pure::metamodel::function::property::Property"),
                createWorkspaceSymbol("vscodelsp::test::dependency::Employee.foobar2", SymbolKind.Field, TextLocation.newTextSource("legend-vfs:/dependencies.pure", 6, 2, 6, 19), "vscodelsp::test::dependency::Employee", "meta::pure::metamodel::function::property::Property"),

                createWorkspaceSymbol("vscodelsp::test::dependency::StaticConnection", SymbolKind.Struct, TextLocation.newTextSource("legend-vfs:/dependencies.pure", 10, 0, 17, 0), null, "meta::pure::runtime::PackageableConnection"),


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
                        new Range(new Position(1, 23), new Position(1, 31)),
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

        Map<String, Set<Diagnostic>> reportsByUri = items
                .stream()
                .collect(Collectors.toMap(
                                x -> x.getWorkspaceFullDocumentDiagnosticReport().getUri(),
                                x -> new HashSet<>(x.getWorkspaceFullDocumentDiagnosticReport().getItems())
                        )
                );

        Map<String, Set<Diagnostic>> expectedPathAsString = expected.entrySet().stream().collect(Collectors.toMap(x -> x.getKey().toUri().toString(), Map.Entry::getValue));

        Assertions.assertEquals(expectedPathAsString, reportsByUri);

        return items
                .stream()
                .map(x -> new PreviousResultId(x.getWorkspaceFullDocumentDiagnosticReport().getUri(), x.getWorkspaceFullDocumentDiagnosticReport().getResultId()))
                .collect(Collectors.toList());
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
                        "###Pure\n" +
                        "Class vscodelsp::test::dependency::Employee\n" +
                        "{\n" +
                        "  foobar1: Float[1];\n" +
                        "  foobar2: Float[1];\n" +
                        "}\n" +
                        "\n" +
                        "###Connection\n" +
                        "RelationalDatabaseConnection vscodelsp::test::dependency::StaticConnection\n" +
                        "{\n" +
                        "  type: H2;\n" +
                        "  specification: LocalH2\n" +
                        "  {\n" +
                        "  };\n" +
                        "  auth: DefaultH2;\n" +
                        "}\n" +
                        "\n",
                content
        );
        ResponseErrorException exception = Assertions.assertThrows(ResponseErrorException.class, () -> extension.futureGet(extension.getServer().getLegendLanguageService().loadLegendVirtualFile("file:/dependencies.pure")));
        Assertions.assertTrue(exception.getResponseError().getData().toString().contains("Provided URI not managed by Legend Virtual Filesystem: " + "file:/dependencies.pure"));
    }

    @Test
    void entities() throws Exception
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

        extension.addToWorkspace("file2.pure", "###Pure\n" +
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

        List<LegendEntity> entities = extension.futureGet(extension.getServer().getLegendLanguageService().entities(new LegendEntitiesRequest()));
        Assertions.assertEquals(9, entities.size());
        entities.sort(Comparator.comparing(LegendEntity::getPath));
        String json = new Gson().toJson(entities);

        Configuration jsonAssertConfig = JsonAssert.when(Option.IGNORING_EXTRA_FIELDS).whenIgnoringPaths(
                "[*].location.documentId",
                "[*].content.sourceInformation.sourceId",
                "[*].content.properties[*].sourceInformation.sourceId",
                "[*].content.properties[*].genericType.rawType.sourceInformation.sourceId",
                "[*].content.values[*].sourceInformation.sourceId"
        );
        JsonAssert.assertJsonEquals(
                "[\n" +
                        "  {\n" +
                        "    \"path\": \"abc::abc\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 2.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 5.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 4.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 4.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 4.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 4.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"abc\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 1,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 4,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"abc::abc2\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc2\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 6.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 9.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 8.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 8.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 8.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 8.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"abc\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 5,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 8,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"abc::abc3\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc3\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 10.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 13.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 12.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 12.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 12.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 12.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"abc\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 9,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 12,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"test::model::TestEnumeration\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Enumeration\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"Enumeration\",\n" +
                        "      \"name\": \"TestEnumeration\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 1.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 5.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"values\": [\n" +
                        "        {\n" +
                        "          \"value\": \"VAL1\",\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 3.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 3.0,\n" +
                        "            \"endColumn\": 6.0\n" +
                        "          }\n" +
                        "        },\n" +
                        "        {\n" +
                        "          \"value\": \"VAL2\",\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 3.0,\n" +
                        "            \"startColumn\": 9.0,\n" +
                        "            \"endLine\": 3.0,\n" +
                        "            \"endColumn\": 12.0\n" +
                        "          }\n" +
                        "        },\n" +
                        "        {\n" +
                        "          \"value\": \"VAL3\",\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 4.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 4.0,\n" +
                        "            \"endColumn\": 6.0\n" +
                        "          }\n" +
                        "        },\n" +
                        "        {\n" +
                        "          \"value\": \"VAL4\",\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 4.0,\n" +
                        "            \"startColumn\": 9.0,\n" +
                        "            \"endLine\": 4.0,\n" +
                        "            \"endColumn\": 12.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"package\": \"test::model\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 0,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 4,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"vscodelsp::test::dependency::Employee\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"Employee\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"legend-vfs:/dependencies.pure\",\n" +
                        "        \"startLine\": 4.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 8.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"foobar1\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"legend-vfs:/dependencies.pure\",\n" +
                        "                \"startLine\": 6.0,\n" +
                        "                \"startColumn\": 12.0,\n" +
                        "                \"endLine\": 6.0,\n" +
                        "                \"endColumn\": 16.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"Float\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"legend-vfs:/dependencies.pure\",\n" +
                        "            \"startLine\": 6.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 6.0,\n" +
                        "            \"endColumn\": 20.0\n" +
                        "          }\n" +
                        "        },\n" +
                        "        {\n" +
                        "          \"name\": \"foobar2\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"legend-vfs:/dependencies.pure\",\n" +
                        "                \"startLine\": 7.0,\n" +
                        "                \"startColumn\": 12.0,\n" +
                        "                \"endLine\": 7.0,\n" +
                        "                \"endColumn\": 16.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"Float\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"legend-vfs:/dependencies.pure\",\n" +
                        "            \"startLine\": 7.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 7.0,\n" +
                        "            \"endColumn\": 20.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"vscodelsp::test::dependency\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"legend-vfs:/dependencies.pure\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 3,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 7,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"vscodelsp::test::dependency::StaticConnection\",\n" +
                        "    \"classifierPath\": \"meta::pure::runtime::PackageableConnection\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"connection\",\n" +
                        "      \"name\": \"StaticConnection\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"legend-vfs:/dependencies.pure\",\n" +
                        "        \"startLine\": 11.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 18.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"connectionValue\": {\n" +
                        "        \"_type\": \"RelationalDatabaseConnection\",\n" +
                        "        \"sourceInformation\": {\n" +
                        "          \"sourceId\": \"legend-vfs:/dependencies.pure\",\n" +
                        "          \"startLine\": 11.0,\n" +
                        "          \"startColumn\": 1.0,\n" +
                        "          \"endLine\": 18.0,\n" +
                        "          \"endColumn\": 1.0\n" +
                        "        },\n" +
                        "        \"type\": \"H2\",\n" +
                        "        \"postProcessorWithParameter\": [],\n" +
                        "        \"datasourceSpecification\": {\n" +
                        "          \"_type\": \"h2Local\",\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"legend-vfs:/dependencies.pure\",\n" +
                        "            \"startLine\": 14.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 16.0,\n" +
                        "            \"endColumn\": 4.0\n" +
                        "          }\n" +
                        "        },\n" +
                        "        \"authenticationStrategy\": {\n" +
                        "          \"_type\": \"h2Default\",\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"legend-vfs:/dependencies.pure\",\n" +
                        "            \"startLine\": 17.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 17.0,\n" +
                        "            \"endColumn\": 18.0\n" +
                        "          }\n" +
                        "        },\n" +
                        "        \"databaseType\": \"H2\"\n" +
                        "      },\n" +
                        "      \"package\": \"vscodelsp::test::dependency\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"legend-vfs:/dependencies.pure\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 10,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 17,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"xyz::abc\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 2.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 5.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 4.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 4.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 4.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 4.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"xyz\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 1,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 4,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"xyz::abc2\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc2\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 6.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 9.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 8.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 8.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 8.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 8.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"xyz\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 5,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 8,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"xyz::abc3\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc3\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 10.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 13.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 12.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 12.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 12.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 12.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"xyz\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 9,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 12,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "]",
                json,
                jsonAssertConfig
        );

        List<LegendEntity> entitiesPerFile = extension.futureGet(extension.getServer().getLegendLanguageService().entities(
                        new LegendEntitiesRequest(
                                List.of(
                                        new TextDocumentIdentifier(enumPath.toUri().toString()),
                                        new TextDocumentIdentifier(file1Path.toUri().toString())
                                ),
                                List.of()
                        )
                )
        );

        Assertions.assertEquals(4, entitiesPerFile.size());
        entitiesPerFile.sort(Comparator.comparing(LegendEntity::getPath));
        String jsonPerFile = new Gson().toJson(entitiesPerFile);

        JsonAssert.assertJsonEquals(
                "[\n" +
                        "  {\n" +
                        "    \"path\": \"abc::abc\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 2.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 5.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 4.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 4.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 4.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 4.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"abc\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 1,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 4,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"abc::abc2\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc2\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 6.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 9.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 8.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 8.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 8.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 8.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"abc\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 5,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 8,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"abc::abc3\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc3\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 10.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 13.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 12.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 12.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 12.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 12.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"abc\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 9,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 12,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"test::model::TestEnumeration\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Enumeration\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"Enumeration\",\n" +
                        "      \"name\": \"TestEnumeration\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 1.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 5.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"values\": [\n" +
                        "        {\n" +
                        "          \"value\": \"VAL1\",\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 3.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 3.0,\n" +
                        "            \"endColumn\": 6.0\n" +
                        "          }\n" +
                        "        },\n" +
                        "        {\n" +
                        "          \"value\": \"VAL2\",\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 3.0,\n" +
                        "            \"startColumn\": 9.0,\n" +
                        "            \"endLine\": 3.0,\n" +
                        "            \"endColumn\": 12.0\n" +
                        "          }\n" +
                        "        },\n" +
                        "        {\n" +
                        "          \"value\": \"VAL3\",\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 4.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 4.0,\n" +
                        "            \"endColumn\": 6.0\n" +
                        "          }\n" +
                        "        },\n" +
                        "        {\n" +
                        "          \"value\": \"VAL4\",\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 4.0,\n" +
                        "            \"startColumn\": 9.0,\n" +
                        "            \"endLine\": 4.0,\n" +
                        "            \"endColumn\": 12.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"package\": \"test::model\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 0,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 4,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "]",
                jsonPerFile,
                jsonAssertConfig
        );

        List<LegendEntity> entitiesPerPath = extension.futureGet(extension.getServer().getLegendLanguageService().entities(
                        new LegendEntitiesRequest(
                                List.of(),
                                List.of("abc::abc", "xyz::abc")
                        )
                )
        );

        Assertions.assertEquals(2, entitiesPerPath.size());
        entitiesPerPath.sort(Comparator.comparing(LegendEntity::getPath));
        String jsonPerPath = new Gson().toJson(entitiesPerPath);

        JsonAssert.assertJsonEquals(
                "[\n" +
                        "  {\n" +
                        "    \"path\": \"abc::abc\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 2.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 5.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 4.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 4.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 4.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 4.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"abc\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 1,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 4,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"path\": \"xyz::abc\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 2.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 5.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 4.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 4.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 4.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 4.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"xyz\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 1,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 4,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "]",
                jsonPerPath,
                jsonAssertConfig
        );

        List<LegendEntity> entitiesPerFileAndPath = extension.futureGet(extension.getServer().getLegendLanguageService().entities(
                        new LegendEntitiesRequest(
                                List.of(
                                        new TextDocumentIdentifier(file1Path.toUri().toString())
                                ),
                                List.of("abc::abc", "xyz::abc")
                        )
                )
        );

        Assertions.assertEquals(1, entitiesPerFileAndPath.size());
        entitiesPerFileAndPath.sort(Comparator.comparing(LegendEntity::getPath));
        String jsonPerFileAndPath = new Gson().toJson(entitiesPerFileAndPath);

        JsonAssert.assertJsonEquals(
                "[\n" +
                        "  {\n" +
                        "    \"path\": \"abc::abc\",\n" +
                        "    \"classifierPath\": \"meta::pure::metamodel::type::Class\",\n" +
                        "    \"content\": {\n" +
                        "      \"_type\": \"class\",\n" +
                        "      \"name\": \"abc\",\n" +
                        "      \"sourceInformation\": {\n" +
                        "        \"sourceId\": \"\",\n" +
                        "        \"startLine\": 2.0,\n" +
                        "        \"startColumn\": 1.0,\n" +
                        "        \"endLine\": 5.0,\n" +
                        "        \"endColumn\": 1.0\n" +
                        "      },\n" +
                        "      \"superTypes\": [],\n" +
                        "      \"originalMilestonedProperties\": [],\n" +
                        "      \"properties\": [\n" +
                        "        {\n" +
                        "          \"name\": \"abc\",\n" +
                        "          \"genericType\": {\n" +
                        "            \"rawType\": {\n" +
                        "              \"_type\": \"packageableType\",\n" +
                        "              \"sourceInformation\": {\n" +
                        "                \"sourceId\": \"\",\n" +
                        "                \"startLine\": 4.0,\n" +
                        "                \"startColumn\": 8.0,\n" +
                        "                \"endLine\": 4.0,\n" +
                        "                \"endColumn\": 13.0\n" +
                        "              },\n" +
                        "              \"fullPath\": \"String\"\n" +
                        "            },\n" +
                        "            \"typeArguments\": [],\n" +
                        "            \"multiplicityArguments\": [],\n" +
                        "            \"typeVariableValues\": []\n" +
                        "          },\n" +
                        "          \"multiplicity\": {\n" +
                        "            \"lowerBound\": 1.0,\n" +
                        "            \"upperBound\": 1.0\n" +
                        "          },\n" +
                        "          \"stereotypes\": [],\n" +
                        "          \"taggedValues\": [],\n" +
                        "          \"sourceInformation\": {\n" +
                        "            \"sourceId\": \"\",\n" +
                        "            \"startLine\": 4.0,\n" +
                        "            \"startColumn\": 3.0,\n" +
                        "            \"endLine\": 4.0,\n" +
                        "            \"endColumn\": 17.0\n" +
                        "          }\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"qualifiedProperties\": [],\n" +
                        "      \"stereotypes\": [],\n" +
                        "      \"taggedValues\": [],\n" +
                        "      \"constraints\": [],\n" +
                        "      \"package\": \"abc\"\n" +
                        "    },\n" +
                        "    \"location\": {\n" +
                        "      \"documentId\": \"\",\n" +
                        "      \"textInterval\": {\n" +
                        "        \"start\": {\n" +
                        "          \"line\": 1,\n" +
                        "          \"column\": 0\n" +
                        "        },\n" +
                        "        \"end\": {\n" +
                        "          \"line\": 4,\n" +
                        "          \"column\": 0\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }\n" +
                        "  }\n" +
                        "]",
                jsonPerFileAndPath,
                jsonAssertConfig
        );

        List<LegendEntity> entitiesPerFileAndPathNoMatch = extension.futureGet(extension.getServer().getLegendLanguageService().entities(
                        new LegendEntitiesRequest(
                                List.of(
                                        new TextDocumentIdentifier(file1Path.toUri().toString())
                                ),
                                List.of("xyz::abc")
                        )
                )
        );

        Assertions.assertEquals(0, entitiesPerFileAndPathNoMatch.size());
        entitiesPerFileAndPathNoMatch.sort(Comparator.comparing(LegendEntity::getPath));
        String jsonPerFileAndPathNoMatch = new Gson().toJson(entitiesPerFileAndPathNoMatch);

        JsonAssert.assertJsonEquals(
                "[]",
                jsonPerFileAndPathNoMatch,
                jsonAssertConfig
        );
    }

    @Test
    void getDeclarationReferences() throws Exception
    {

        Path modelPath = extension.addToWorkspace("LegalEntity.pure",
                "###Pure\n" +
                        "Class showcase::model::LegalEntity\n" +
                        "{\n" +
                        "  id: String[1];\n" +
                        "  legalName: String[1];\n" +
                        "  businessDate: Date[1];\n" +
                        "}\n" +
                        "Class showcase::model::LegalEntitySrc\n" +
                        "{\n" +
                        "  id: String[1];\n" +
                        "  legalName: String[1];\n" +
                        "  businessDate: Date[1];\n" +
                        "}"
        );

        Path mappingPath = extension.addToWorkspace("mapping.pure",
                "###Mapping\n" +
                        "Mapping showcase::model::mapping\n" +
                        "(\n" +
                        "   showcase::model::LegalEntity : Pure\n" +
                        "   {\n" +
                        "      ~src showcase::model::LegalEntitySrc\n" +
                        "      id : '123',\n" +
                        "      legalName : $src.legalName,\n" +
                        "      businessDate : $src.businessDate\n" +
                        "   }\n" +
                        ")");

        Path funcPath = extension.addToWorkspace("myfunc.pure",
                "###Pure\n" +
                        "function showcase::model::myfunc(businessDate: Date[1]): meta::pure::tds::TabularDataSet[1]\n" +
                        "{\n" +
                        "  showcase::model::LegalEntity.all($businessDate)->project(\n" +
                        "    [\n" +
                        "      x|$x.id,\n" +
                        "      x|$x.legalName\n" +
                        "    ],\n" +
                        "    [\n" +
                        "      'Id',\n" +
                        "      'Legal Name'\n" +
                        "    ]\n" +
                        "  )->distinct()->take(100);\n" +
                        "}");

        String modelDocumentId = modelPath.toUri().toString();
        String mappingDocumentId = mappingPath.toUri().toString();
        String functionDocumentId = funcPath.toUri().toString();

        this.assertReferences("Usage of class LegalEntity", modelPath, 2, 3, false,
                // reference in class mapping definition
                TextLocation.newTextSource(mappingDocumentId, 3, 3, 3, 30),
                TextLocation.newTextSource(functionDocumentId, 3, 2, 3, 29)
        );

        this.assertReferences("Usage of property LegalEntity.legalName", modelPath, 4, 3, false,
                // reference in class mapping property definition
                TextLocation.newTextSource(mappingDocumentId, 7, 6, 7, 14),
                // usage in function expression
                TextLocation.newTextSource(functionDocumentId, 6, 11, 6, 19)
        );

        this.assertReferences("Usage of property LegalEntity.id (without declaration)", modelPath, 3, 3, false,
                // reference in class mapping property definition
                TextLocation.newTextSource(mappingDocumentId, 6, 6, 6, 7),
                // usage in function expression
                TextLocation.newTextSource(functionDocumentId, 5, 11, 5, 12)
        );

        this.assertReferences("Usage of property LegalEntity.id (with declaration)", modelPath, 3, 3, true,
                // reference in class mapping property definition
                TextLocation.newTextSource(mappingDocumentId, 6, 6, 6, 7),
                // usage in function expression
                TextLocation.newTextSource(functionDocumentId, 5, 11, 5, 12),
                // the declaration of the property
                TextLocation.newTextSource(modelDocumentId, 3, 2, 3, 15)
        );

        this.assertReferences("Usage of class LegalEntitySrc", modelPath, 8, 3, false,
                // reference in the Pure class mapping ~src
                TextLocation.newTextSource(mappingDocumentId, 5, 11, 5, 41)
        );

        this.assertReferences("Usage of class LegalEntitySrc.businessDate", modelPath, 11, 3, false,
                // reference in class mapping property right-side expression
                TextLocation.newTextSource(mappingDocumentId, 8, 26, 8, 37)
        );
    }

    private void assertReferences(String description, Path document, int posLine, int posChar, boolean includeDeclaration, TextLocation... expectedReferences) throws Exception
    {
        Comparator<Location> locationComparator = Comparator.comparing(Location::toString);


        ReferenceParams params = new ReferenceParams(
                new TextDocumentIdentifier(document.toUri().toString()),
                new Position(posLine, posChar),
                new ReferenceContext(includeDeclaration)
        );
        List<? extends Location> locations = extension.futureGet(extension.getServer().getTextDocumentService().references(params));
        locations.sort(locationComparator);

        List<Location> expected = Stream.of(expectedReferences).map(x -> new Location(x.getDocumentId(), LegendToLSPUtilities.toRange(x.getTextInterval())))
                .sorted(locationComparator)
                .collect(Collectors.toList());

        Assertions.assertEquals(expected, locations, description);
    }
}
