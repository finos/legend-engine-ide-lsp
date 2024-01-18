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

import java.util.Set;

import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Kind;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Source;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.junit.jupiter.api.Assertions;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.junit.jupiter.api.Test;

public class TestServiceLSPGrammarExtension extends AbstractLSPGrammarExtensionTest<ServiceLSPGrammarExtension>
{
    @Test
    public void testGetName()
    {
        testGetName("Service");
    }

    @Test
    public void testGetDeclarations()
    {
        testGetDeclarations(
                "###Service\n" +
                        "\r\n" +
                        "\n" +
                        "Service test::services::TestService\n" +
                        "{\r\n" +
                        "    pattern : 'test';\n" +
                        "    documentation : 'service for testing';\r\n" +
                        "    execution : Single\n" +
                        "    {\n" +
                        "        query : src:test::model::TestClass[1] | $src.name;\n" +
                        "        mapping : test::mappings::TestMapping;\n" +
                        "        runtime : test::runtimes::TestRuntime;\r\n" +
                        "    }\n" +
                        "    test : Single" +
                        "    {\n" +
                        "        data : '';\n" +
                        "        asserts : [];\n" +
                        "    }\r\n" +
                        "}\n",
                LegendDeclaration.builder().withIdentifier("test::services::TestService").withClassifier("meta::legend::service::metamodel::Service").withLocation(3, 0, 17, 0).build()
        );
    }


    @Test
    public void testDiagnostics_parserError()
    {
        testDiagnostics(
                "###Service\n" +
                        "\r\n" +
                        "\n" +
                        "Service test::services::TestService\n" +
                        "{\r\n" +
                        "    pattern 'test';\n" +
                        "    documentation : 'service for testing';\r\n" +
                        "    execution : Single\n" +
                        "    {\n" +
                        "        query : src:test::model::TestClass[1] | $src.name;\n" +
                        "        mapping : test::mappings::TestMapping;\n" +
                        "        runtime : test::runtimes::TestRuntime;\r\n" +
                        "    }\n" +
                        "    test : Single" +
                        "    {\n" +
                        "        data : '';\n" +
                        "        asserts : [];\n" +
                        "    }\r\n" +
                        "}\n",
                LegendDiagnostic.newDiagnostic(TextInterval.newInterval(5, 12, 5, 17), "Unexpected token", Kind.Error, Source.Parser)
        );
    }

    @Test
    public void testDiagnostics_compilerError()
    {
        testDiagnostics(
                "###Service\n" +
                        "\r\n" +
                        "\n" +
                        "Service test::services::TestService\n" +
                        "{\r\n" +
                        "    pattern : 'test';\n" +
                        "    documentation : 'service for testing';\r\n" +
                        "    execution : Single\n" +
                        "    {\n" +
                        "        query : src:test::model::TestClass[1] | $src.name;\n" +
                        "        mapping : test::mappings::TestMapping;\n" +
                        "        runtime : test::runtimes::TestRuntime;\r\n" +
                        "    }\n" +
                        "    test : Single" +
                        "    {\n" +
                        "        data : '';\n" +
                        "        asserts : [];\n" +
                        "    }\r\n" +
                        "}\n",
                LegendDiagnostic.newDiagnostic(TextInterval.newInterval(10, 18, 10, 44), "Can't find mapping 'test::mappings::TestMapping'", Kind.Error, Source.Compiler)
        );
    }

    @Test
    public void testCompletion()
    {
        String code = "###Service\n" +
                "Service package::path::serviceName\n" +
                "\n";

        Iterable<? extends LegendCompletion> noCompletion = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(0, 1));
        Assertions.assertFalse(noCompletion.iterator().hasNext());

        String boilerPlate = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(1, 0)).iterator().next().getDescription();
        Assertions.assertEquals("Service boilerplate", boilerPlate);
    }

    @Test
    void testAntlrExpectedTokens()
    {
        Set<String> antlrExpectedTokens = this.extension.getAntlrExpectedTokens();
        Assertions.assertEquals(Set.of("Service", "ExecutionEnvironment"), antlrExpectedTokens);
    }

    @Override
    protected ServiceLSPGrammarExtension newExtension()
    {
        return new ServiceLSPGrammarExtension();
    }
}
