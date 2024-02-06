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

package org.finos.legend.engine.ide.lsp.extension.sdlc;

import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtensionTest;
import org.finos.legend.engine.ide.lsp.extension.core.PureLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.junit.jupiter.api.Test;

public class TestLegendDependencyManagement extends AbstractLSPGrammarExtensionTest<PureLSPGrammarExtension>
{
    @Test
    void testDependenciesDiscovered()
    {
        String codeThatDepends = "###Pure\n" +
                "function vscodelsp::test::functionRefersDependency(): Any[*]\n" +
                "{\n" +
                "    vscodelsp::test::dependency::Employee.all();\n" +
                "}";

        // no dependency, compile problem
        testDiagnostics(codeThatDepends,
                LegendDiagnostic.newDiagnostic(
                        TextLocation.newTextSource("file.pure", 3, 4, 3, 40),
                        "Can't find the packageable element 'vscodelsp::test::dependency::Employee'",
                        LegendDiagnostic.Kind.Error,
                        LegendDiagnostic.Source.Compiler
                )
        );

        // register feature to discover and process dependencies
        this.registerFeature(new LegendDependencyManagementImpl());
        // same code as before, it compiles
        testDiagnostics(codeThatDepends);
    }
}
