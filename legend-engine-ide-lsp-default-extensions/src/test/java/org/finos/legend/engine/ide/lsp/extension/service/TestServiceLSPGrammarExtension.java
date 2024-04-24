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

package org.finos.legend.engine.ide.lsp.extension.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtensionTest;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.core.FunctionExecutionSupport;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Kind;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Source;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommand;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.junit.jupiter.api.Assertions;
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
                        "\n" +
                        "\n" +
                        "Service test::services::TestService\n" +
                        "{\n" +
                        "    pattern : 'test';\n" +
                        "    documentation : 'service for testing';\n" +
                        "    execution : Single\n" +
                        "    {\n" +
                        "        query : src:test::model::TestClass[1] | $src.name;\n" +
                        "        mapping : test::mappings::TestMapping;\n" +
                        "        runtime : test::runtimes::TestRuntime;\n" +
                        "    }\n" +
                        "    test : Single" +
                        "    {\n" +
                        "        data : '';\n" +
                        "        asserts : [];\n" +
                        "    }\n" +
                        "}\n",
                LegendDeclaration.builder().withIdentifier("test::services::TestService").withClassifier("meta::legend::service::metamodel::Service").withLocation(DOC_ID_FOR_TEXT,3, 0, 17, 0).build()
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
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT,5, 12, 5, 17), "Unexpected token", Kind.Error, Source.Parser)
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
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT,10, 18, 10, 44), "Can't find mapping 'test::mappings::TestMapping'", Kind.Error, Source.Compiler)
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

    private static final String TEST_RUNTIME_DOC_ID = "vscodelsp::test::H2Runtime";
    private static final String TEST_MAPPING_DOC_ID = "vscodelsp::test::EmployeeMapping";
    private static final String TEST_STORE_DOC_ID_1 = "vscodelsp::test::TestDB1";
    private static final String TEST_STORE_DOC_ID_2 = "vscodelsp::test::TestDB2";
    private static final String TEST_CONNECTION_DOC_ID_1 = "vscodelsp::test::LocalH2Connection1";
    private static final String TEST_CONNECTION_DOC_ID_2 = "vscodelsp::test::LocalH2Connection2";
    private static final String TEST_SERVICE_DOC_ID = "vscodelsp::test::TestService";

    private MutableMap<String, String> getCodeFilesThatParseCompile()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("vscodelsp::test::Employee",
                "###Pure\n" +
                        "Class vscodelsp::test::Employee\n" +
                        "{\n" +
                        "    foobar: Float[1];\n" +
                        "    hireDate : Date[1];\n" +
                        "    hireType : String[1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::EmployeeSrc",
                "###Pure\n" +
                        "Class vscodelsp::test::EmployeeSrc\n" +
                        "{\n" +
                        "    foobar: Float[1];\n" +
                        "    hireDate : Date[1];\n" +
                        "    hireType : String[1];\n" +
                        "}");

        codeFiles.put(TEST_MAPPING_DOC_ID,
                "###Mapping\n" +
                        "Mapping vscodelsp::test::EmployeeMapping\n" +
                        "(\n" +
                        "   vscodelsp::test::Employee[emp] : Pure\n" +
                        "   {\n" +
                        "      ~src vscodelsp::test::EmployeeSrc\n" +
                        "      hireDate : today(),\n" +
                        "      hireType : 'FullTime'\n" +
                        "   }\n" +
                        ")");

        codeFiles.put(TEST_STORE_DOC_ID_1,
                "###Relational\n" +
                        "Database vscodelsp::test::TestDB1\n" +
                        "(\n" +
                        "   Table PersonTable\n" +
                        "   (\n" +
                        "       id INTEGER PRIMARY KEY,\n" +
                        "       firm_id INTEGER,\n" +
                        "       firstName VARCHAR(200),\n" +
                        "       lastName VARCHAR(200)\n" +
                        "   )\n" +
                        ")");

        codeFiles.put(TEST_CONNECTION_DOC_ID_1,
                "###Connection\n" +
                        "RelationalDatabaseConnection vscodelsp::test::LocalH2Connection1\n" +
                        "{\n" +
                        "   store: vscodelsp::test::TestDB1;\n" +
                        "   type: H2;\n" +
                        "   specification: LocalH2\n" +
                        "   {\n" +
                        "       testDataSetupSqls: [\n" +
                        "           'Drop table if exists FirmTable;\\nDrop table if exists PersonTable;\\nCreate Table FirmTable(id INT, Type VARCHAR(200), Legal_Name VARCHAR(200));\\nCreate Table PersonTable(id INT, firm_id INT, lastName VARCHAR(200), firstName VARCHAR(200));\\nInsert into FirmTable (id, Type, Legal_Name) values (1,\\'LLC\\',\\'FirmA\\');\\nInsert into FirmTable (id, Type, Legal_Name) values (2,\\'CORP\\',\\'Apple\\');\\nInsert into PersonTable (id, firm_id, lastName, firstName) values (1, 1, \\'John\\', \\'Doe\\');\\n\\n\\n'\n" +
                        "           ];\n" +
                        "   };\n" +
                        "   auth: DefaultH2;\n" +
                        "}");

        codeFiles.put(TEST_STORE_DOC_ID_2,
                "###Relational\n" +
                        "Database vscodelsp::test::TestDB2\n" +
                        "(\n" +
                        "   Table FirmTable\n" +
                        "   (\n" +
                        "       id INTEGER PRIMARY KEY,\n" +
                        "       Type VARCHAR(200),\n" +
                        "       Legal_name VARCHAR(200)\n" +
                        "   )\n" +
                        ")");

        codeFiles.put(TEST_CONNECTION_DOC_ID_2,
                "###Connection\n" +
                        "RelationalDatabaseConnection vscodelsp::test::LocalH2Connection2\n" +
                        "{\n" +
                        "   store: vscodelsp::test::TestDB2;\n" +
                        "   type: H2;\n" +
                        "   specification: LocalH2\n" +
                        "   {\n" +
                        "       testDataSetupSqls: [\n" +
                        "           'Drop table if exists FirmTable;\\nDrop table if exists PersonTable;\\nCreate Table FirmTable(id INT, Type VARCHAR(200), Legal_Name VARCHAR(200));\\nCreate Table PersonTable(id INT, firm_id INT, lastName VARCHAR(200), firstName VARCHAR(200));\\nInsert into FirmTable (id, Type, Legal_Name) values (1,\\'LLC\\',\\'FirmA\\');\\nInsert into FirmTable (id, Type, Legal_Name) values (2,\\'CORP\\',\\'Apple\\');\\nInsert into PersonTable (id, firm_id, lastName, firstName) values (1, 1, \\'John\\', \\'Doe\\');\\n\\n\\n'\n" +
                        "           ];\n" +
                        "   };\n" +
                        "   auth: DefaultH2;\n" +
                        "}");

        codeFiles.put(TEST_RUNTIME_DOC_ID,
                "###Runtime\n" +
                        "Runtime vscodelsp::test::H2Runtime\n" +
                        "{\n" +
                        "   mappings:\n" +
                        "   [\n" +
                        "       vscodelsp::test::EmployeeMapping\n" +
                        "   ];\n" +
                        "   connections:\n" +
                        "   [\n" +
                        "       vscodelsp::test::TestDB1:\n" +
                        "       [\n" +
                        "           connection_1: vscodelsp::test::LocalH2Connection1\n" +
                        "       ],\n" +
                        "       vscodelsp::test::TestDB2:\n" +
                        "       [\n" +
                        "           connection_2: vscodelsp::test::LocalH2Connection2\n" +
                        "       ]\n" +
                        "   ];\n" +
                        "}");

        codeFiles.put(TEST_SERVICE_DOC_ID,
                "###Service\n" +
                        "Service vscodelsp::test::TestService\n" +
                        "{\n" +
                        "    pattern : 'test';\n" +
                        "    documentation : 'service for testing';\n" +
                        "    execution : Single\n" +
                        "    {\n" +
                        "        query : src:vscodelsp::test::Employee[1] | $src.hireType;\n" +
                        "        mapping : vscodelsp::test::EmployeeMapping;\n" +
                        "        runtime : vscodelsp::test::H2Runtime;\n" +
                        "    }\n" +
                        "    test : Single" +
                        "    {\n" +
                        "        data : '';\n" +
                        "        asserts : [];\n" +
                        "    }\n" +
                        "}");

        return codeFiles;
    }

    @Test
    public void testGetReferenceResolvers()
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();

        LegendReference mappedMappingReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_SERVICE_DOC_ID, 8, 18, 8, 49))
                .withReferencedLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID, 1, 0, 9, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_SERVICE_DOC_ID, TextPosition.newPosition(8, 21), mappedMappingReference, "Within the mapping name has been mapped, referring to mapping definition");

        LegendReference mappedRuntimeReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_SERVICE_DOC_ID, 9, 18, 9, 43))
                .withReferencedLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 1, 0, 18, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_SERVICE_DOC_ID, TextPosition.newPosition(9, 41), mappedRuntimeReference, "Within the runtime name has been mapped, referring to runtime definition");
    }


    @Test
    public void testCommands()
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();
        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        SectionState sectionState = sectionStates.select(x -> x.getExtension() instanceof ServiceLSPGrammarExtension).getOnly();

        List<? extends LegendCommand> commands = Lists.mutable.ofAll(this.extension.getCommands(sectionState))
                .sortThis(Comparator.comparing(LegendCommand::getId).thenComparing(x -> x.getLocation().getTextInterval().getStart().getLine()));
        Set<String> expectedCommands = Set.of(FunctionExecutionSupport.EXECUTE_COMMAND_ID, ServiceLSPGrammarExtension.RUN_LEGACY_TESTS_COMMAND_ID);
        Set<String> actualCommands = Sets.mutable.empty();
        commands.forEach(c -> actualCommands.add(c.getId()));
        Assertions.assertEquals(expectedCommands, actualCommands);
    }
}
