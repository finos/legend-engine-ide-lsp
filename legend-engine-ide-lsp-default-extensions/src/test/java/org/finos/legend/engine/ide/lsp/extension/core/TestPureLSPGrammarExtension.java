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

package org.finos.legend.engine.ide.lsp.extension.core;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtensionTest;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Kind;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Source;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTest;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestAssertionResult;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TestPureLSPGrammarExtension extends AbstractLSPGrammarExtensionTest<PureLSPGrammarExtension>
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
                LegendDeclaration.builder().withIdentifier("test::model::TestClass1").withClassifier(M3Paths.Class).withLocation(DOC_ID_FOR_TEXT, 0, 0, 2, 0).build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestEnumeration").withClassifier(M3Paths.Enumeration).withLocation(DOC_ID_FOR_TEXT,5, 0, 9, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL1").withClassifier("test::model::TestEnumeration").withLocation(DOC_ID_FOR_TEXT,7, 2, 7, 5).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL2").withClassifier("test::model::TestEnumeration").withLocation(DOC_ID_FOR_TEXT,7, 8, 7, 11).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL3").withClassifier("test::model::TestEnumeration").withLocation(DOC_ID_FOR_TEXT,8, 2, 8, 5).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL4").withClassifier("test::model::TestEnumeration").withLocation(DOC_ID_FOR_TEXT,8, 8, 8, 11).build())
                        .build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestProfile").withClassifier(M3Paths.Profile).withLocation(DOC_ID_FOR_TEXT,11, 0, 15, 0).build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestClass2").withClassifier(M3Paths.Class).withLocation(DOC_ID_FOR_TEXT,17, 0, 21, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("name").withClassifier(M3Paths.Property).withLocation(DOC_ID_FOR_TEXT,19, 3, 19, 19).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("type").withClassifier(M3Paths.Property).withLocation(DOC_ID_FOR_TEXT,20, 3, 20, 41).build())
                        .build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestAssociation").withClassifier(M3Paths.Association).withLocation(DOC_ID_FOR_TEXT,23, 0, 27, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("oneToTwo").withClassifier(M3Paths.Property).withLocation(DOC_ID_FOR_TEXT,25, 3, 25, 40).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("twoToOne").withClassifier(M3Paths.Property).withLocation(DOC_ID_FOR_TEXT,26, 3, 26, 40).build())
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
                LegendDeclaration.builder().withIdentifier("test::model::TestClass1").withClassifier(M3Paths.Class).withLocation(DOC_ID_FOR_TEXT,2, 0, 4, 0).build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestEnumeration").withClassifier(M3Paths.Enumeration).withLocation(DOC_ID_FOR_TEXT,7, 0, 11, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL1").withClassifier("test::model::TestEnumeration").withLocation(DOC_ID_FOR_TEXT,9, 2, 9, 5).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL2").withClassifier("test::model::TestEnumeration").withLocation(DOC_ID_FOR_TEXT,9, 8, 9, 11).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL3").withClassifier("test::model::TestEnumeration").withLocation(DOC_ID_FOR_TEXT,10, 2, 10, 5).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("VAL4").withClassifier("test::model::TestEnumeration").withLocation(DOC_ID_FOR_TEXT,10, 8, 10, 11).build())
                        .build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestProfile").withClassifier(M3Paths.Profile).withLocation(DOC_ID_FOR_TEXT,13, 0, 17, 0).build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestClass2").withClassifier(M3Paths.Class).withLocation(DOC_ID_FOR_TEXT,19, 0, 23, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("name").withClassifier(M3Paths.Property).withLocation(DOC_ID_FOR_TEXT,21, 3, 21, 19).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("type").withClassifier(M3Paths.Property).withLocation(DOC_ID_FOR_TEXT,22, 3, 22, 41).build())
                        .build(),
                LegendDeclaration.builder().withIdentifier("test::model::TestAssociation").withClassifier(M3Paths.Association).withLocation(DOC_ID_FOR_TEXT,25, 0, 29, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("oneToTwo").withClassifier(M3Paths.Property).withLocation(DOC_ID_FOR_TEXT,27, 3, 27, 40).build())
                        .withChild(LegendDeclaration.builder().withIdentifier("twoToOne").withClassifier(M3Paths.Property).withLocation(DOC_ID_FOR_TEXT,28, 3, 28, 40).build())
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
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT, 3, 20, 3, 24), "no viable alternative at input 'foobarFloat'", Kind.Error, Source.Parser)
        );
    }

    @Test
    public void testDiagnostics_unknownParserError()
    {
        // missing island close and trailing new line leads to bad source info from Engine ParserErrorListener
        testDiagnostics(
                "function hello::world():Any[1] {#>{db.table}->select()}\n",
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT, 0, 0, 1, 0), "Unexpected token", Kind.Error, Source.Parser)
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
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT,5, 15, 5, 39), "Can't find type 'vscodelsp::test::HireType'", Kind.Error, Source.Compiler)
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
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT,3, 4, 3, 20), "Found duplicated property 'foobar' in class 'vscodelsp::test::Employee'", Kind.Warning, Source.Compiler)
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
        testDiagnostics(codeFiles, "vscodelsp::test::Employee1", LegendDiagnostic.newDiagnostic(TextLocation.newTextSource("vscodelsp::test::Employee1",1, 41, 1, 66), "Can't find type 'vscodelsp::test::Employee2'", Kind.Error, Source.Compiler));
    }

    @Test
    public void testDiagnostics_partial_compilation()
    {
        testDiagnostics(
                "###Pure\n" +
                "Class test::Persond\n" +
                "{\n" +
                "    id: String[1];\n" +
                "}\n" +
                "\n" +
                "Class test::Person2 extends test::Person\n" +
                "{\n" +
                "    id: String[1];\n" +
                "}\n" +
                "\n" +
                "Class test::Person3 extends test::Person\n" +
                "{\n" +
                "    id: String[1];\n" +
                "}\n",
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT, 6, 28, 6, 39), "Can't find type 'test::Person'", Kind.Error, Source.Compiler),
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT, 11, 28, 11, 39), "Can't find type 'test::Person'", Kind.Error, Source.Compiler)
        );
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
        Assertions.assertFalse(noCompletion.iterator().hasNext());

        String attributeTypesSuggestion = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(3, 9)).iterator().next().getDescription();
        Assertions.assertEquals("Attribute type", attributeTypesSuggestion);

        String attributeMultiplicitiesSuggestion = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(3, 15)).iterator().next().getDescription();
        Assertions.assertEquals("Attribute multiplicity", attributeMultiplicitiesSuggestion);
    }

    @Test
    public void testAutoCompletionDotInFilter()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("vscodelsp::test::TestClass",
                "###Pure\n" +
                "Class vscodelsp::test::TestClass\n" +
                "{\n" +
                "   name: String[1];\n" +
                "   other: Integer[1];\n" +
                "}");

        codeFiles.put("vscodelsp::test::TestFunction",
                "###Pure\n" +
                "function vscodelsp::test::func(): Any[*] {\n" +
                "   vscodelsp::test::TestClass.all()->filter(c|$c.\n");

        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        Set<String> expectedCompletions = new HashSet<>(Arrays.asList("name", "other"));
        Set<String> actualCompletions = new HashSet<>();
        Iterable<? extends LegendCompletion> completions = this.extension.getCompletions(sectionStates.get(1), TextPosition.newPosition(2, 49));
        completions.forEach(completion -> actualCompletions.add(completion.getDescription()));
        Assertions.assertEquals(expectedCompletions, actualCompletions);
    }

    @Test
    public void testAutoCompletionArrowOnType()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("vscodelsp::test::TestClass",
                "###Pure\n" +
                "Class vscodelsp::test::TestClass\n" +
                "{\n" +
                "   name: String[1];\n" +
                "   other: Integer[1];\n" +
                "}");

        codeFiles.put("vscodelsp::test::TestFunction1",
                "###Pure\n" +
                "function vscodelsp::test::func1(): Any[*] {\n" +
                "   vscodelsp::test::TestClass.all()->\n");

        codeFiles.put("vscodelsp::test::TestFunction2",
                "###Pure\n" +
                "function vscodelsp::test::func2(): Any[*] {\n" +
                "   vscodelsp::test::TestClass.all()->fu\n");

        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        Set<String> expectedCompletions = Set.of("project");
        Set<String> actualCompletions = new HashSet<>();
        Iterable<? extends LegendCompletion> completions = this.extension.getCompletions(sectionStates.get(1), TextPosition.newPosition(2, 37));
        completions.forEach(completion -> actualCompletions.add(completion.getDescription()));
        Assertions.assertEquals(expectedCompletions, actualCompletions);

        Set<String> expectedNoCompletions = Set.of();
        Set<String> actualNoCompletions = new HashSet<>();
        Iterable<? extends LegendCompletion> noCompletions = this.extension.getCompletions(sectionStates.get(2), TextPosition.newPosition(2, 39));
        noCompletions.forEach(completion -> actualNoCompletions.add(completion.getDescription()));
        Assertions.assertEquals(expectedNoCompletions, actualNoCompletions);
    }

    @Test
    public void testAutoCompletionEmptyFunctionBody()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("vscodelsp::test::TestClass",
                "###Pure\n" +
                "Class vscodelsp::test::TestClass\n" +
                "{\n" +
                "   name: String[1];\n" +
                "   other: Integer[1];\n" +
                "}");

        codeFiles.put("vscodelsp::test::TestFunction1",
                "###Pure\n" +
                "function vscodelsp::test::func1(): Any[*] {\n" +
                " \n" +
                "}");

        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        Set<String> expectedCompletions = Set.of();
        Set<String> actualCompletions = new HashSet<>();
        Iterable<? extends LegendCompletion> completions = this.extension.getCompletions(sectionStates.get(1), TextPosition.newPosition(2, 2));
        completions.forEach(completion -> actualCompletions.add(completion.getDescription()));
        Assertions.assertEquals(expectedCompletions, actualCompletions);
    }

    @Test
    public void testAutoCompletionArrowOnRelation()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("vscodelsp::test::TestDatabase",
                "###Relational\n" +
                "Database vscodelsp::test::TestDatabase\n" +
                "(\n" +
                "   Table FirmTable\n" +
                "   (\n" +
                "       id INTEGER PRIMARY KEY,\n" +
                "       Type VARCHAR(200),\n" +
                "       Legal_name VARCHAR(200)\n" +
                "   )\n" +
                ")");

        codeFiles.put("vscodelsp::test::TestFunction",
                "###Pure\n" +
                "function vscodelsp::test::func(): Any[*] {\n" +
                "   #>{vscodelsp::test::TestDatabase.FirmTable}#->s");

        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        Set<String> expectedCompletions = new HashSet<>(Arrays.asList("select", "size", "slice", "sort"));
        Set<String> actualCompletions = new HashSet<>();
        Iterable<? extends LegendCompletion> completions = this.extension.getCompletions(sectionStates.get(1), TextPosition.newPosition(2, 50));
        completions.forEach(completion -> actualCompletions.add(completion.getDescription()));
        Assertions.assertEquals(expectedCompletions, actualCompletions);
    }

    @Test
    public void testAutoCompletionMultipleFunctionsInSingularSection()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("vscodelsp::test::TestDatabase",
                "###Relational\n" +
                "Database vscodelsp::test::TestDatabase\n" +
                "(\n" +
                "   Table FirmTable\n" +
                "   (\n" +
                "       id INTEGER PRIMARY KEY,\n" +
                "       Type VARCHAR(200),\n" +
                "       Legal_name VARCHAR(200)\n" +
                "   )\n" +
                ")");

        codeFiles.put("vscodelsp::test::TestFunction",
                "###Pure\n" +
                "function vscodelsp::test::func1(): Any[*]\n" +
                "{  #>{vscodelsp::test::TestDatabase.FirmTable}#->s\n" +
                "}\n" +
                "\n" +
                "function vscodelsp::test::func2(): Any[*] {\n" +
                "   #>{vscodelsp::test::TestDatabase.FirmTable}#->");

        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        Set<String> expectedCompletions = new HashSet<>(Arrays.asList("select", "size", "slice", "sort"));
        Set<String> actualCompletions = new HashSet<>();
        Iterable<? extends LegendCompletion> completions = this.extension.getCompletions(sectionStates.get(1), TextPosition.newPosition(2, 50));
        completions.forEach(completion -> actualCompletions.add(completion.getDescription()));
        Assertions.assertEquals(expectedCompletions, actualCompletions);
    }

    @Test
    void functionTestsDiscovery()
    {
        String code = "function model::Hello(name: String[1]): String[1]\n" +
                        "{\n" +
                        "  'Hello World! My name is ' + $name + '.';\n" +
                        "}\n" +
                        "{\n" +
                        "  testSuite_1\n" +
                        "  (\n" +
                        "    testPass | Hello('John') => 'Hello World! My name is John.';\n" +
                        "    testFail | Hello('John') => 'Hello World! My name is Johnx.';\n" +
                        "  )\n" +
                        "}\n";

        SectionState sectionState = newSectionState("docId", code);

        List<LegendTest> legendTests = this.extension.testCases(sectionState);
        Assertions.assertEquals(1, legendTests.size());
        LegendTest legendTest = legendTests.get(0);
        Assertions.assertEquals("model::Hello_String_1__String_1_", legendTest.getId());
        Assertions.assertEquals(TextInterval.newInterval(0, 0, 10, 0), legendTest.getLocation().getTextInterval());

        Assertions.assertEquals(1, legendTest.getChildren().size());
        LegendTest legendTestSuite = legendTest.getChildren().get(0);
        Assertions.assertEquals("model::Hello_String_1__String_1_.testSuite_1", legendTestSuite.getId());
        Assertions.assertEquals(TextInterval.newInterval(5, 2, 9, 2), legendTestSuite.getLocation().getTextInterval());

        Assertions.assertEquals(2, legendTestSuite.getChildren().size());
        List<LegendTest> tests = legendTestSuite.getChildren().stream().sorted(Comparator.comparing(x -> x.getLocation().getTextInterval().getStart())).collect(Collectors.toList());

        LegendTest legendTest1 = tests.get(0);
        Assertions.assertEquals("model::Hello_String_1__String_1_.testSuite_1.testPass", legendTest1.getId());
        Assertions.assertEquals(TextInterval.newInterval(7, 4, 7, 63), legendTest1.getLocation().getTextInterval());

        LegendTest legendTest2 = tests.get(1);
        Assertions.assertEquals("model::Hello_String_1__String_1_.testSuite_1.testFail", legendTest2.getId());
        Assertions.assertEquals(TextInterval.newInterval(8, 4, 8, 64), legendTest2.getLocation().getTextInterval());
    }

    @Test
    void activateFunction() throws JsonProcessingException
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("Relational",
                "###Relational\n" +
                "Database showcase::model::Test\n" +
                "(\n" +
                "  Table FirmTable\n" +
                "  (\n" +
                "    id INTEGER PRIMARY KEY,\n" +
                "    Type VARCHAR(200),\n" +
                "    Legal_name VARCHAR(200)\n" +
                "  )\n" +
                ")");

        codeFiles.put("Connection",
                "###Connection\n" +
                "RelationalDatabaseConnection showcase::model::mySimpleConnection\n" +
                "{\n" +
                "  store: showcase::model::Test;\n" +
                "  type: Snowflake;\n" +
                "  specification: Snowflake\n" +
                "  {\n" +
                "    name: 'test';\n" +
                "    account: 'account';\n" +
                "    warehouse: 'warehouseName';\n" +
                "    region: 'us-east2';\n" +
                "    cloudType: 'aws';\n" +
                "  };\n" +
                "  auth: SnowflakePublic\n" +
                "  {\n" +
                "    publicUserName: 'myName';\n" +
                "    privateKeyVaultReference: 'privateKeyRef';\n" +
                "    passPhraseVaultReference: 'passRef';\n" +
                "  };\n" +
                "}\n" +
                "\n" +
                "RelationalDatabaseConnection showcase::model::mySimpleConnection2\n" +
                "{\n" +
                "  store: showcase::model::Test;\n" +
                "  type: Snowflake;\n" +
                "  specification: Snowflake\n" +
                "  {\n" +
                "    name: 'test';\n" +
                "    account: 'account';\n" +
                "    warehouse: 'warehouseName';\n" +
                "    region: 'us-east2';\n" +
                "    cloudType: 'aws';\n" +
                "  };\n" +
                "  auth: SnowflakePublic\n" +
                "  {\n" +
                "    publicUserName: 'myName';\n" +
                "    privateKeyVaultReference: 'privateKeyRef';\n" +
                "    passPhraseVaultReference: 'passRef';\n" +
                "  };\n" +
                "}");

        codeFiles.put("Pure",
                "###Pure\n" +
                "function showcase::model::testFunction(name: String[1], isTrue: Boolean[*]): meta::pure::store::RelationStoreAccessor<Any>[*]\n" +
                "{\n" +
                "  #>{showcase::model::Test.FirmTable}#->filter(x | $x.id == 1);\n" +
                "}");
        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        SectionState sectionContainingFunction = sectionStates.get(2);
        Iterable<? extends LegendExecutionResult> results = this.extension.getFunctionActivatorSnippets(sectionContainingFunction, "showcase::model::testFunction_String_1__Boolean_MANY__RelationStoreAccessor_MANY_");
        List<? extends LegendExecutionResult> resultList = StreamSupport.stream(results.spliterator(), false).collect(Collectors.toList());
        Assertions.assertEquals(1, resultList.size());
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> functionActivatorSnippets = objectMapper.readValue(resultList.get(0).getMessage(), new TypeReference<>() {});
        String expectedSnowflakeSnippet = "\n" +
                "\n" +
                "###Snowflake\n" +
                "SnowflakeApp ${1:showcase::model}::${2:testFunctionSnowflakeActivator}\n" +
                "{\n" +
                "\tapplicationName: '${3:testFunctionSnowflakeActivator}';\n" +
                "\tfunction: showcase::model::testFunction(String[1],Boolean[*]):RelationStoreAccessor[*];\n" +
                "\townership: Deployment { identifier: '${4:DID}' };\n" +
                "\tdescription: '${5:Please provide a description}';\n" +
                "\tactivationConfiguration: ${6|showcase::model::mySimpleConnection,showcase::model::mySimpleConnection2|};\n" +
                "}";
        String expectedHostedServiceSnippet = "\n" +
                "\n" +
                "###HostedService\n" +
                "HostedService ${1:showcase::model}::${2:testFunctionHostedServiceActivator}\n" +
                "{\n" +
                "\tpattern: '/${3:Please provide a pattern}';\n" +
                "\townership: Deployment { identifier: '${4:DID}' };\n" +
                "\tfunction: showcase::model::testFunction(String[1],Boolean[*]):RelationStoreAccessor[*];\n" +
                "\tdocumentation: '${5:Please provide a documentation}';\n" +
                "\tautoActivateUpdates: ${6|true,false|};\n" +
                "}";
        Assertions.assertTrue(functionActivatorSnippets.containsKey("Snowflake"));
        Assertions.assertTrue(functionActivatorSnippets.containsKey("HostedService"));
        Assertions.assertEquals(expectedSnowflakeSnippet, functionActivatorSnippets.get("Snowflake"));
        Assertions.assertEquals(expectedHostedServiceSnippet, functionActivatorSnippets.get("HostedService"));
    }

    @Test
    void functionTestsExecution()
    {
        String code = "function model::Hello(name: String[1]): String[1]\n" +
                "{\n" +
                "  'Hello World! My name is ' + $name + '.';\n" +
                "}\n" +
                "{\n" +
                "  testSuite_1\n" +
                "  (\n" +
                "    testPass | Hello('John') => 'Hello World! My name is John.';\n" +
                "    testFail | Hello('John') => 'Hello World! My name is Johnx.';\n" +
                "  )\n" +
                "}\n";

        SectionState sectionState = newSectionState("doc", code);

        TextLocation location1 = TextLocation.newTextSource("doc", TextInterval.newInterval(5, 4, 5, 5));

        LegendTestAssertionResult assertionResult = LegendTestAssertionResult.failure("default", TextLocation.newTextSource("doc", 8, 32, 8, 63), "expected:Hello World! My name is Johnx., Found : Hello World! My name is John.", null, null);
        LegendTestExecutionResult failResult = LegendTestExecutionResult.failures(List.of(assertionResult), "model::Hello_String_1__String_1_.testSuite_1.testFail");
        LegendTestExecutionResult passResult = LegendTestExecutionResult.success("model::Hello_String_1__String_1_.testSuite_1.testPass");

        // all test
        assertTestExecution("model::Hello_String_1__String_1_", Set.of(), sectionState, location1, List.of(failResult, passResult));
        // skip suite
        assertTestExecution("model::Hello_String_1__String_1_", Set.of("model::Hello_String_1__String_1_.testSuite_1"), sectionState, location1, List.of());
        // skip one test
        assertTestExecution("model::Hello_String_1__String_1_", Set.of("model::Hello_String_1__String_1_.testSuite_1.testFail"), sectionState, location1, List.of(passResult));
        // skip a different test
        assertTestExecution("model::Hello_String_1__String_1_", Set.of("model::Hello_String_1__String_1_.testSuite_1.testPass"), sectionState, location1, List.of(failResult));
        // execute the suite
        assertTestExecution("model::Hello_String_1__String_1_.testSuite_1", Set.of(), sectionState, location1, List.of(failResult, passResult));
        // execute a test directly
        assertTestExecution("model::Hello_String_1__String_1_.testSuite_1.testPass", Set.of(), sectionState, location1, List.of(passResult));
    }

    @Test
    void testGetReferenceResolversClass()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();

        String docId = "classes.pure";
        codeFiles.put(docId,
                "###Pure\n" +
                        "Class showcase::model::Mammal\n" +
                        "{\n" +
                        "  id: String[1];\n" +
                        "}\n" +
                        "Class showcase::model::Pet\n" +
                        "{\n" +
                        "  name: String[1];\n" +
                        "}\n" +
                        "Class showcase::model::Dog extends showcase::model::Pet, showcase::model::Mammal\n" +
                        "{\n" +
                        "}\n"
        );


        LegendReference petReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(docId, 9, 35, 9, 54))
                .withDeclarationLocation(TextLocation.newTextSource(docId, 5, 0, 8, 0))
                .build();

        testReferenceLookup(codeFiles, docId, TextPosition.newPosition(9, 50), petReference, "Supertype linked to class");

        LegendReference mammalReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(docId, 9, 57, 9, 79))
                .withDeclarationLocation(TextLocation.newTextSource(docId, 1, 0, 4, 0))
                .build();

        testReferenceLookup(codeFiles, docId, TextPosition.newPosition(9, 65), mammalReference, "Supertype linked to class");

    }

    @Test
    void testGetReferenceResolversProfilesAndProperties()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_PROFILE_DOC_ID = "showcase::model::MyExtension";
        final String TEST_ENUMERATION_DOC_ID = "showcase::model::IncType";
        final String TEST_CLASS_DOC_ID = "showcase::model::LegalEntity";
        final String TEST_CLASS_DOC_ID2 = "showcase::model::Person";
        final String TEST_CLASS_DOC_ID3 = "showcase::model::Firm";
        codeFiles.put(TEST_PROFILE_DOC_ID,
                "###Pure\n" +
                "Profile showcase::model::MyExtension\n" +
                "{\n" +
                "  stereotypes: [important];\n" +
                "  tags: [doc];\n" +
                "}");

        codeFiles.put(TEST_ENUMERATION_DOC_ID,
                "###Pure\n" +
                "Enum showcase::model::IncType\n" +
                "{\n" +
                "  Corp,\n" +
                "  LLC\n" +
                "}");

        codeFiles.put(TEST_CLASS_DOC_ID,
                "###Pure\n" +
                "Class showcase::model::LegalEntity\n" +
                "{\n" +
                "  id: String[1];\n" +
                "  legalName: String[1];\n" +
                "  businessDate: Date[1];\n" +
                "}");

        codeFiles.put(TEST_CLASS_DOC_ID2,
                "###Pure\n" +
                "Class showcase::model::Person\n" +
                "{\n" +
                "  firstName: String[1];\n" +
                "  lastName: String[1];\n" +
                "}");

        codeFiles.put(TEST_CLASS_DOC_ID3,
                "###Pure\n" +
                "Class <<showcase::model::MyExtension.important>> {showcase::model::MyExtension.doc = 'This is a model of a firm'} showcase::model::Firm extends showcase::model::LegalEntity\n" +
                "[\n" +
                "  validName: $this.legalName->startsWith('_')\n" +
                "]\n" +
                "{\n" +
                "  employees: showcase::model::Person[1..*];\n" +
                "  incType: showcase::model::IncType[1];\n" +
                "  isApple: Boolean[1];\n" +
                "  myVar: meta::pure::store::RelationStoreAccessor[*];\n" +
                "  employeeSize() {$this.employees->count()}: Number[1];\n" +
                "}");

        LegendReference mappedProfileReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID3, 1, 8, 1, 35))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_PROFILE_DOC_ID, 1, 0, 5, 0))
                .build();
        testReferenceLookup(codeFiles, TEST_CLASS_DOC_ID3, TextPosition.newPosition(1, 30), mappedProfileReference, "Within the profile name has been mapped, referring to profile");

        LegendReference mappedProfileReference2 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID3, 1, 50, 1, 77))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_PROFILE_DOC_ID, 1, 0, 5, 0))
                .build();
        testReferenceLookup(codeFiles, TEST_CLASS_DOC_ID3, TextPosition.newPosition(1, 70), mappedProfileReference2, "Within the profile name has been mapped, referring to profile");

        LegendReference mappedTagReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID3, 1, 79, 1, 81))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_PROFILE_DOC_ID, 4, 9, 4, 11))
                .build();
        testReferenceLookup(codeFiles, TEST_CLASS_DOC_ID3, TextPosition.newPosition(1, 80), mappedTagReference, "Within the class tag has been mapped, referring to profile tag");

        LegendReference mappedPropertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID3, 6, 13, 6, 35))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID2, 1, 0, 5, 0))
                .build();
        testReferenceLookup(codeFiles, TEST_CLASS_DOC_ID3, TextPosition.newPosition(6, 30), mappedPropertyReference, "Within the property has been mapped, referring to property");

        LegendReference mappedPropertyReference2 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID3, 7, 11, 7, 34))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_ENUMERATION_DOC_ID, 1, 0, 5, 0))
                .build();
        testReferenceLookup(codeFiles, TEST_CLASS_DOC_ID3, TextPosition.newPosition(7, 30), mappedPropertyReference2, "Within the property has been mapped, referring to property");
    }

    @Test
    void testGetReferenceResolversPropertyInLambda()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_CLASS_DOC_ID = "showcase::model::LegalEntity";
        final String TEST_FUNCTION_DOC_ID = "showcase::model::myfunc";
        codeFiles.put(TEST_CLASS_DOC_ID,
                "###Pure\n" +
                "Class showcase::model::LegalEntity\n" +
                "{\n" +
                "  id: String[1];\n" +
                "  legalName: String[1];\n" +
                "  businessDate: Date[1];\n" +
                "}");

        codeFiles.put(TEST_FUNCTION_DOC_ID,
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

        LegendReference mappedPropertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 5, 11, 5, 12))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID, 3, 2, 3, 15))
                .build();
        testReferenceLookup(codeFiles, TEST_FUNCTION_DOC_ID, TextPosition.newPosition(5, 12), mappedPropertyReference, "Within the property name has been mapped, referring to property");
    }

    @Test
    void testGetReferenceResolversGraphFetchTree()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_CLASS_DOC_ID = "test::Address";
        final String TEST_CLASS_DOC_ID2 = "test::City";
        final String TEST_CLASS_DOC_ID3 = "test::Street";
        final String TEST_FUNCTION_DOC_ID = "test::myFunc";
        codeFiles.put(TEST_CLASS_DOC_ID,
                "###Pure\n" +
                "Class test::Address\n" +
                "{\n" +
                "  Id: Integer[1];\n" +
                "}");

        codeFiles.put(TEST_CLASS_DOC_ID2,
                "###Pure\n" +
                "Class test::City extends test::Address\n" +
                "{\n" +
                "  name: String[1];\n" +
                "}");

        codeFiles.put(TEST_CLASS_DOC_ID3,
                "###Pure\n" +
                "Class test::Street extends test::Address\n" +
                "{\n" +
                "  street: String[1];\n" +
                "}");

        codeFiles.put(TEST_FUNCTION_DOC_ID,
                "###Pure\n" +
                "function test::myFunc(): Any[*]\n" +
                "{\n" +
                "  test::Address.all()->graphFetch(\n" +
                "      #{\n" +
                "        test::Address{\n" +
                "          Id,\n" +
                "          ->subType(@test::Street){\n" +
                "            street\n" +
                "          },\n" +
                "          ->subType(@test::City){\n" +
                "            name\n" +
                "          }\n" +
                "        }\n" +
                "      }#\n" +
                "    )->serialize(\n" +
                "      #{\n" +
                "        test::Address{\n" +
                "          Id,\n" +
                "          ->subType(@test::Street){\n" +
                "            street\n" +
                "          },\n" +
                "          ->subType(@test::City){\n" +
                "            name\n" +
                "          }\n" +
                "        }\n" +
                "      }#\n" +
                "    );\n" +
                "}");

        LegendReference mappedPropertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 6, 10, 6, 11))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID, 3, 2, 3, 16))
                .build();
        testReferenceLookup(codeFiles, TEST_FUNCTION_DOC_ID, TextPosition.newPosition(6, 10), mappedPropertyReference, "Within the property name has been mapped, referring to property");

        LegendReference mappedClassReference1 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 5, 8, 5, 20))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID, 1, 0, 4, 0))
                .build();
        testReferenceLookup(codeFiles, TEST_FUNCTION_DOC_ID, TextPosition.newPosition(5, 11), mappedClassReference1, "Within the rootGraphFetchTree name has been mapped, referring to class definition");

        LegendReference mappedClassReference2 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 17, 8, 17, 20))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID, 1, 0, 4, 0))
                .build();
        testReferenceLookup(codeFiles, TEST_FUNCTION_DOC_ID, TextPosition.newPosition(17, 10), mappedClassReference2, "Within the rootGraphFetchTree name has been mapped, referring to class definition");
    }

    private MutableMap<String, String> getCodeFilesThatParseCompile()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("vscodelsp::test::MyEnum",
                "###Pure\n" +
                        "Enum vscodelsp::test::MyEnum\n" +
                        "{\n" +
                        "  ENUM1,\n" +
                        "  ENUM2,\n" +
                        "  ENUM3\n" +
                        "}");

        codeFiles.put("vscodelsp::test::TestClass1",
                "###Pure\n" +
                        "Class vscodelsp::test::TestClass1\n" +
                        "{\n" +
                        "  name: String[1];\n" +
                        "  address: String[1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::TestClass2",
                "###Pure\n" +
                        "Class vscodelsp::test::TestClass2\n" +
                        "{\n" +
                        "  type: vscodelsp::test::MyEnum[1];\n" +
                        "  name: String[1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::TestClass3",
                "###Pure\n" +
                        "Class {meta::pure::profiles::doc.doc = 'use tags to add metadata.'} vscodelsp::test::TestClass3 extends vscodelsp::test::TestClass5\n" +
                        "[\n" +
                        "  constraint1: $this->project(\n" +
                        "  [\n" +
                        "    a: vscodelsp::test::TestClass3[1]|$a.tests.id\n" +
                        "  ],\n" +
                        "  ['testId']\n" +
                        ")->groupBy(\n" +
                        "  'testId',\n" +
                        "  'count'->agg(\n" +
                        "    x: meta::pure::tds::TDSRow[1]|$x,\n" +
                        "    y: meta::pure::tds::TDSRow[*]|$y->count()\n" +
                        "  )\n" +
                        ")->filter(\n" +
                        "  t: meta::pure::tds::TDSRow[1]|$t.getInteger('count') > 1\n" +
                        ")->tdsRows()->isEmpty(),\n" +
                        "  constraint2: true\n" +
                        "]\n" +
                        "{\n" +
                        "  isOpen() {$this.closeDate->isEmpty()}: Boolean[1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::TestClass4",
                "###Pure\n" +
                        "Class vscodelsp::test::TestClass4\n" +
                        "{\n" +
                        "  eventType: String[1];\n" +
                        "  eventDate: StrictDate[1];\n" +
                        "  initiator: vscodelsp::test::TestClass1[0..1];\n" +
                        "  prop: vscodelsp::test::TestClass7[1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::TestClass5",
                "###Pure\n" +
                        "Class vscodelsp::test::TestClass5\n" +
                        "{\n" +
                        "  name: String[1];\n" +
                        "  createDate: StrictDate[1];\n" +
                        "  tests: vscodelsp::test::TestClass7[*];\n" +
                        "  closeDate: StrictDate[0..1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::TestClass6",
                "###Pure\n" +
                        "Class vscodelsp::test::TestClass6\n" +
                        "{\n" +
                        "  type: String[1];\n" +
                        "  description: String[1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::TestClass7",
                "###Pure\n" +
                        "Class vscodelsp::test::TestClass7\n" +
                        "{\n" +
                        "  id: Integer[1];\n" +
                        "  testDate: StrictDate[1];\n" +
                        "  quantity: Float[1];\n" +
                        "  dateTime: DateTime[0..1];\n" +
                        "  thing: vscodelsp::test::TestClass8[0..1];\n" +
                        "  account: vscodelsp::test::TestClass5[0..1];\n" +
                        "  events: vscodelsp::test::TestClass4[*];\n" +
                        "  thingIdentifier() {if(\n" +
                        "  $this.thing->isNotEmpty(),\n" +
                        "  |$this.thing->toOne().name,\n" +
                        "  |'Unknown'\n" +
                        ")}: String[1];\n" +
                        "  eventsByDate(date: Date[1]) {$this.events->filter(\n" +
                        "  e: vscodelsp::test::TestClass4[1]|$e.eventDate ==\n" +
                        "    $date\n" +
                        ")}: vscodelsp::test::TestClass4[*];\n" +
                        "  testDateEvent() {$this.eventsByDate($this.testDate->toOne())->toOne()}: vscodelsp::test::TestClass4[1];\n" +
                        "  testDateEventType() {$this.testDateEvent.eventType}: String[1];\n" +
                        "  initiator() {$this.testDateEvent.initiator}: vscodelsp::test::TestClass1[0..1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::TestClass8",
                "###Pure\n" +
                        "Class {meta::pure::profiles::doc.doc = 'must pass date for ENUM2/ENUM1/ENUM3 now.'} vscodelsp::test::TestClass8\n" +
                        "{\n" +
                        "  name: String[1];\n" +
                        "  classification: vscodelsp::test::TestClass6[1];\n" +
                        "  enum1() {$this.property->filter(\n" +
                        "  s: vscodelsp::test::TestClass2[1]|$s.type ==\n" +
                        "    vscodelsp::test::MyEnum.ENUM1\n" +
                        ")->toOne().name}: String[1];\n" +
                        "  enum2() {$this.property->filter(\n" +
                        "  s: vscodelsp::test::TestClass2[1]|$s.type ==\n" +
                        "    vscodelsp::test::MyEnum.ENUM2\n" +
                        ")->toOne().name}: String[1];\n" +
                        "  enum3() {$this.property->filter(\n" +
                        "  s: vscodelsp::test::TestClass2[1]|$s.type ==\n" +
                        "    vscodelsp::test::MyEnum.ENUM3\n" +
                        ")->toOne().name}: String[1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::TestAssociation",
                "###Pure\n" +
                        "Association vscodelsp::test::TestAssociation\n" +
                        "{\n" +
                        "  thing: vscodelsp::test::TestClass8[1];\n" +
                        "  property: vscodelsp::test::TestClass2[*];\n" +
                        "}");

        return codeFiles;
    }

    @Test
    void testGetReferenceResolversLambdasInConstraintsAndProperties()
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();
        LegendReference mappedClassPropertyInLambdaReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::TestClass3", 5, 41, 5, 45))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::TestClass5", 5, 2, 5, 39))
                .build();
        testReferenceLookup(codeFiles, "vscodelsp::test::TestClass3", TextPosition.newPosition(5, 42), mappedClassPropertyInLambdaReference, "Within the class property has been mapped, referring to class property definition");

        LegendReference mappedQualifiedPropertyLambdaPropertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::TestClass3", 20, 18, 20, 26))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::TestClass5", 6, 2, 6, 29))
                .build();
        testReferenceLookup(codeFiles, "vscodelsp::test::TestClass3", TextPosition.newPosition(20, 20), mappedQualifiedPropertyLambdaPropertyReference, "Within the property has been mapped, referring to property definition");
    }

    @Test
    @Disabled("Enable once m3 source information is fixed")
    void testGetReferenceResolversLambdasComplete()
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();
        LegendReference mappedClassReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::TestClass3", 3, 16, 3, 19))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::TestClass3", 1, 0, 21, 0))
                .build();
        testReferenceLookup(codeFiles, "vscodelsp::test::TestClass3", TextPosition.newPosition(3, 17), mappedClassReference, "Within the class name has been mapped, referring to class definition");

        LegendReference mappedClassReference2 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::TestClass3", 5, 7, 5, 33))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::TestClass3", 1, 0, 21, 0))
                .build();
        testReferenceLookup(codeFiles, "vscodelsp::test::TestClass3", TextPosition.newPosition(5, 17), mappedClassReference2, "Within the class name has been mapped, referring to class definition");

        LegendReference mappedNestedClassPropertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::TestClass3", 5, 47, 5, 48))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::TestClass7", 3, 2, 3, 16))
                .build();
        testReferenceLookup(codeFiles, "vscodelsp::test::TestClass3", TextPosition.newPosition(5, 47), mappedNestedClassPropertyReference, "Within the nested property name has been mapped, referring to property definition");

        LegendReference mappedReturnTypeReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::TestClass7", 18, 4, 18, 30))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::TestClass4", 3, 2, 3, 16))
                .build();
        testReferenceLookup(codeFiles, "vscodelsp::test::TestClass7", TextPosition.newPosition(18, 25), mappedReturnTypeReference, "Within the return type has been mapped, referring to class definition");

        LegendReference mappedEnumReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::TestClass8", 7, 4, 7, 26))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::MyEnum", 1, 0, 6, 0))
                .build();
        testReferenceLookup(codeFiles, "vscodelsp::test::TestClass8", TextPosition.newPosition(7, 25), mappedEnumReference, "Within the enum has been mapped, referring to enum definition");

        LegendReference mappedEnumValueReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::TestClass8", 7, 28, 7, 32))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::MyEnum", 3, 2, 3, 6))
                .build();
        testReferenceLookup(codeFiles, "vscodelsp::test::TestClass8", TextPosition.newPosition(7, 29), mappedEnumValueReference, "Within the enum value has been mapped, referring to enum value definition");
    }

    @Test
    @Disabled("Enable once m3 source information is fixed")
    void testGetReferenceResolversFunction()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_CLASS_DOC_ID = "showcase::model::LegalEntity";
        final String TEST_FUNCTION_DOC_ID = "showcase::model::myfunc";
        codeFiles.put(TEST_CLASS_DOC_ID,
                "###Pure\n" +
                "Class showcase::model::LegalEntity\n" +
                "{\n" +
                "  id: String[1];\n" +
                "  legalName: String[1];\n" +
                "  businessDate: Date[1];\n" +
                "}");

        codeFiles.put(TEST_FUNCTION_DOC_ID,
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

        LegendReference mappedClassReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 3, 2, 3, 29))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID, 1, 0, 6, 0))
                .build();
        testReferenceLookup(codeFiles, TEST_FUNCTION_DOC_ID, TextPosition.newPosition(3, 25), mappedClassReference, "Within the class name has been mapped, referring to class definition");

        LegendReference mappedParameterReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 3, 36, 3, 47))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 1, 33, 1, 53))
                .build();
        testReferenceLookup(codeFiles, TEST_FUNCTION_DOC_ID, TextPosition.newPosition(3, 40), mappedParameterReference, "Within the parameter name has been mapped, referring to parameter");

        LegendReference mappedPropertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 5, 11, 5, 12))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID, 3, 2, 3, 14))
                .build();
        testReferenceLookup(codeFiles, TEST_FUNCTION_DOC_ID, TextPosition.newPosition(5, 12), mappedPropertyReference, "Within the property name has been mapped, referring to property");

        LegendReference mappedLambdaVariableReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 5, 9, 5, 9))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 5, 6, 5, 6))
                .build();
        testReferenceLookup(codeFiles, TEST_FUNCTION_DOC_ID, TextPosition.newPosition(5, 9), mappedLambdaVariableReference, "Within the lambda variable has been mapped, referring to lambda variable");
    }

    @Test
    void testGetReferenceResolversFunctionTestSuite()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_DATABASE_DOC_ID = "store::TestDB";
        final String TEST_CLASS_DOC_ID = "model::Person";
        final String TEST_MAPPING_DOC_ID = "execution::RelationalMapping";
        final String TEST_FUNCTION_DOC_ID = "model::PersonWithConnectionStores";
        final String TEST_RUNTIME_DOC_ID = "execution::RuntimeWithStoreConnections";
        final String TEST_CONNECTION_DOC_ID = "model::MyConnection";

        codeFiles.put(TEST_DATABASE_DOC_ID,
                "###Relational\n" +
                        "Database store::TestDB\n" +
                        "(\n" +
                        "  Table FirmTable\n" +
                        "  (\n" +
                        "    id INTEGER PRIMARY KEY,\n" +
                        "    legal_name VARCHAR(200)\n" +
                        "  )\n" +
                        "  Table PersonTable\n" +
                        "  (\n" +
                        "    id INTEGER PRIMARY KEY,\n" +
                        "    firm_id INTEGER,\n" +
                        "    firstName VARCHAR(200),\n" +
                        "    lastName VARCHAR(200)\n" +
                        "  )\n" +
                        "\n" +
                        "  Join FirmPerson(PersonTable.firm_id = FirmTable.id)\n" +
                        ")");

        codeFiles.put(TEST_CLASS_DOC_ID,
                "###Pure\n" +
                "Class model::Person\n" +
                        "{\n" +
                        "  firstName: String[1];\n" +
                        "  lastName: String[1];\n" +
                        "}");

        codeFiles.put(TEST_MAPPING_DOC_ID,
                "###Mapping\n" +
                        "Mapping execution::RelationalMapping\n" +
                        "(\n" +
                        "  *model::Person: Relational\n" +
                        "  {\n" +
                        "    ~primaryKey\n" +
                        "    (\n" +
                        "      [store::TestDB]PersonTable.id\n" +
                        "    )\n" +
                        "    ~mainTable [store::TestDB]PersonTable\n" +
                        "    firstName: [store::TestDB]PersonTable.firstName,\n" +
                        "    lastName: [store::TestDB]PersonTable.lastName\n" +
                        "  }\n" +
                        ")");

        codeFiles.put(TEST_CONNECTION_DOC_ID,
                "###Connection\n" +
                        "RelationalDatabaseConnection model::MyConnection\n" +
                        "{\n" +
                        "  store: store::TestDB;\n" +
                        "  type: H2;\n" +
                        "  specification: LocalH2\n" +
                        "  {\n" +
                        "    testDataSetupSqls: [\n" +
                        "      ];\n" +
                        "  };\n" +
                        "  auth: DefaultH2;\n" +
                        "}\n");

        codeFiles.put(TEST_RUNTIME_DOC_ID,
                "###Runtime\n" +
                        "Runtime execution::RuntimeWithStoreConnections\n" +
                        "{\n" +
                        "  mappings:\n" +
                        "  [\n" +
                        "    execution::RelationalMapping\n" +
                        "  ];\n" +
                        "  connectionStores:\n" +
                        "  [\n" +
                        "    model::MyConnection:\n" +
                        "    [\n" +
                        "       store::TestDB\n" +
                        "    ]\n" +
                        "  ];\n" +
                        "}");

        codeFiles.put(TEST_FUNCTION_DOC_ID,
                "###Pure\n" +
                        "function model::PersonWithConnectionStores(): meta::pure::tds::TabularDataSet[1]\n" +
                        "{\n" +
                        "  model::Person.all()->project(\n" +
                        "    [\n" +
                        "      x|$x.firstName,\n" +
                        "      x|$x.lastName\n" +
                        "    ],\n" +
                        "    [\n" +
                        "      'First Name',\n" +
                        "      'Last Name'\n" +
                        "    ]\n" +
                        "  )->from(\n" +
                        "     execution::RelationalMapping,\n" +
                        "     execution::RuntimeWithStoreConnections\n" +
                        "  )\n" +
                        "}\n" +
                        "{\n" +
                        "testSuite_1\n" +
                        "  (\n" +
                        "    store::TestDB:\n" +
                        "          Relational\n" +
                        "          #{\n" +
                        "            default.PersonTable:\n" +
                        "              'id,firm_id,firstName,lastName,employeeType\\n'+\n" +
                        "              '2,1,Nicole,Smith,FTC\\n';\n" +
                        "          }#;\n" +
                        "    test_1 | PersonWithConnectionStores() => (JSON) '[ {\\n  \"First Name\" : \"Nicole\",\\n  \"Last Name\" : \"Smith\"\\n} ]';\n" +
                        "   )\n" +
                        "}");

        LegendReference mappedStoreReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 20, 4, 20, 16))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_DATABASE_DOC_ID, 1, 0, 17, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_FUNCTION_DOC_ID, TextPosition.newPosition(20, 4), mappedStoreReference, "Within the function test suite class name has been mapped, referring to store definition");
    }

    @Test
    @Disabled("Enable once m3 source information is fixed")
    void testGetReferenceResolversFunctionNewSyntax()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_DATABASE_DOC_ID = "showcase::model::Test";
        final String TEST_FUNCTION_DOC_ID = "showcase::model::myfunc";
        codeFiles.put(TEST_DATABASE_DOC_ID,
                "###Relational\n" +
                "Database showcase::model::Test\n" +
                "(\n" +
                "  Table FirmTable\n" +
                "  (\n" +
                "    id INTEGER PRIMARY KEY,\n" +
                "    Type VARCHAR(200),\n" +
                "    Legal_name VARCHAR(200)\n" +
                "  )\n" +
                ")");

        codeFiles.put(TEST_FUNCTION_DOC_ID,
                "###Pure\n" +
                "function showcase::model::myfunc(name: String[1], isTrue: Boolean[*]): meta::pure::store::RelationStoreAccessor[*]\n" +
                "{\n" +
                "  #>{showcase::model::Test.FirmTable}#->filter(x | $x.id == 1);\n" +
                "}");

        LegendReference mappedTablePropertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_FUNCTION_DOC_ID, 3, 54, 3, 55))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_DATABASE_DOC_ID, 5, 5, 5, 25))
                .build();
        testReferenceLookup(codeFiles, TEST_FUNCTION_DOC_ID, TextPosition.newPosition(3, 55), mappedTablePropertyReference, "Within the property name has been mapped, referring to property definition");
    }

    private void assertTestExecution(String testId, Set<String> exclusions, SectionState sectionState, TextLocation location, List<LegendTestExecutionResult> expectedResults)
    {
        List<LegendTestExecutionResult> legendTestExecutionResults = this.extension.executeTests(sectionState, location, testId, exclusions);
        Assertions.assertEquals(expectedResults, legendTestExecutionResults.stream().sorted(Comparator.comparing(LegendTestExecutionResult::getId)).collect(Collectors.toList()));
    }
}
