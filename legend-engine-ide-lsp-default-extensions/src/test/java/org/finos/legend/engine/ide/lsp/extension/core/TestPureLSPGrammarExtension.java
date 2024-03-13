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
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtensionTest;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Kind;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Source;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommand;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.junit.jupiter.api.Assertions;
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
        testDiagnostics(codeFiles, "vscodelsp::test::Employee1", LegendDiagnostic.newDiagnostic(TextLocation.newTextSource("vscodelsp::test::Employee1",1, 0, 6, 0), "Can't find type 'vscodelsp::test::Employee2'", Kind.Error, Source.Compiler));
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
        Iterable<? extends LegendCompletion> completions = this.extension.getCompletions(sectionStates.get(1), TextPosition.newPosition(2, 48));
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
        Set<String> expectedCompletions = new HashSet<>(Arrays.asList("project"));
        Set<String> actualCompletions = new HashSet<>();
        Iterable<? extends LegendCompletion> completions = this.extension.getCompletions(sectionStates.get(1), TextPosition.newPosition(2, 36));
        completions.forEach(completion -> actualCompletions.add(completion.getDescription()));
        Assertions.assertEquals(expectedCompletions, actualCompletions);

        Set<String> expectedNoCompletions = new HashSet<>();
        Set<String> actualNoCompletions = new HashSet<>();
        Iterable<? extends LegendCompletion> noCompletions = this.extension.getCompletions(sectionStates.get(2), TextPosition.newPosition(2, 38));
        noCompletions.forEach(completion -> actualNoCompletions.add(completion.getDescription()));
        Assertions.assertEquals(expectedNoCompletions, actualNoCompletions);
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
        Iterable<? extends LegendCompletion> completions = this.extension.getCompletions(sectionStates.get(1), TextPosition.newPosition(2, 49));
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
        Iterable<? extends LegendCompletion> completions = this.extension.getCompletions(sectionStates.get(1), TextPosition.newPosition(2, 49));
        completions.forEach(completion -> actualCompletions.add(completion.getDescription()));
        Assertions.assertEquals(expectedCompletions, actualCompletions);
    }

    @Test
    void functionTestableCommand()
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
        List<? extends LegendCommand> commands = Lists.mutable.ofAll(this.extension.getCommands(sectionState))
                .sortThis(Comparator.comparing(LegendCommand::getId).thenComparing(x -> x.getLocation().getTextInterval().getStart().getLine()));
        Assertions.assertEquals(4, commands.size());

        // todo missing the run test suite as source info is wrong on engine: https://github.com/finos/legend-engine/pull/2693

        Assertions.assertEquals(PureLSPGrammarExtension.EXEC_FUNCTION_WITH_PARAMETERS_ID, commands.get(0).getId());
        Assertions.assertEquals(AbstractLSPGrammarExtension.RUN_TEST_COMMAND_ID, commands.get(1).getId());
        Assertions.assertEquals(TextInterval.newInterval(7, 4, 7, 63), commands.get(1).getLocation().getTextInterval());
        Assertions.assertEquals(AbstractLSPGrammarExtension.RUN_TEST_COMMAND_ID, commands.get(2).getId());
        Assertions.assertEquals(TextInterval.newInterval(8, 4, 8, 64), commands.get(2).getLocation().getTextInterval());
        Assertions.assertEquals(AbstractLSPGrammarExtension.RUN_TESTS_COMMAND_ID, commands.get(3).getId());
        Assertions.assertEquals(TextInterval.newInterval(0, 0, 10, 0), commands.get(3).getLocation().getTextInterval());
    }

    @Test
    void runAllTests()
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
                "}\n" +
                "function model::HelloNickName(name: String[1]): String[1]\n" +
                "{\n" +
                "  'Hello World! My nickname is ' + $name + '.';\n" +
                "}\n" +
                "{\n" +
                "  testSuite_1\n" +
                "  (\n" +
                "    testPass | HelloNickName('John') => 'Hello World! My nickname is John.';\n" +
                "    testFail | HelloNickName('John') => 'Hello World! My nickname is Johnx.';\n" +
                "  )\n" +
                "}\n";

        SectionState sectionState = newSectionState("doc", code);
        List<? extends LegendExecutionResult> results = this.extension.executeAllTestCases(sectionState).sorted(Comparator.comparing(x -> x.getLocation().getTextInterval().getStart())).collect(Collectors.toList());
        Assertions.assertEquals(4, results.size());

        Assertions.assertEquals(Lists.mutable.of("model::Hello_String_1__String_1_", "testSuite_1", "testPass", "default"), results.get(0).getIds());
        Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, results.get(0).getType());
        Assertions.assertEquals(TextLocation.newTextSource("doc", 7, 4, 7, 63), results.get(0).getLocation());

        Assertions.assertEquals(Lists.mutable.of("model::Hello_String_1__String_1_", "testSuite_1", "testFail", "default"), results.get(1).getIds());
        Assertions.assertEquals(LegendExecutionResult.Type.FAILURE, results.get(1).getType());
        Assertions.assertEquals(TextLocation.newTextSource("doc", 8, 4, 8, 64), results.get(1).getLocation());

        Assertions.assertEquals(Lists.mutable.of("model::HelloNickName_String_1__String_1_", "testSuite_1", "testPass", "default"), results.get(2).getIds());
        Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, results.get(2).getType());
        Assertions.assertEquals(TextLocation.newTextSource("doc", 18, 4, 18, 75), results.get(2).getLocation());

        Assertions.assertEquals(Lists.mutable.of("model::HelloNickName_String_1__String_1_", "testSuite_1", "testFail", "default"), results.get(3).getIds());
        Assertions.assertEquals(LegendExecutionResult.Type.FAILURE, results.get(3).getType());
        Assertions.assertEquals(TextLocation.newTextSource("doc", 19, 4, 19, 76), results.get(3).getLocation());
    }
}
