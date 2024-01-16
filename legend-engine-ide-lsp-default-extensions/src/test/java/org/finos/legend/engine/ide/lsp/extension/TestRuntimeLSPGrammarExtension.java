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

package org.finos.legend.engine.ide.lsp.extension;

import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.junit.jupiter.api.Test;

public class TestRuntimeLSPGrammarExtension extends AbstractLSPGrammarExtensionTest<RuntimeLSPGrammarExtension>
{
    @Test
    public void testGetName()
    {
        testGetName("Runtime");
    }

    @Test
    public void testGetDeclarations()
    {
        testGetDeclarations(
                "###Runtime\n" +
                        "\n" +
                        "Runtime test::runtime::TestRuntime\n" +
                        "{\r\n" +
                        "    mappings: [];\r\n" +
                        "    connections: [];\n" +
                        " }\n",
                LegendDeclaration.builder().withIdentifier("test::runtime::TestRuntime").withClassifier("meta::pure::runtime::PackageableRuntime").withLocation(2, 0, 6, 1).build()
        );
    }

    @Test
    public void testDiagnostics_parserError()
    {
        testDiagnostics(
                "###Runtime\n" +
                        "\n" +
                        "Runtime test::runtime::TestRuntime\n" +
                        "{\r\n" +
                        "    mappings: [;\r\n" +
                        "    connections: [];\n" +
                        " }\n",
                LegendDiagnostic.newDiagnostic(TextInterval.newInterval(4, 15, 4, 15), "Unexpected token", LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Parser)
        );
    }

    @Test
    public void testDiagnostics_compilerWarning()
    {
        testDiagnostics(
                "###Runtime\n" +
                "\n" +
                "Runtime test::runtime::TestRuntime\n" +
                "{\r\n" +
                "    mappings: [];\r\n" +
                "    connections: [];\n" +
                " }\n",
                LegendDiagnostic.newDiagnostic(TextInterval.newInterval(2, 0, 6, 1), "Runtime must cover at least one mapping", LegendDiagnostic.Kind.Warning, LegendDiagnostic.Source.Compiler)
        );
    }

    @Override
    protected RuntimeLSPGrammarExtension newExtension()
    {
        return new RuntimeLSPGrammarExtension();
    }
}
