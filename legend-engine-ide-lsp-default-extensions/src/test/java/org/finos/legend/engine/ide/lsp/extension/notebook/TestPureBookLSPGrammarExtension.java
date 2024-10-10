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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.finos.legend.engine.ide.lsp.extension.StateForTestFactory;
import org.finos.legend.engine.ide.lsp.extension.core.FunctionExecutionSupport;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
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
        SectionState goodSection = stateForTestFactory.newSectionState("good.purebook", "1 + 1", "purebook");
        Iterable<? extends LegendDiagnostic> noDiagnostics = this.extension.getDiagnostics(goodSection);

        Assertions.assertEquals(
                List.of(),
                noDiagnostics
        );

        SectionState parseFailure = stateForTestFactory.newSectionState("parse_problem.purebook", "1 +", "purebook");
        Iterable<? extends LegendDiagnostic> parseDiagnostics = this.extension.getDiagnostics(parseFailure);

        Assertions.assertEquals(
                List.of(LegendDiagnostic.newDiagnostic(TextLocation.newTextSource("parse_problem.purebook", 0, 2, 0, 2), "Unexpected token", LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Parser)),
                parseDiagnostics
        );

        SectionState compileFailure = stateForTestFactory.newSectionState("compile_problem.purebook", "does::not::exists()", "purebook");
        Iterable<? extends LegendDiagnostic> compileDiagnostics = this.extension.getDiagnostics(compileFailure);

        Assertions.assertEquals(
                List.of(LegendDiagnostic.newDiagnostic(TextLocation.newTextSource("compile_problem.purebook", 0, 0, 0, 16), "Can't resolve the builder for function 'does::not::exists' - stack:[build Lambda, new lambda, Applying does::not::exists]", LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Compiler)),
                compileDiagnostics
        );
    }

    @Test
    void references()
    {
        SectionState pureCode = stateForTestFactory.newSectionState("func.pure", "function hello::world():Any[1]{ 1 + 1 }");
        SectionState notebook = stateForTestFactory.newSectionState(pureCode.getDocumentState().getGlobalState(), "notebook.purebook", "hello::world()", "purebook");

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
        SectionState notebook = stateForTestFactory.newSectionState(pureCode.getDocumentState().getGlobalState(), "notebook.purebook", "hello::world()", "purebook");
        Assertions.assertEquals(
                List.of(FunctionExecutionSupport.FunctionLegendExecutionResult.newResult("notebook_cell", LegendExecutionResult.Type.SUCCESS, "2", null, notebook.getDocumentState().getDocumentId(), 0, Map.of())),
                this.extension.execute(notebook, "notebook", "executeCell", Map.of())
        );

        SectionState emptyNotebook = stateForTestFactory.newSectionState("notebook.purebook", "", "purebook");
        Assertions.assertEquals(
                List.of(FunctionExecutionSupport.FunctionLegendExecutionResult.newResult("notebook_cell", LegendExecutionResult.Type.SUCCESS, "[]", "Nothing to execute!", emptyNotebook.getDocumentState().getDocumentId(), 0, Map.of())),
                this.extension.execute(emptyNotebook, "notebook", "executeCell", Map.of())
        );

        SectionState cannotCompileNotebook = stateForTestFactory.newSectionState("notebook.purebook", "1 + 1 +", "purebook");
        LegendExecutionResult compileFailure = this.extension.execute(cannotCompileNotebook, "notebook", "executeCell", Map.of()).iterator().next();
        Assertions.assertEquals(
                LegendExecutionResult.Type.ERROR,
                compileFailure.getType()
        );
        Assertions.assertEquals(
                "Cannot execute since cell does not parse or compile.  Check diagnostics for further details...",
                compileFailure.getMessage()
        );

        SectionState failExecNotebook = stateForTestFactory.newSectionState(pureCode.getDocumentState().getGlobalState(), "notebook.purebook", "let a = hello::world();\nlet b = hello::world();", "purebook");
        LegendExecutionResult planGenFailure = this.extension.execute(failExecNotebook, "notebook", "executeCell", Map.of()).iterator().next();
        Assertions.assertEquals(
                LegendExecutionResult.Type.ERROR,
                planGenFailure.getType()
        );
        Assertions.assertEquals(
                "Cannot generate an execution plan for given expression.  Likely the expression is not supported yet...",
                planGenFailure.getMessage()
        );
    }
}
