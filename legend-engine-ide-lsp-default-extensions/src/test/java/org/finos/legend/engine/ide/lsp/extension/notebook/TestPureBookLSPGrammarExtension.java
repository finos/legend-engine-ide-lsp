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

package org.finos.legend.engine.ide.lsp.extension.notebook;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.ide.lsp.extension.StateForTestFactory;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.core.FunctionExecutionSupport;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.CancellationToken;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TestPureBookLSPGrammarExtension
{
    private static StateForTestFactory stateForTestFactory;
    private final PureBookLSPGrammarExtension extension = new PureBookLSPGrammarExtension();

    @BeforeEach
    public void loadExtensionToUse()
    {
        this.extension.startup(stateForTestFactory.newGlobalState());
    }

    @BeforeAll
    static void beforeAll()
    {
        stateForTestFactory = new StateForTestFactory();
    }

    @Test
    public void testGetName()
    {
        Assertions.assertEquals("purebook", extension.getName());
    }

    @Test
    void diagnostics()
    {
        SectionState goodSection = stateForTestFactory.newPureBookSectionState("good.purebook", "1 + 1");
        Iterable<? extends LegendDiagnostic> noDiagnostics = this.extension.getDiagnostics(goodSection);

        Assertions.assertEquals(
                List.of(),
                noDiagnostics
        );

        SectionState parseFailure = stateForTestFactory.newPureBookSectionState("parse_problem.purebook", "1 +");
        Iterable<? extends LegendDiagnostic> parseDiagnostics = this.extension.getDiagnostics(parseFailure);

        Assertions.assertEquals(
                List.of(LegendDiagnostic.newDiagnostic(TextLocation.newTextSource("parse_problem.purebook", 0, 2, 0, 2), "Unexpected token", LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Parser)),
                parseDiagnostics
        );

        SectionState compileFailure = stateForTestFactory.newPureBookSectionState("compile_problem.purebook", "does::not::exists()");
        Iterable<? extends LegendDiagnostic> compileDiagnostics = this.extension.getDiagnostics(compileFailure);

        Assertions.assertEquals(
                List.of(LegendDiagnostic.newDiagnostic(TextLocation.newTextSource("compile_problem.purebook", 0, 0, 0, 16), "Can't resolve the builder for function 'does::not::exists' - stack:[build Lambda, new lambda, Applying does::not::exists]", LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Compiler)),
                compileDiagnostics
        );

        SectionState wrongUsageOfPureBook = stateForTestFactory.newSectionState("wrongUsageOfPureBook.pure", "###purebook\n1 + 1");
        Iterable<? extends LegendDiagnostic> wrongUsageOfPureBookDiagnostic = this.extension.getDiagnostics(wrongUsageOfPureBook);

        Assertions.assertEquals(
                List.of(LegendDiagnostic.newDiagnostic(TextLocation.newTextSource("wrongUsageOfPureBook.pure", 0, 0, 1, 5), "###purebook should not be use outside of Purebooks", LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Parser)),
                wrongUsageOfPureBookDiagnostic
        );
    }

    @Test
    void references()
    {
        SectionState pureCode = stateForTestFactory.newSectionState("func.pure", "function hello::world():Any[1]{ 1 + 1 }");
        SectionState notebook = stateForTestFactory.newPureBookSectionState(pureCode.getDocumentState().getGlobalState(), "notebook.purebook", "hello::world()");

        List<LegendReference> references = this.extension.getLegendReferences(notebook).collect(Collectors.toList());

        Assertions.assertEquals(
                List.of(LegendReference.builder()
                        .withLocation("notebook.purebook", 0, 0, 0, 11)
                        .withDeclarationLocation(TextLocation.newTextSource("func.pure", 0, 0, 0, 38))
                        .build()
                ),
                references
        );
    }

    @Test
    void executeCell()
    {
        SectionState pureCode = stateForTestFactory.newSectionState("func.pure", "function hello::world():Any[1]{ 1 + 1 }");
        SectionState notebook = stateForTestFactory.newPureBookSectionState(pureCode.getDocumentState().getGlobalState(), "notebook.purebook", "hello::world()");
        Assertions.assertEquals(
                List.of(FunctionExecutionSupport.FunctionLegendExecutionResult.newResult("notebook_cell", LegendExecutionResult.Type.SUCCESS, "2", null, notebook.getDocumentState().getDocumentId(), 0, Map.of())),
                this.extension.execute(notebook, "notebook", "executeCell", Map.of(), Map.of(), pureCode.getDocumentState().getGlobalState().cancellationToken("test"))
        );

        // update code
        stateForTestFactory.newSectionState(pureCode.getDocumentState().getGlobalState(), "func.pure", "function hello::world():Any[1]{ 1 + 2 }");

        Assertions.assertEquals(
                List.of(FunctionExecutionSupport.FunctionLegendExecutionResult.newResult("notebook_cell", LegendExecutionResult.Type.SUCCESS, "3", null, notebook.getDocumentState().getDocumentId(), 0, Map.of())),
                this.extension.execute(notebook, "notebook", "executeCell", Map.of(), Map.of(), pureCode.getDocumentState().getGlobalState().cancellationToken("test"))
        );

        SectionState emptyNotebook = stateForTestFactory.newPureBookSectionState("notebook.purebook", "");
        Assertions.assertEquals(
                List.of(FunctionExecutionSupport.FunctionLegendExecutionResult.newResult("notebook_cell", LegendExecutionResult.Type.SUCCESS, "[]", "Nothing to execute!", emptyNotebook.getDocumentState().getDocumentId(), 0, Map.of())),
                this.extension.execute(emptyNotebook, "notebook", "executeCell", Map.of(), Map.of(), pureCode.getDocumentState().getGlobalState().cancellationToken("test"))
        );

        SectionState cannotCompileNotebook = stateForTestFactory.newPureBookSectionState("notebook.purebook", "1 + 1 +");
        LegendExecutionResult compileFailure = this.extension.execute(cannotCompileNotebook, "notebook", "executeCell", Map.of(), Map.of(), pureCode.getDocumentState().getGlobalState().cancellationToken("test")).iterator().next();
        Assertions.assertEquals(
                LegendExecutionResult.Type.ERROR,
                compileFailure.getType()
        );
        Assertions.assertEquals(
                "Cannot execute since cell does not parse or compile.  Check diagnostics for further details...",
                compileFailure.getMessage()
        );

        SectionState failExecNotebook = stateForTestFactory.newPureBookSectionState(pureCode.getDocumentState().getGlobalState(), "notebook.purebook", "let a = hello::world();\nlet b = hello::world();");
        LegendExecutionResult planGenFailure = this.extension.execute(failExecNotebook, "notebook", "executeCell", Map.of(), Map.of(), pureCode.getDocumentState().getGlobalState().cancellationToken("test")).iterator().next();
        Assertions.assertEquals(
                LegendExecutionResult.Type.ERROR,
                planGenFailure.getType()
        );
        Assertions.assertEquals(
                "Cannot generate an execution plan for given expression.  Likely the expression is not supported yet...",
                planGenFailure.getMessage()
        );
    }

    private MutableMap<String, String> getCodeFilesThatParseCompile()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("database.pure",
                "###Relational\n" +
                        "Database test::h2Store\n" +
                        "(\n" +
                        "    Table personTable\n" +
                        "    (\n" +
                        "     fullName VARCHAR(100),\n" +
                        "     firmName VARCHAR(100),\n" +
                        "     addressName VARCHAR(100)\n" +
                        "    )\n" +
                        "    Table anotherPersonTable\n" +
                        "    (\n" +
                        "     fullName VARCHAR(100),\n" +
                        "     firmName VARCHAR(100),\n" +
                        "     addressName VARCHAR(100)\n" +
                        "    )\n" +
                        ")");

        codeFiles.put("connection.pure",
                "###Connection\n" +
                        "RelationalDatabaseConnection test::h2Conn\n" +
                        "{\n" +
                        "    store: test::h2Store; \n" +
                        "    type: H2;\n" +
                        "    specification: LocalH2\n" +
                        "    {\n" +
                        "        testDataSetupCSV: 'default\\npersonTable\\nfullName,firmName,addressName\\nP1,F1,A1\\nP2,F2,A2\\nP3,,\\nP4,,A3\\nP5,F1,A1\\n---';\n" +
                        "    };\n" +
                        "    auth: DefaultH2;\n" +
                        "}");

        codeFiles.put("runtime.pure",
                "###Runtime\n" +
                        "Runtime  test::h2Runtime\n" +
                        "{\n" +
                        "    mappings: [];\n" +
                        "    connectionStores:\n" +
                        "    [\n" +
                        "        test::h2Conn: [ test::h2Store ]\n" +
                        "    ];\n" +
                        "}");
        return codeFiles;
    }

    @Test
    void relationExecuteCellReturnsLambda()
    {
        SectionState notebook = stateForTestFactory.newPureBookSectionState("notebook.purebook", "#>{test::h2Store.personTable}#->select()->from(test::h2Runtime)");
        GlobalState gs = notebook.getDocumentState().getGlobalState();
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();
        stateForTestFactory.newSectionStates(gs, codeFiles);

        String expectedMessage = "{\"_type\":\"lambda\",\"body\":[{\"_type\":\"func\",\"function\":\"from\"," +
                "\"parameters\":[{\"_type\":\"func\",\"function\":\"select\"," +
                "\"parameters\":[{\"_type\":\"classInstance\",\"sourceInformation\":{\"endColumn\":30,\"endLine\":1," +
                "\"sourceId\":\"notebook.purebook\",\"startColumn\":1,\"startLine\":1},\"type\":\">\"," +
                "\"value\":{\"path\":[\"test::h2Store\",\"personTable\"],\"sourceInformation\":{\"endColumn\":30," +
                "\"endLine\":1,\"sourceId\":\"notebook.purebook\",\"startColumn\":1,\"startLine\":1}}}]," +
                "\"sourceInformation\":{\"endColumn\":38,\"endLine\":1,\"sourceId\":\"notebook.purebook\"," +
                "\"startColumn\":33,\"startLine\":1}},{\"_type\":\"packageableElementPtr\"," +
                "\"fullPath\":\"test::h2Runtime\",\"sourceInformation\":{\"endColumn\":62,\"endLine\":1," +
                "\"sourceId\":\"notebook.purebook\",\"startColumn\":48,\"startLine\":1}}]," +
                "\"sourceInformation\":{\"endColumn\":46,\"endLine\":1,\"sourceId\":\"notebook.purebook\"," +
                "\"startColumn\":43,\"startLine\":1}}],\"parameters\":[],\"sourceInformation\":{\"endColumn\":63," +
                "\"endLine\":1,\"sourceId\":\"notebook.purebook\",\"startColumn\":1,\"startLine\":1}}";
        Iterable<? extends LegendExecutionResult> actual = this.extension.execute(notebook, "notebook", "executeCell"
                , Map.of("requestId", "123456"), Map.of(),
                notebook.getDocumentState().getGlobalState().cancellationToken("test"));

        Assertions.assertEquals(1, Iterate.sizeOf(actual));
        LegendExecutionResult result = actual.iterator().next();
        Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, result.getType(), result.getMessage());
        Assertions.assertEquals(expectedMessage, result.getMessage());
    }

    @Test
    void completions()
    {
        SectionState notebook = stateForTestFactory.newPureBookSectionState("notebook.purebook", "#>");
        GlobalState gs = notebook.getDocumentState().getGlobalState();
        stateForTestFactory.newSectionState(gs, "database.pure",
                "###Relational\n" +
                        "Database test::h2Store\n" +
                        "(\n" +
                        "    Table personTable\n" +
                        "    (\n" +
                        "     fullName VARCHAR(100),\n" +
                        "     firmName VARCHAR(100),\n" +
                        "     addressName VARCHAR(100)\n" +
                        "    )\n" +
                        "    Table anotherPersonTable\n" +
                        "    (\n" +
                        "     fullName VARCHAR(100),\n" +
                        "     firmName VARCHAR(100),\n" +
                        "     addressName VARCHAR(100)\n" +
                        "    )\n" +
                        ")");

        stateForTestFactory.newSectionState(gs, "connection.pure",
                "###Connection\n" +
                        "RelationalDatabaseConnection test::h2Conn\n" +
                        "{\n" +
                        "    store: test::h2Store; \n" +
                        "    type: H2;\n" +
                        "    specification: LocalH2\n" +
                        "    {\n" +
                        "        testDataSetupCSV: 'default\\npersonTable\\nfullName,firmName,addressName\\nP1,F1,A1\\nP2,F2,A2\\nP3,,\\nP4,,A3\\nP5,F1,A1\\n---';\n" +
                        "    };\n" +
                        "    auth: DefaultH2;\n" +
                        "}");

        stateForTestFactory.newSectionState(gs, "runtime.pure",
                "###Runtime\n" +
                        "Runtime  test::h2Runtime\n" +
                        "{\n" +
                        "    mappings: [];\n" +
                        "    connectionStores:\n" +
                        "    [\n" +
                        "        test::h2Conn: [ test::h2Store ]\n" +
                        "    ];\n" +
                        "}");

        Iterable<? extends LegendCompletion> storeCompletions = this.extension.getCompletions(notebook, TextPosition.newPosition(0, 2));
        Assertions.assertEquals(
                List.of(new LegendCompletion("test::h2Store", ">{test::h2Store.")),
                storeCompletions
        );

        notebook = stateForTestFactory.newPureBookSectionState(gs, "notebook.purebook", "#>{test::h2Store.");

        Set<LegendCompletion> tableCompletions = new HashSet<>();
        this.extension.getCompletions(notebook, TextPosition.newPosition(0, 17)).forEach(tableCompletions::add);
        Assertions.assertEquals(
                Set.of(new LegendCompletion("personTable", "personTable}#"),
                        new LegendCompletion("anotherPersonTable", "anotherPersonTable}#")
                ),
                tableCompletions
        );
    }

    @Test
    void cancel()
    {
        SectionState notebook = stateForTestFactory.newPureBookSectionState("notebook.purebook", "#>{test::h2Store.personTable}#->select()->from(test::h2Runtime)");
        GlobalState gs = notebook.getDocumentState().getGlobalState();
        stateForTestFactory.newSectionState(gs, "database.pure",
                "###Relational\n" +
                        "Database test::h2Store\n" +
                        "(\n" +
                        "    Table personTable\n" +
                        "    (\n" +
                        "     fullName VARCHAR(100),\n" +
                        "     firmName VARCHAR(100),\n" +
                        "     addressName VARCHAR(100)\n" +
                        "    )\n" +
                        "    Table anotherPersonTable\n" +
                        "    (\n" +
                        "     fullName VARCHAR(100),\n" +
                        "     firmName VARCHAR(100),\n" +
                        "     addressName VARCHAR(100)\n" +
                        "    )\n" +
                        ")");

        stateForTestFactory.newSectionState(gs, "connection.pure",
                "###Connection\n" +
                        "RelationalDatabaseConnection test::h2Conn\n" +
                        "{\n" +
                        "    store: test::h2Store; \n" +
                        "    type: H2;\n" +
                        "    specification: LocalH2\n" +
                        "    {\n" +
                        "        testDataSetupCSV: 'default\\npersonTable\\nfullName,firmName,addressName\\nP1,F1,A1\\nP2,F2,A2\\nP3,,\\nP4,,A3\\nP5,F1,A1\\n---';\n" +
                        "    };\n" +
                        "    auth: DefaultH2;\n" +
                        "}");

        stateForTestFactory.newSectionState(gs, "runtime.pure",
                "###Runtime\n" +
                        "Runtime  test::h2Runtime\n" +
                        "{\n" +
                        "    mappings: [];\n" +
                        "    connectionStores:\n" +
                        "    [\n" +
                        "        test::h2Conn: [ test::h2Store ]\n" +
                        "    ];\n" +
                        "}");

        // it's hard to test the cancel,
        // hence we will trigger the cancellation 1st
        // and when the actual execution request runs should fail as its already canceled.
        CancellationToken requestId = notebook.getDocumentState().getGlobalState().cancellationToken("test");
        requestId.cancel();
        RuntimeException runtimeException = Assertions.assertThrows(RuntimeException.class, () -> this.extension.execute(notebook, "notebook", "executeCell", Map.of(), Map.of(), requestId));
        Assertions.assertTrue(runtimeException.toString().contains("The object is already closed [90007-214]"));
    }
}
