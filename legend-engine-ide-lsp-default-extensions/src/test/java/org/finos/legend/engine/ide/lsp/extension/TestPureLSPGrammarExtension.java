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
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Kind;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Source;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestPureLSPGrammarExtension extends AbstractLSPGrammarExtensionTest
{
    @Test
    public void testGetName()
    {
        testGetName("Pure");
    }

    @Test
    public void testGetDeclarations()
    {
        testGetDeclarations(
                "Class test::model::TestClass1\n" +
                        "{\r\n" +
                        "}\n" +
                        "\n" +
                        "\r\n" +
                        "Enum test::model::TestEnumeration\n" +
                        "{\n" +
                        "  VAL1, VAL2,\n" +
                        "  VAL3, VAL4\r\n" +
                        "}\n" +
                        "\r\n" +
                        "Profile test::model::TestProfile\n" +
                        "{\n" +
                        "  stereotypes: [st1, st2];\n" +
                        "  tags: [tag1, tag2, tag3];\n" +
                        "}\n" +
                        "\r\n" +
                        "Class test::model::TestClass2\n" +
                        "{\n" +
                        "   name : String[1];\n" +
                        "   type : test::model::TestEnumeration[1];\n" +
                        "}\n" +
                        "\r\n" +
                        "Association test::model::TestAssociation\n" +
                        "{\n" +
                        "   oneToTwo : test::model::TestClass2[*];\n" +
                        "   twoToOne : test::model::TestClass1[*];\n" +
                        "}\n",
                LegendDeclaration.builder().withIdentifier("test::model::TestClass1").withClassifier(M3Paths.Class).withLocation(0, 0, 2, 0).build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestEnumeration").withClassifier(M3Paths.Enumeration).withLocation(5, 0, 9, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL1").withClassifier("test::model::TestEnumeration").withLocation(7, 2, 7, 5).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL2").withClassifier("test::model::TestEnumeration").withLocation(7, 8, 7, 11).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL3").withClassifier("test::model::TestEnumeration").withLocation(8, 2, 8, 5).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL4").withClassifier("test::model::TestEnumeration").withLocation(8, 8, 8, 11).build())
                        .build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestProfile").withClassifier(M3Paths.Profile).withLocation(11, 0, 15, 0).build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestClass2").withClassifier(M3Paths.Class).withLocation(17, 0, 21, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("name").withClassifier(M3Paths.Property).withLocation(19, 3, 19, 19).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("type").withClassifier(M3Paths.Property).withLocation(20, 3, 20, 41).build())
                        .build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestAssociation").withClassifier(M3Paths.Association).withLocation(23, 0, 27, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("oneToTwo").withClassifier(M3Paths.Property).withLocation(25, 3, 25, 40).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("twoToOne").withClassifier(M3Paths.Property).withLocation(26, 3, 26, 40).build())
                        .build()
        );

        testGetDeclarations(
                "###Pure\n" +
                        "\r\n" +
                        "Class test::model::TestClass1\n" +
                        "{\r\n" +
                        "}\n" +
                        "\n" +
                        "\r\n" +
                        "Enum test::model::TestEnumeration\n" +
                        "{\n" +
                        "  VAL1, VAL2,\n" +
                        "  VAL3, VAL4\r\n" +
                        "}\n" +
                        "\r\n" +
                        "Profile test::model::TestProfile\n" +
                        "{\n" +
                        "  stereotypes: [st1, st2];\n" +
                        "  tags: [tag1, tag2, tag3];\n" +
                        "}\n" +
                        "\r\n" +
                        "Class test::model::TestClass2\n" +
                        "{\n" +
                        "   name : String[1];\n" +
                        "   type : test::model::TestEnumeration[1];\n" +
                        "}\n" +
                        "\r\n" +
                        "Association test::model::TestAssociation\n" +
                        "{\n" +
                        "   oneToTwo : test::model::TestClass2[*];\n" +
                        "   twoToOne : test::model::TestClass1[*];\n" +
                        "}\n",
                LegendDeclaration.builder().withIdentifier("test::model::TestClass1").withClassifier(M3Paths.Class).withLocation(2, 0, 4, 0).build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestEnumeration").withClassifier(M3Paths.Enumeration).withLocation(7, 0, 11, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL1").withClassifier("test::model::TestEnumeration").withLocation(9, 2, 9, 5).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL2").withClassifier("test::model::TestEnumeration").withLocation(9, 8, 9, 11).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL3").withClassifier("test::model::TestEnumeration").withLocation(10, 2, 10, 5).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL4").withClassifier("test::model::TestEnumeration").withLocation(10, 8, 10, 11).build())
                        .build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestProfile").withClassifier(M3Paths.Profile).withLocation(13, 0, 17, 0).build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestClass2").withClassifier(M3Paths.Class).withLocation(19, 0, 23, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("name").withClassifier(M3Paths.Property).withLocation(21, 3, 21, 19).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("type").withClassifier(M3Paths.Property).withLocation(22, 3, 22, 41).build())
                        .build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestAssociation").withClassifier(M3Paths.Association).withLocation(25, 0, 29, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("oneToTwo").withClassifier(M3Paths.Property).withLocation(27, 3, 27, 40).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("twoToOne").withClassifier(M3Paths.Property).withLocation(28, 3, 28, 40).build())
                        .build()
        );
    }

    @Test
    public void testDiagnostics_parserError()
    {
        testDiagnostics(
                "###Pure\n" +
                        "Class vscodelsp::test::Employee\n" +
                        "{\n" +
                        "             foobar Float[1];\n" +
                        "    hireDate : Date[1];\n" +
                        "    hireType : String[1];\n" +
                        "}",
                LegendDiagnostic.newDiagnostic(TextInterval.newInterval(3, 20, 3, 24), "no viable alternative at input 'foobarFloat'", Kind.Error, Source.Parser)
        );
    }

    @Test
    public void testDiagnostics_compilerError()
    {
        testDiagnostics(
                "###Pure\n" +
                        "Class vscodelsp::test::Employee\n" +
                        "{\n" +
                        "    foobar: Float[1];\n" +
                        "    hireDate : Date[1];\n" +
                        "    hireType : vscodelsp::test::HireType[1];\n" +
                        "}",
                LegendDiagnostic.newDiagnostic(TextInterval.newInterval(5, 15, 5, 39), "Can't find type 'vscodelsp::test::HireType'", Kind.Error, Source.Compiler)
        );
    }

    @Test
    public void testDiagnostics_compilerWarning()
    {
        testDiagnostics(
                "###Pure\n" +
                        "Class vscodelsp::test::Employee\n" +
                        "{\n" +
                        "    foobar: Float[1];\n" +
                        "    foobar: Float[1];\n" +
                        "}",
                LegendDiagnostic.newDiagnostic(TextInterval.newInterval(3, 4, 3, 20), "Found duplicated property 'foobar' in class 'vscodelsp::test::Employee'", Kind.Warning, Source.Compiler)
        );
    }

    @Test
    public void testDiagnostics_noError()
    {
        testDiagnostics(
                "###Pure\n" +
                        "Class vscodelsp::test::Employee\n" +
                        "{\n" +
                        "    foobar: Float[1];\n" +
                        "    hireDate : Date[1];\n" +
                        "    hireType : String[1];\n" +
                        "}"
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
        codeFiles.put("vscodelsp::test::Employee1", "###Pure\n" +
                "Class vscodelsp::test::Employee1 extends vscodelsp::test::Employee2\n" +
                "{\n" +
                "    foobar: Float[1];\n" +
                "    hireDate : Date[1];\n" +
                "    hireType : String[1];\n" +
                "}");
        testDiagnostics(codeFiles, "vscodelsp::test::Employee1", LegendDiagnostic.newDiagnostic(TextInterval.newInterval(1, 0, 6, 0), "Can't find type 'vscodelsp::test::Employee2'", Kind.Error, Source.Compiler));
    }

    @Test
    public void testCompletion()
    {
        String code = "###Pure\n" +
                "Class vscodelsp::test::Employee\n" +
                "{\n" +
                "foobar1: Float [1];\n" +
                "foobar2: Float [1];\n" +
                "}";

        String boilerPlate = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(2, 0)).iterator().next().getDescription();
        Assertions.assertEquals("Pure boilerplate", boilerPlate);

        Iterable<? extends LegendCompletion> noCompletion = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(2, 1));
        Assertions.assertEquals(List.of(), noCompletion);

        String attributeTypesSuggestion = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(3, 9)).iterator().next().getDescription();
        Assertions.assertEquals("Attribute type", attributeTypesSuggestion);

        String attributeMultiplicitiesSuggestion = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(3, 15)).iterator().next().getDescription();
        Assertions.assertEquals("Attribute multiplicity", attributeMultiplicitiesSuggestion);
    }

    @Override
    protected PureLSPGrammarExtension newExtension()
    {
        return new PureLSPGrammarExtension();
    }
}
