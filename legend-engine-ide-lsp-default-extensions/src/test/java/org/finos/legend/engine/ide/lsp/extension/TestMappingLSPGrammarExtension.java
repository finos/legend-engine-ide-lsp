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

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Kind;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Source;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestMappingLSPGrammarExtension extends AbstractLSPGrammarExtensionTest<MappingLSPGrammarExtension>
{
    @Test
    public void testGetName()
    {
        testGetName("Mapping");
    }

    @Test
    public void testGetKeywords()
    {
        MutableSet<String> missingKeywords = Sets.mutable.with("AggregationAware", "AggregateSpecification", "EnumerationMapping", "include", "Mapping", "MappingTests", "Operation", "Pure", "Relational", "XStore");
        this.extension.getKeywords().forEach(missingKeywords::remove);
        Assertions.assertEquals(Sets.mutable.empty(), missingKeywords);
    }

    @Test
    public void testCompletion()
    {
        String code = "\n" +
                "###Mapping\n" +
                "\n" +
                "Mapping test::mapping::TestMapping\n" +
                "(\n" +
                "~mainTable [package::path::storeName]schemaName.TableName1\n" +
                " )\n";
        String boilerPlate = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(2, 0)).iterator().next().getDescription();
        Assertions.assertEquals("Mapping boilerplate", boilerPlate);

        Iterable<? extends LegendCompletion> noCompletion = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(2, 1));
        Assertions.assertFalse(noCompletion.iterator().hasNext());

        String storeObjectSuggestion = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(5, 1)).iterator().next().getDescription();
        Assertions.assertEquals("Store object type", storeObjectSuggestion);
    }

    @Test
    public void testGetDeclarations()
    {
        testGetDeclarations(
                "###Mapping\n" +
                        "\r\n" +
                        "\n" +
                        "Mapping test::mapping::TestMapping\n" +
                        "(\r\n" +
                        "   )\n",
                LegendDeclaration.builder().withIdentifier("test::mapping::TestMapping").withClassifier("meta::pure::mapping::Mapping").withLocation(3, 0, 5, 3).build()
        );
    }


    @Test
    public void testDiagnostics_parserError()
    {
        testDiagnostics(
                "###Mapping\n" +
                        "Mapping vscodelsp::test::EmployeeMapping\n" +
                        "(\n" +
                        "   Employee[emp] : Relational\n" +
                        "   {\n" +
                        "      hireDate   [EmployeeDatabase]EmployeeTable.hireDate,\n" +
                        "      hireType : [EmployeeDatabase]EmployeeTable.hireType\n" +
                        "   }\n" +
                        ")",
                LegendDiagnostic.newDiagnostic(TextInterval.newInterval(5, 35, 5, 47), "Unexpected token 'EmployeeTable'. Valid alternatives: ['(', ':']", Kind.Error, Source.Parser)
        );
    }

    @Test
    public void testDiagnostics_compilerError()
    {
        testDiagnostics(
                "###Mapping\n" +
                        "Mapping vscodelsp::test::EmployeeMapping\n" +
                        "(\n" +
                        "   Employee[emp] : Relational\n" +
                        "   {\n" +
                        "      hireDate : [EmployeeDatabase]EmployeeTable.hireDate,\n" +
                        "      hireType : [EmployeeDatabase]EmployeeTable.hireType\n" +
                        "   }\n" +
                        ")",
                LegendDiagnostic.newDiagnostic(TextInterval.newInterval(3, 3, 3, 10), "Can't find class 'Employee'", Kind.Error, Source.Compiler)
        );
    }

    @Test
    public void testDiagnostics_multipleFiles_compilerError()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("vscodelsp::test::Employee", "###Pure\n" +
                "Class vscodelsp::test::Employee\n" +
                "{\n" +
                "    foobar: Float[1];\n" +
                "    hireDate : Date[1];\n" +
                "    hireType : String[1];\n" +
                "}");
        codeFiles.put("vscodelsp::test::EmployeeMapping", "###Mapping\n" +
                "Mapping vscodelsp::test::EmployeeMapping\n" +
                "(\n" +
                "   Employee[emp] : Relational\n" +
                "   {\n" +
                "      hireDate : [EmployeeDatabase]EmployeeTable.hireDate,\n" +
                "      hireType : [EmployeeDatabase]EmployeeTable.hireType\n" +
                "   }\n" +
                ")");
        testDiagnostics(codeFiles, "vscodelsp::test::EmployeeMapping", LegendDiagnostic.newDiagnostic(TextInterval.newInterval(3, 3, 3, 10), "Can't find class 'Employee'", Kind.Error, Source.Compiler));
    }

    @Override
    protected MappingLSPGrammarExtension newExtension()
    {
        return new MappingLSPGrammarExtension();
    }
}
