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

public class TestMappingLSPGrammarExtension extends AbstractLSPGrammarExtensionTest
{
    @Test
    public void testGetName()
    {
        testGetName("Mapping");
    }

    @Test
    public void testGetDeclarations()
    {
        testGetDeclarations("###Mapping\n" +
                        "\r\n" +
                        "\n" +
                        "Mapping test::mapping::TestMapping\n" +
                        "(\r\n" +
                        "   )\n",
                LegendDeclaration.builder().withIdentifier("test::mapping::TestMapping").withClassifier("meta::pure::mapping::Mapping").withLocation(3, 0, 5, 3).build()
        );
    }


    @Test
    public void testMappingParsingError()
    {
        String code = "###Mapping\n" +
            "Mapping vscodelsp::test::EmployeeMapping\n" +
                "(\n" +
                "   Employee[emp] : Relational\n" +
                "   {\n" +
                "      hireDate   [EmployeeDatabase]EmployeeTable.hireDate,\n" +
                "      hireType : [EmployeeDatabase]EmployeeTable.hireType\n" +
                "   }\n" +
                ")";
        LegendDiagnostic expectedDiagnostics = LegendDiagnostic.newDiagnostic(TextInterval.newInterval(3, 20, 3, 25), "no viable alternative at input 'foobarFloat'", LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Parser);
        this.testDiagnostics(code, expectedDiagnostics);
    }

    @Test
    public void testMappingParsingNoError()
    {
        String code = "###Mapping\n"+
                "Mapping vscodelsp::test::EmployeeMapping\n" +
                "(\n" +
                "   Employee[emp] : Relational\n" +
                "   {\n" +
                "      hireDate : [EmployeeDatabase]EmployeeTable.hireDate,\n" +
                "      hireType : [EmployeeDatabase]EmployeeTable.hireType\n" +
                "   }\n" +
                ")";

        testDiagnostics(code);
    }

    @Test
    public void testMappingParsingNoErrorEmptyCode()
    {
        String code = "###Mapping";
        testDiagnostics(code);
    }

    @Test
    public void testMappingParsingNoErrorEmptyFile()
    {
        testDiagnostics("");
    }

    @Override
    protected LegendLSPGrammarExtension newExtension()
    {
        return new MappingLSPGrammarExtension();
    }
}
