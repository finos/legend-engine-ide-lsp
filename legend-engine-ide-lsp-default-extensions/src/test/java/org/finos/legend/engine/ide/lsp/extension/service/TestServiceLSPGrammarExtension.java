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

import com.fasterxml.jackson.core.type.TypeReference;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtensionTest;
import org.finos.legend.engine.ide.lsp.extension.CompileResult;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.core.FunctionExecutionSupport;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Kind;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Source;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommand;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestAssertionResult;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.SingleExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.result.DataTypeResultType;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.RuntimePointer;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Lambda;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.executionContext.BaseExecutionContext;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.executionContext.ExecutionContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.finos.legend.engine.ide.lsp.extension.core.FunctionExecutionSupport.*;

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
                LegendDeclaration.builder().withIdentifier("test::services::TestService").withClassifier("meta::legend::service::metamodel::Service").withLocation(DOC_ID_FOR_TEXT, 3, 0, 17, 0).build()
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
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT, 5, 12, 5, 17), "Unexpected token", Kind.Error, Source.Parser)
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
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT, 10, 18, 10, 44), "Can't find mapping 'test::mappings::TestMapping'", Kind.Error, Source.Compiler)
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

        codeFiles.put("vscodelsp::test::EmployeeRelational",
                "###Pure\n" +
                        "Class vscodelsp::test::EmployeeRelational\n" +
                        "{\n" +
                        "    id: Integer[1];\n" +
                        "    firmId : Integer[1];\n" +
                        "    firstName : String[1];\n" +
                        "    lastName : String[1];\n" +
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
                        ")\n" +
                        "Mapping vscodelsp::test::EmployeeRelationalMapping\n" +
                        "(\n" +
                        "   vscodelsp::test::EmployeeRelational[empRelational] : Relational\n" +
                        "   {\n" +
                        "       ~primaryKey\n" +
                        "       (\n" +
                        "           [vscodelsp::test::TestDB1]PersonTable.id\n" +
                        "       )\n" +
                        "       ~mainTable [vscodelsp::test::TestDB1]PersonTable\n" +
                        "       id: [vscodelsp::test::TestDB1]PersonTable.id,\n" +
                        "       firmId: [vscodelsp::test::TestDB1]PersonTable.firm_id,\n" +
                        "       firstName: [vscodelsp::test::TestDB1]PersonTable.firstName,\n" +
                        "       lastName: [vscodelsp::test::TestDB1]PersonTable.lastName\n" +
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
                        "}\n" +
                        "Runtime vscodelsp::test::H2RuntimeRelational\n" +
                        "{\n" +
                        "   mappings:\n" +
                        "   [\n" +
                        "       vscodelsp::test::EmployeeRelationalMapping\n" +
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
                        "Service vscodelsp::test::TestService1\n" +
                        "{\n" +
                        "    pattern : 'test1';\n" +
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
                        "}\n" +
                        "Service vscodelsp::test::TestService2\n" +
                        "{\n" +
                        "    pattern : 'test2';\n" +
                        "    documentation : 'service for testing';\n" +
                        "    execution : Single\n" +
                        "    {\n" +
                        "        query : testParam: String[1]|vscodelsp::test::EmployeeRelational.all()->project(\n" +
                        "                  [ x|$x.firstName ],\n" +
                        "                  [ 'First Name' ]\n" +
                        "        );\n" +
                        "        mapping : vscodelsp::test::EmployeeRelationalMapping;\n" +
                        "        runtime : vscodelsp::test::H2RuntimeRelational;\n" +
                        "    }\n" +
                        "}\n" +
                        "Service test::service\n" +
                        "{\n" +
                        "    pattern: 'url/myUrl/';\n" +
                        "    owners: ['ownerName'];\n" +
                        "    documentation: 'test';\n" +
                        "    autoActivateUpdates: true;\n" +
                        "    execution: Multi\n" +
                        "    {\n" +
                        "        query: src:vscodelsp::test::Employee[1] | $src.hireType;\n" +
                        "        key: 'env';\n" +
                        "        executions['default']:\n" +
                        "        {\n" +
                        "           mapping: vscodelsp::test::EmployeeMapping;\n" +
                        "           runtime: vscodelsp::test::H2Runtime;\n" +
                        "        }\n" +
                        "    }\n" +
                        "}");

        return codeFiles;
    }

    private MutableMap<String, String> getCodeFilesForPostValidations()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("test::class",
                "###Pure\n" +
                        "Class test::class\n" +
                        "{\n" +
                        "    prop1: String[1];\n" +
                        "}");

        codeFiles.put("test::mapping",
                "###Mapping\n" +
                        "Mapping test::mapping\n" +
                        "(\n" +
                        ")");

        codeFiles.put("test::connection",
                "###Connection\n" +
                        "JsonModelConnection test::connection\n" +
                        "{\n" +
                        "    class: test::class;\n" +
                        "    url: 'asd';\n" +
                        "}");

        codeFiles.put("test::runtime",
                "###Runtime\n" +
                        "Runtime test::runtime\n" +
                        "{\n" +
                        "    mappings: [test::mapping];\n" +
                        "}");

        codeFiles.put("test::service",
                "###Service\n" +
                        "Service test::service\n" +
                        "{\n" +
                        "    pattern: 'url/myUrl/';\n" +
                        "    owners: ['ownerName'];\n" +
                        "    documentation: 'test';\n" +
                        "    autoActivateUpdates: true;\n" +
                        "    execution: Single\n" +
                        "    {\n" +
                        "        query: test::class.all()->project([col(p|$p.prop1, 'prop1')]);\n" +
                        "        mapping: test::mapping;\n" +
                        "        runtime: test::runtime;\n" +
                        "    }\n" +
                        "    postValidations:\n" +
                        "    [\n" +
                        "        {\n" +
                        "            description: 'A good description of the validation';\n" +
                        "            params: [];\n" +
                        "            assertions: [\n" +
                        "                testAssert: tds: TabularDataSet[1]|$tds->filter(row|$row.getString('prop1')->startsWith('X'))->meta::legend::service::validation::assertTabularDataSetEmpty('Expected no prop1 values to begin with the letter X');\n" +
                        "            ];\n" +
                        "        }\n" +
                        "    ]\n" +
                        "}");

        return codeFiles;
    }

    @Test
    public void testGetReferenceResolvers()
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();

        LegendReference mappedMappingReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_SERVICE_DOC_ID, 8, 18, 8, 49))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID, 1, 0, 9, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_SERVICE_DOC_ID, TextPosition.newPosition(8, 21), mappedMappingReference, "Within the mapping name has been mapped, referring to mapping definition");

        LegendReference mappedRuntimeReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_SERVICE_DOC_ID, 9, 18, 9, 43))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 1, 0, 18, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_SERVICE_DOC_ID, TextPosition.newPosition(9, 41), mappedRuntimeReference, "Within the runtime name has been mapped, referring to runtime definition");
    }

    @Test
    public void testGetReferenceResolversExecutionLambda()
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesForPostValidations();
        LegendReference mappedClassPropertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("test::service", 9, 52, 9, 56))
                .withDeclarationLocation(TextLocation.newTextSource("test::class", 3, 4, 3, 20))
                .build();

        testReferenceLookup(codeFiles, "test::service", TextPosition.newPosition(9, 54), mappedClassPropertyReference, "Within the class property has been mapped, referring to class property definition");
    }

    @Test
    @Disabled("Enable once m3 source information is fixed")
    public void testGetReferenceResolversPostValidations()
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesForPostValidations();
        LegendReference mappedClassPropertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("test::service", 19, 68, 19, 71))
                .withDeclarationLocation(TextLocation.newTextSource("test::service", 19, 64, 19, 66))
                .build();

        testReferenceLookup(codeFiles, "test::service", TextPosition.newPosition(19, 70), mappedClassPropertyReference, "Within the lambda variable has been mapped, referring to lambda variable definition");
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

        LegendCommand singleServiceCommand = commands.stream().filter(x -> x.getId().equals(FunctionExecutionSupport.EXECUTE_COMMAND_ID) && x.getEntity().equals("vscodelsp::test::TestService1")).findAny().orElseThrow();
        LegendCommand multiServiceCommand = commands.stream().filter(x -> x.getId().equals(FunctionExecutionSupport.EXECUTE_COMMAND_ID) && x.getEntity().equals("test::service")).findAny().orElseThrow();

        Assertions.assertEquals(Set.of("src"), singleServiceCommand.getInputParameters().keySet());
        Assertions.assertEquals(Set.of("env", "src"), multiServiceCommand.getInputParameters().keySet());
    }

    @Test
    void serviceTestsExecution()
    {
        String data =
                "###Data\n" +
                        "Data testServiceStoreTestSuites::TestData\n" +
                        "{\n" +
                        "   ExternalFormat\n" +
                        "   #{\n" +
                        "       contentType: 'application/json';\n" +
                        "       data: '{\\n  \"sFirm_tbl\": {\\n    \"legalName\": \"legalName 18\",\\n    \"firmId\": 22,\\n    \"ceoId\": 49,\\n    \"addressId\": 88,\\n    \"employees\": {\\n      \"firstName\": \"firstName 69\",\\n      \"lastName\": \"lastName 2\",\\n      \"age\": 14,\\n      \"id\": 52,\\n      \"addressId\": 83,\\n      \"firmId\": 73\\n    }\\n  },\\n  \"sPerson_tbl\": {\\n    \"firstName\": \"firstName 69\",\\n    \"lastName\": \"lastName 4\",\\n    \"age\": 98,\\n    \"id\": 87,\\n    \"addressId\": 46,\\n    \"firmId\": 26\\n  }\\n}';\n" +
                        "   }#\n" +
                        "}\n" +
                        "Data testServiceStoreTestSuites::TestData2\n" +
                        "{\n" +
                        "   ExternalFormat\n" +
                        "   #{\n" +
                        "       contentType: 'application/json';\n" +
                        "       data: '{\\n  \"sFirm_tbl\": {\\n    \"legalName\": \"legalName 18\",\\n    \"firmId\": 22,\\n    \"ceoId\": 49,\\n    \"addressId\": 88,\\n    \"employees\": {\\n      \"firstName\": \"firstName 69\",\\n      \"lastName\": \"lastName 2\",\\n      \"age\": 14,\\n      \"id\": 52,\\n      \"addressId\": 83,\\n      \"firmId\": 73\\n    }\\n  },\\n  \"sPerson_tbl\": {\\n    \"firstName\": \"firstName 69\",\\n    \"lastName\": \"lastName 4\",\\n    \"age\": 98,\\n    \"id\": 87,\\n    \"addressId\": 46,\\n    \"firmId\": 26\\n  }\\n}';\n" +
                        "   }#\n" +
                        "}\n" +
                        "Data testServiceStoreTestSuites::TestData3\n" +
                        "{\n" +
                        "   ExternalFormat\n" +
                        "   #{\n" +
                        "       contentType: 'application/json';\n" +
                        "       data: '{\\n  \"sFirm_tbl\": {\\n    \"legalName\": \"legalName 19\",\\n    \"firmId\": 22,\\n    \"ceoId\": 49,\\n    \"addressId\": 88,\\n    \"employees\": {\\n      \"firstName\": \"firstName 69\",\\n      \"lastName\": \"lastName 2\",\\n      \"age\": 14,\\n      \"id\": 52,\\n      \"addressId\": 83,\\n      \"firmId\": 73\\n    }\\n  },\\n  \"sPerson_tbl\": {\\n    \"firstName\": \"firstName 69\",\\n    \"lastName\": \"lastName 4\",\\n    \"age\": 98,\\n    \"id\": 87,\\n    \"addressId\": 46,\\n    \"firmId\": 26\\n  }\\n}';\n" +
                        "   }#\n" +
                        "}\n";

        String model =
                "###Pure\n" +
                        "Class testModelStoreTestSuites::model::Doc\n" +
                        "{\n" +
                        "  firm_tbl: testModelStoreTestSuites::model::Firm_TBL[1];\n" +
                        "  person_tbl: testModelStoreTestSuites::model::Person_TBL[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class testModelStoreTestSuites::model::Firm_TBL\n" +
                        "{\n" +
                        "  legalName: String[1];\n" +
                        "  <<equality.Key>> firmId: Integer[1];\n" +
                        "  ceoId: Integer[1];\n" +
                        "  addressId: Integer[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class testModelStoreTestSuites::model::Person_TBL\n" +
                        "{\n" +
                        "  firstName: String[1];\n" +
                        "  lastName: String[1];\n" +
                        "  age: Integer[1];\n" +
                        "  <<equality.Key>> id: Integer[1];\n" +
                        "  addressId: Integer[1];\n" +
                        "  firmId: Integer[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class testModelStoreTestSuites::model::sDoc\n" +
                        "{\n" +
                        "  sFirm_tbl: testModelStoreTestSuites::model::sFirm_TBL[1];\n" +
                        "  sPerson_tbl: testModelStoreTestSuites::model::sPerson_TBL[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class testModelStoreTestSuites::model::sFirm_TBL\n" +
                        "{\n" +
                        "  legalName: String[1];\n" +
                        "  firmId: Integer[1];\n" +
                        "  ceoId: Integer[1];\n" +
                        "  addressId: Integer[1];\n" +
                        "  employees: testModelStoreTestSuites::model::sPerson_TBL[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class testModelStoreTestSuites::model::sPerson_TBL\n" +
                        "{\n" +
                        "  firstName: String[1];\n" +
                        "  lastName: String[1];\n" +
                        "  age: Integer[1];\n" +
                        "  id: Integer[1];\n" +
                        "  addressId: Integer[1];\n" +
                        "  firmId: Integer[1];\n" +
                        "}\n";

        String mapping =
                "###Mapping\n" +
                        "Mapping testModelStoreTestSuites::mapping::DocM2MMapping\n" +
                        "(\n" +
                        "  *testModelStoreTestSuites::model::Doc: Pure\n" +
                        "  {\n" +
                        "    ~src testModelStoreTestSuites::model::sDoc\n" +
                        "    firm_tbl: $src.sFirm_tbl,\n" +
                        "    person_tbl: $src.sPerson_tbl\n" +
                        "  }\n" +
                        "  *testModelStoreTestSuites::model::Firm_TBL: Pure\n" +
                        "  {\n" +
                        "    ~src testModelStoreTestSuites::model::sFirm_TBL\n" +
                        "    legalName: $src.legalName,\n" +
                        "    firmId: $src.firmId,\n" +
                        "    ceoId: $src.ceoId,\n" +
                        "    addressId: $src.addressId\n" +
                        "  }\n" +
                        "  *testModelStoreTestSuites::model::Person_TBL: Pure\n" +
                        "  {\n" +
                        "    ~src testModelStoreTestSuites::model::sPerson_TBL\n" +
                        "    firstName: $src.firstName,\n" +
                        "    lastName: $src.lastName,\n" +
                        "    age: $src.age,\n" +
                        "    id: $src.id,\n" +
                        "    addressId: $src.addressId,\n" +
                        "    firmId: $src.firmId\n" +
                        "  }\n" +
                        ")\n" +
                        "\n" +
                        "Mapping testModelStoreTestSuites::mapping::DocM2MMapping2\n" +
                        "(\n" +
                        "  *testModelStoreTestSuites::model::Doc: Pure\n" +
                        "  {\n" +
                        "    ~src testModelStoreTestSuites::model::sDoc\n" +
                        "    firm_tbl: $src.sFirm_tbl,\n" +
                        "    person_tbl: $src.sPerson_tbl\n" +
                        "  }\n" +
                        "  *testModelStoreTestSuites::model::Firm_TBL: Pure\n" +
                        "  {\n" +
                        "    ~src testModelStoreTestSuites::model::sFirm_TBL\n" +
                        "    legalName: $src.legalName->toUpper(),\n" +
                        "    firmId: $src.firmId,\n" +
                        "    ceoId: $src.ceoId,\n" +
                        "    addressId: $src.addressId\n" +
                        "  }\n" +
                        "  *testModelStoreTestSuites::model::Person_TBL: Pure\n" +
                        "  {\n" +
                        "    ~src testModelStoreTestSuites::model::sPerson_TBL\n" +
                        "    firstName: $src.firstName,\n" +
                        "    lastName: $src.lastName,\n" +
                        "    age: $src.age,\n" +
                        "    id: $src.id,\n" +
                        "    addressId: $src.addressId,\n" +
                        "    firmId: $src.firmId\n" +
                        "  }\n" +
                        ")\n";

        String runtime =
                "###Runtime\n" +
                        "Runtime testModelStoreTestSuites::runtime::DocM2MRuntime\n" +
                        "{\n" +
                        "  mappings:\n" +
                        "  [\n" +
                        "    testModelStoreTestSuites::mapping::DocM2MMapping\n" +
                        "  ];\n" +
                        "  connections:\n" +
                        "  [\n" +
                        "    ModelStore:\n" +
                        "    [\n" +
                        "      connection_1:\n" +
                        "      #{\n" +
                        "        JsonModelConnection\n" +
                        "        {\n" +
                        "          class: testModelStoreTestSuites::model::sDoc;\n" +
                        "          url: 'executor:default';\n" +
                        "        }\n" +
                        "      }#\n" +
                        "    ]\n" +
                        "  ];\n" +
                        "}\n" +
                        "\n" +
                        "Runtime testModelStoreTestSuites::runtime::DocM2MRuntime2\n" +
                        "{\n" +
                        "  mappings:\n" +
                        "  [\n" +
                        "    testModelStoreTestSuites::mapping::DocM2MMapping\n" +
                        "  ];\n" +
                        "  connections:\n" +
                        "  [\n" +
                        "    ModelStore:\n" +
                        "    [\n" +
                        "      connection_1:\n" +
                        "      #{\n" +
                        "        JsonModelConnection\n" +
                        "        {\n" +
                        "          class: testModelStoreTestSuites::model::sDoc;\n" +
                        "          url: 'executor:default';\n" +
                        "        }\n" +
                        "      }#\n" +
                        "    ]\n" +
                        "  ];\n" +
                        "}\n" +
                        "\n" +
                        "Runtime testModelStoreTestSuites::runtime::DocM2MRuntime3\n" +
                        "{\n" +
                        "  mappings:\n" +
                        "  [\n" +
                        "    testModelStoreTestSuites::mapping::DocM2MMapping\n" +
                        "  ];\n" +
                        "  connections:\n" +
                        "  [\n" +
                        "    ModelStore:\n" +
                        "    [\n" +
                        "      connection_2:\n" +
                        "      #{\n" +
                        "        JsonModelConnection\n" +
                        "        {\n" +
                        "          class: testModelStoreTestSuites::model::sDoc;\n" +
                        "          url: 'executor:default';\n" +
                        "        }\n" +
                        "      }#\n" +
                        "    ]\n" +
                        "  ];\n" +
                        "}\n";

        String services =
                "###Service\n" +
                        "Service testModelStoreTestSuites::service::DocM2MService\n" +
                        "{\n" +
                        "  pattern: '/testModelStoreTestSuites/service';\n" +
                        "  owners:\n" +
                        "  [\n" +
                        "    'dummy',\n" +
                        "    'dummy1'\n" +
                        "  ];\n" +
                        "  documentation: 'Service to test refiner flow';\n" +
                        "  autoActivateUpdates: true;\n" +
                        "  execution: Multi\n" +
                        "  {\n" +
                        "    query: |testModelStoreTestSuites::model::Doc.all()->graphFetchChecked(#{testModelStoreTestSuites::model::Doc{firm_tbl{addressId,firmId,legalName,ceoId},person_tbl{addressId,age,firmId,firstName,id,lastName}}}#)->serialize(#{testModelStoreTestSuites::model::Doc{firm_tbl{addressId,firmId,legalName,ceoId},person_tbl{addressId,age,firmId,firstName,id,lastName}}}#);\n" +
                        "    key: 'env';\n" +
                        "    executions['PASS']:\n" +
                        "    {\n" +
                        "      mapping: testModelStoreTestSuites::mapping::DocM2MMapping;\n" +
                        "      runtime: testModelStoreTestSuites::runtime::DocM2MRuntime;\n" +
                        "    }\n" +
                        "    executions['FAIL']:\n" +
                        "    {\n" +
                        "      mapping: testModelStoreTestSuites::mapping::DocM2MMapping2;\n" +
                        "      runtime: testModelStoreTestSuites::runtime::DocM2MRuntime2;\n" +
                        "    }\n" +
                        "  }\n" +
                        "  testSuites:\n" +
                        "  [\n" +
                        "    testSuite1:\n" +
                        "    {\n" +
                        "      data:\n" +
                        "      [\n" +
                        "        connections:\n" +
                        "        [\n" +
                        "          connection_1:\n" +
                        "            Reference \n" +
                        "            #{ \n" +
                        "              testServiceStoreTestSuites::TestData \n" +
                        "            }#\n" +
                        "        ]\n" +
                        "      ]\n" +
                        "      tests:\n" +
                        "      [\n" +
                        "        noParameterTest:\n" +
                        "        {\n" +
                        "          serializationFormat: PURE;\n" +
                        "          asserts:\n" +
                        "          [\n" +
                        "            assert1:\n" +
                        "              EqualToJson\n" +
                        "              #{\n" +
                        "                expected:\n" +
                        "                  ExternalFormat\n" +
                        "                  #{\n" +
                        "                    contentType: 'application/json';\n" +
                        "                    data: '{\"defects\":[],\"source\":{\"defects\":[],\"source\":{\"number\":1,\"record\":\"{\\\\\"sFirm_tbl\\\\\":{\\\\\"legalName\\\\\":\\\\\"legalName 18\\\\\",\\\\\"firmId\\\\\":22,\\\\\"ceoId\\\\\":49,\\\\\"addressId\\\\\":88,\\\\\"employees\\\\\":{\\\\\"firstName\\\\\":\\\\\"firstName 69\\\\\",\\\\\"lastName\\\\\":\\\\\"lastName 2\\\\\",\\\\\"age\\\\\":14,\\\\\"id\\\\\":52,\\\\\"addressId\\\\\":83,\\\\\"firmId\\\\\":73}},\\\\\"sPerson_tbl\\\\\":{\\\\\"firstName\\\\\":\\\\\"firstName 69\\\\\",\\\\\"lastName\\\\\":\\\\\"lastName 4\\\\\",\\\\\"age\\\\\":98,\\\\\"id\\\\\":87,\\\\\"addressId\\\\\":46,\\\\\"firmId\\\\\":26}}\"},\"value\":{\"sFirm_tbl\":{\"addressId\":88,\"firmId\":22,\"legalName\":\"legalName 18\",\"ceoId\":49},\"sPerson_tbl\":{\"addressId\":46,\"age\":98,\"firmId\":26,\"firstName\":\"firstName 69\",\"id\":87,\"lastName\":\"lastName 4\"}}},\"value\":{\"firm_tbl\":{\"addressId\":88,\"firmId\":22,\"legalName\":\"legalName 18\",\"ceoId\":49},\"person_tbl\":{\"addressId\":46,\"age\":98,\"firmId\":26,\"firstName\":\"firstName 69\",\"id\":87,\"lastName\":\"lastName 4\"}}}';\n" +
                        "                  }#;\n" +
                        "              }#\n" +
                        "          ]\n" +
                        "        },\n" +
                        "        failEnvParameter:\n" +
                        "        {\n" +
                        "          serializationFormat: PURE;\n" +
                        "          parameters: [ env = 'FAIL' ]\n" +
                        "          asserts:\n" +
                        "          [\n" +
                        "            assert1:\n" +
                        "              EqualToJson\n" +
                        "              #{\n" +
                        "                expected:\n" +
                        "                  ExternalFormat\n" +
                        "                  #{\n" +
                        "                    contentType: 'application/json';\n" +
                        "                    data: '{\"defects\":[],\"source\":{\"defects\":[],\"source\":{\"number\":1,\"record\":\"{\\\\\"sFirm_tbl\\\\\":{\\\\\"legalName\\\\\":\\\\\"legalName 18\\\\\",\\\\\"firmId\\\\\":22,\\\\\"ceoId\\\\\":49,\\\\\"addressId\\\\\":88,\\\\\"employees\\\\\":{\\\\\"firstName\\\\\":\\\\\"firstName 69\\\\\",\\\\\"lastName\\\\\":\\\\\"lastName 2\\\\\",\\\\\"age\\\\\":14,\\\\\"id\\\\\":52,\\\\\"addressId\\\\\":83,\\\\\"firmId\\\\\":73}},\\\\\"sPerson_tbl\\\\\":{\\\\\"firstName\\\\\":\\\\\"firstName 69\\\\\",\\\\\"lastName\\\\\":\\\\\"lastName 4\\\\\",\\\\\"age\\\\\":98,\\\\\"id\\\\\":87,\\\\\"addressId\\\\\":46,\\\\\"firmId\\\\\":26}}\"},\"value\":{\"sFirm_tbl\":{\"addressId\":88,\"firmId\":22,\"legalName\":\"legalName 18\",\"ceoId\":49},\"sPerson_tbl\":{\"addressId\":46,\"age\":98,\"firmId\":26,\"firstName\":\"firstName 69\",\"id\":87,\"lastName\":\"lastName 4\"}}},\"value\":{\"firm_tbl\":{\"addressId\":88,\"firmId\":22,\"legalName\":\"legalName 18\",\"ceoId\":49},\"person_tbl\":{\"addressId\":46,\"age\":98,\"firmId\":26,\"firstName\":\"firstName 69\",\"id\":87,\"lastName\":\"lastName 4\"}}}';\n" +
                        "                  }#;\n" +
                        "              }#\n" +
                        "          ]\n" +
                        "        },\n" +
                        "        multiEnvParameter:\n" +
                        "        {\n" +
                        "          serializationFormat: PURE;\n" +
                        "          parameters: [ env = ['PASS', 'FAIL'] ]\n" +
                        "          asserts:\n" +
                        "          [\n" +
                        "            assert1:\n" +
                        "              EqualToJson\n" +
                        "              #{\n" +
                        "                expected:\n" +
                        "                  ExternalFormat\n" +
                        "                  #{\n" +
                        "                    contentType: 'application/json';\n" +
                        "                    data: '{\"defects\":[],\"source\":{\"defects\":[],\"source\":{\"number\":1,\"record\":\"{\\\\\"sFirm_tbl\\\\\":{\\\\\"legalName\\\\\":\\\\\"legalName 18\\\\\",\\\\\"firmId\\\\\":22,\\\\\"ceoId\\\\\":49,\\\\\"addressId\\\\\":88,\\\\\"employees\\\\\":{\\\\\"firstName\\\\\":\\\\\"firstName 69\\\\\",\\\\\"lastName\\\\\":\\\\\"lastName 2\\\\\",\\\\\"age\\\\\":14,\\\\\"id\\\\\":52,\\\\\"addressId\\\\\":83,\\\\\"firmId\\\\\":73}},\\\\\"sPerson_tbl\\\\\":{\\\\\"firstName\\\\\":\\\\\"firstName 69\\\\\",\\\\\"lastName\\\\\":\\\\\"lastName 4\\\\\",\\\\\"age\\\\\":98,\\\\\"id\\\\\":87,\\\\\"addressId\\\\\":46,\\\\\"firmId\\\\\":26}}\"},\"value\":{\"sFirm_tbl\":{\"addressId\":88,\"firmId\":22,\"legalName\":\"legalName 18\",\"ceoId\":49},\"sPerson_tbl\":{\"addressId\":46,\"age\":98,\"firmId\":26,\"firstName\":\"firstName 69\",\"id\":87,\"lastName\":\"lastName 4\"}}},\"value\":{\"firm_tbl\":{\"addressId\":88,\"firmId\":22,\"legalName\":\"legalName 18\",\"ceoId\":49},\"person_tbl\":{\"addressId\":46,\"age\":98,\"firmId\":26,\"firstName\":\"firstName 69\",\"id\":87,\"lastName\":\"lastName 4\"}}}';\n" +
                        "                  }#;\n" +
                        "              }#\n" +
                        "          ]\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}\n" +
                        "\n" +
                        "\n";

        MutableMap<String, String> sections = Maps.mutable.of(
                "data.pure", data,
                "mapping.pure", mapping,
                "model.pure", model,
                "runtime.pure", runtime
        ).withKeyValue("service.pure", services);

        List<SectionState> sectionStates = newSectionStates(sections);

        SectionState sectionState = sectionStates.stream().filter(x -> x.getDocumentState().getDocumentId().equals("service.pure")).findAny().orElseThrow();

        TextLocation location = TextLocation.newTextSource("service.pure", TextInterval.newInterval(1, 1, 1, 5));

        String failureExpectedValue = "{\n" +
                "  \"defects\" : [ ],\n" +
                "  \"source\" : {\n" +
                "    \"defects\" : [ ],\n" +
                "    \"source\" : {\n" +
                "      \"number\" : 1,\n" +
                "      \"record\" : \"{\\\"sFirm_tbl\\\":{\\\"legalName\\\":\\\"legalName 18\\\",\\\"firmId\\\":22,\\\"ceoId\\\":49,\\\"addressId\\\":88,\\\"employees\\\":{\\\"firstName\\\":\\\"firstName 69\\\",\\\"lastName\\\":\\\"lastName 2\\\",\\\"age\\\":14,\\\"id\\\":52,\\\"addressId\\\":83,\\\"firmId\\\":73}},\\\"sPerson_tbl\\\":{\\\"firstName\\\":\\\"firstName 69\\\",\\\"lastName\\\":\\\"lastName 4\\\",\\\"age\\\":98,\\\"id\\\":87,\\\"addressId\\\":46,\\\"firmId\\\":26}}\"\n" +
                "    },\n" +
                "    \"value\" : {\n" +
                "      \"sFirm_tbl\" : {\n" +
                "        \"addressId\" : 88,\n" +
                "        \"firmId\" : 22,\n" +
                "        \"legalName\" : \"legalName 18\",\n" +
                "        \"ceoId\" : 49\n" +
                "      },\n" +
                "      \"sPerson_tbl\" : {\n" +
                "        \"addressId\" : 46,\n" +
                "        \"age\" : 98,\n" +
                "        \"firmId\" : 26,\n" +
                "        \"firstName\" : \"firstName 69\",\n" +
                "        \"id\" : 87,\n" +
                "        \"lastName\" : \"lastName 4\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"value\" : {\n" +
                "    \"firm_tbl\" : {\n" +
                "      \"addressId\" : 88,\n" +
                "      \"firmId\" : 22,\n" +
                "      \"legalName\" : \"legalName 18\",\n" +
                "      \"ceoId\" : 49\n" +
                "    },\n" +
                "    \"person_tbl\" : {\n" +
                "      \"addressId\" : 46,\n" +
                "      \"age\" : 98,\n" +
                "      \"firmId\" : 26,\n" +
                "      \"firstName\" : \"firstName 69\",\n" +
                "      \"id\" : 87,\n" +
                "      \"lastName\" : \"lastName 4\"\n" +
                "    }\n" +
                "  }\n" +
                "}";
        String failureActualValue = "{\n" +
                "  \"defects\" : [ ],\n" +
                "  \"source\" : {\n" +
                "    \"defects\" : [ ],\n" +
                "    \"source\" : {\n" +
                "      \"number\" : 1,\n" +
                "      \"record\" : \"{\\\"sFirm_tbl\\\":{\\\"legalName\\\":\\\"legalName 18\\\",\\\"firmId\\\":22,\\\"ceoId\\\":49,\\\"addressId\\\":88,\\\"employees\\\":{\\\"firstName\\\":\\\"firstName 69\\\",\\\"lastName\\\":\\\"lastName 2\\\",\\\"age\\\":14,\\\"id\\\":52,\\\"addressId\\\":83,\\\"firmId\\\":73}},\\\"sPerson_tbl\\\":{\\\"firstName\\\":\\\"firstName 69\\\",\\\"lastName\\\":\\\"lastName 4\\\",\\\"age\\\":98,\\\"id\\\":87,\\\"addressId\\\":46,\\\"firmId\\\":26}}\"\n" +
                "    },\n" +
                "    \"value\" : {\n" +
                "      \"sFirm_tbl\" : {\n" +
                "        \"addressId\" : 88,\n" +
                "        \"ceoId\" : 49,\n" +
                "        \"firmId\" : 22,\n" +
                "        \"legalName\" : \"legalName 18\"\n" +
                "      },\n" +
                "      \"sPerson_tbl\" : {\n" +
                "        \"addressId\" : 46,\n" +
                "        \"age\" : 98,\n" +
                "        \"firmId\" : 26,\n" +
                "        \"firstName\" : \"firstName 69\",\n" +
                "        \"id\" : 87,\n" +
                "        \"lastName\" : \"lastName 4\"\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"value\" : {\n" +
                "    \"firm_tbl\" : {\n" +
                "      \"addressId\" : 88,\n" +
                "      \"firmId\" : 22,\n" +
                "      \"legalName\" : \"LEGALNAME 18\",\n" +
                "      \"ceoId\" : 49\n" +
                "    },\n" +
                "    \"person_tbl\" : {\n" +
                "      \"addressId\" : 46,\n" +
                "      \"age\" : 98,\n" +
                "      \"firmId\" : 26,\n" +
                "      \"firstName\" : \"firstName 69\",\n" +
                "      \"id\" : 87,\n" +
                "      \"lastName\" : \"lastName 4\"\n" +
                "    }\n" +
                "  }\n" +
                "}";

        LegendTestAssertionResult test1FailAssertionResult = LegendTestAssertionResult.failure("assert1",
                TextLocation.newTextSource("service.pure", TextInterval.newInterval(49, 14, 57, 15)),
                "Actual result does not match Expected result",
                failureExpectedValue.replace("\n", System.lineSeparator()),
                failureActualValue.replace("\n", System.lineSeparator())
        );

        LegendTestAssertionResult test2FailAssertionResult = LegendTestAssertionResult.failure("assert1",
                TextLocation.newTextSource("service.pure", TextInterval.newInterval(67, 14, 75, 15)),
                "Actual result does not match Expected result",
                failureExpectedValue.replace("\n", System.lineSeparator()),
                failureActualValue.replace("\n", System.lineSeparator())
        );

        LegendTestAssertionResult test3FailAssertionResult = LegendTestAssertionResult.failure("assert1",
                TextLocation.newTextSource("service.pure", TextInterval.newInterval(85, 14, 93, 15)),
                "Actual result does not match Expected result",
                failureExpectedValue.replace("\n", System.lineSeparator()),
                failureActualValue.replace("\n", System.lineSeparator())
        );

        LegendTestExecutionResult test1PassResult = LegendTestExecutionResult.success("testModelStoreTestSuites::service::DocM2MService.testSuite1.noParameterTest[PASS]");
        LegendTestExecutionResult test1FailResult = LegendTestExecutionResult.failures(List.of(test1FailAssertionResult), "testModelStoreTestSuites::service::DocM2MService.testSuite1.noParameterTest[FAIL]");
        LegendTestExecutionResult test2FailResult = LegendTestExecutionResult.failures(List.of(test2FailAssertionResult), "testModelStoreTestSuites::service::DocM2MService.testSuite1.failEnvParameter[FAIL]");
        LegendTestExecutionResult test3PassResult = LegendTestExecutionResult.success("testModelStoreTestSuites::service::DocM2MService.testSuite1.multiEnvParameter[PASS]");
        LegendTestExecutionResult test3FailResult = LegendTestExecutionResult.failures(List.of(test3FailAssertionResult), "testModelStoreTestSuites::service::DocM2MService.testSuite1.multiEnvParameter[FAIL]");

        // all test
        assertTestExecution("testModelStoreTestSuites::service::DocM2MService", Set.of(), sectionState, location, List.of(test1FailResult, test1PassResult, test2FailResult, test3PassResult, test3FailResult));
        // skip suite
        assertTestExecution("testModelStoreTestSuites::service::DocM2MService", Set.of("testModelStoreTestSuites::service::DocM2MService"), sectionState, location, List.of());
        // skip one test
        assertTestExecution("testModelStoreTestSuites::service::DocM2MService", Set.of("testModelStoreTestSuites::service::DocM2MService.testSuite1.noParameterTest[FAIL]"), sectionState, location, List.of(test1PassResult, test2FailResult, test3PassResult, test3FailResult));
        // skip a different test
        assertTestExecution("testModelStoreTestSuites::service::DocM2MService", Set.of("testModelStoreTestSuites::service::DocM2MService.testSuite1.noParameterTest[PASS]"), sectionState, location, List.of(test1FailResult, test2FailResult, test3PassResult, test3FailResult));
        // skip two tests
        assertTestExecution("testModelStoreTestSuites::service::DocM2MService", Set.of("testModelStoreTestSuites::service::DocM2MService.testSuite1.noParameterTest[PASS]", "testModelStoreTestSuites::service::DocM2MService.testSuite1.multiEnvParameter[PASS]"), sectionState, location, List.of(test1FailResult, test2FailResult, test3FailResult));
        // execute the suite
        assertTestExecution("testModelStoreTestSuites::service::DocM2MService.testSuite1", Set.of(), sectionState, location, List.of(test1FailResult, test1PassResult, test2FailResult, test3PassResult, test3FailResult));
        // execute a test directly
        assertTestExecution("testModelStoreTestSuites::service::DocM2MService.testSuite1.multiEnvParameter[PASS]", Set.of(), sectionState, location, List.of(test3PassResult));
    }

    private void assertTestExecution(String testId, Set<String> exclusions, SectionState sectionState, TextLocation location, List<LegendTestExecutionResult> expectedResults)
    {
        Comparator<LegendTestExecutionResult> executionResultComparator = Comparator.comparing(LegendTestExecutionResult::getId);

        List<LegendTestExecutionResult> legendTestExecutionResults = this.extension.executeTests(sectionState, location, testId, exclusions);

        Assertions.assertEquals(expectedResults.stream().sorted(executionResultComparator).collect(Collectors.toList()),
                legendTestExecutionResults.stream().sorted(executionResultComparator).collect(Collectors.toList())
        );
    }

    @Test
    public void testGetExecutionPlan() throws Exception
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();
        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        SectionState sectionState =
                sectionStates.select(x -> x.getExtension() instanceof ServiceLSPGrammarExtension).getOnly();
        CompileResult compileResult = extension.getCompileResult(sectionState);
        PackageableElement serviceElement =
                compileResult.getPureModelContextData().getElements().stream().filter(x -> x.getPath().equals(
                        "vscodelsp::test::TestService1")).findFirst().orElseThrow();
        Lambda lambda = extension.getLambda(serviceElement);
        RuntimePointer runtime = new RuntimePointer();
        runtime.runtime = "vscodelsp::test::H2Runtime";
        ExecutionContext context = new BaseExecutionContext();
        Map<String, String> executableArgs = Map.of("lambda", objectMapper.writeValueAsString(lambda), "mapping",
                "vscodelsp::test::EmployeeMapping", "runtime", objectMapper.writeValueAsString(runtime), "context",
                objectMapper.writeValueAsString(context));

        Iterable<? extends LegendExecutionResult> actual = testCommand(sectionState, "vscodelsp::test::TestService1",
                GENERATE_EXECUTION_PLAN_ID, executableArgs);

        Assertions.assertEquals(1, Iterate.sizeOf(actual));
        LegendExecutionResult result = actual.iterator().next();
        Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, result.getType(), result.getMessage());
        SingleExecutionPlan actualPlan = objectMapper.readValue(result.getMessage(), SingleExecutionPlan.class);
        Assertions.assertInstanceOf(DataTypeResultType.class, actualPlan.rootExecutionNode.resultType);
    }

    @Test
    public void testDebugExecutionPlan() throws Exception
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();
        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        SectionState sectionState =
                sectionStates.select(x -> x.getExtension() instanceof ServiceLSPGrammarExtension).getOnly();
        CompileResult compileResult = extension.getCompileResult(sectionState);
        PackageableElement serviceElement =
                compileResult.getPureModelContextData().getElements().stream().filter(x -> x.getPath().equals(
                        "vscodelsp::test::TestService1")).findFirst().orElseThrow();
        Lambda lambda = extension.getLambda(serviceElement);
        RuntimePointer runtime = new RuntimePointer();
        runtime.runtime = "vscodelsp::test::H2Runtime";
        ExecutionContext context = new BaseExecutionContext();
        Map<String, String> executableArgs = Map.of("lambda", objectMapper.writeValueAsString(lambda), "mapping",
                "vscodelsp::test::EmployeeMapping", "runtime", objectMapper.writeValueAsString(runtime), "context",
                objectMapper.writeValueAsString(context), "debug", "true");

        Iterable<? extends LegendExecutionResult> actual = testCommand(sectionState, "vscodelsp::test::TestService1",
                GENERATE_EXECUTION_PLAN_ID, executableArgs);

        Assertions.assertEquals(1, Iterate.sizeOf(actual));
        LegendExecutionResult result = actual.iterator().next();
        Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, result.getType(), result.getMessage());
        Map<String, Object> planAndDebugMap = objectMapper.readValue(result.getMessage(), new TypeReference<>() {});
        SingleExecutionPlan actualPlan = objectMapper.readValue(objectMapper.writeValueAsString(planAndDebugMap.get(
                "plan")), SingleExecutionPlan.class);
        Assertions.assertInstanceOf(DataTypeResultType.class, actualPlan.rootExecutionNode.resultType);
        Assertions.assertTrue(objectMapper.writeValueAsString(planAndDebugMap.get("debug")).contains("src:vscodelsp" +
                "::test::Employee[1] | {Platform> $src.hireType};"));
    }

    @Test
    public void testExecuteQuery() throws Exception
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();
        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        // Call extension.startup so the planExecutor is initialized
        GlobalState globalState = sectionStates.stream().findFirst().orElseThrow().getDocumentState().getGlobalState();
        extension.startup(globalState);
        SectionState sectionState =
                sectionStates.select(x -> x.getExtension() instanceof ServiceLSPGrammarExtension).getOnly();
        CompileResult compileResult = extension.getCompileResult(sectionState);
        PackageableElement serviceElement =
                compileResult.getPureModelContextData().getElements().stream().filter(x -> x.getPath().equals(
                        "vscodelsp::test::TestService2")).findFirst().orElseThrow();
        Lambda lambda = extension.getLambda(serviceElement);
        RuntimePointer runtime = new RuntimePointer();
        runtime.runtime = "vscodelsp::test::H2RuntimeRelational";
        ExecutionContext context = new BaseExecutionContext();
        Map<String, String> executableArgs = Map.of("lambda", objectMapper.writeValueAsString(lambda), "mapping",
                "vscodelsp::test::EmployeeRelationalMapping", "runtime", objectMapper.writeValueAsString(runtime),
                "context", objectMapper.writeValueAsString(context));
        Map<String, Object> inputParameters = Map.of("testParam", "testValue");

        Iterable<? extends LegendExecutionResult> actual = testCommand(sectionState, "vscodelsp::test::TestService2",
                EXECUTE_QUERY_ID, executableArgs, inputParameters);

        Assertions.assertEquals(1, Iterate.sizeOf(actual));
        FunctionLegendExecutionResult result = (FunctionLegendExecutionResult) actual.iterator().next();
        Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, result.getType(), result.getMessage());
        Assertions.assertEquals("testValue", result.getInputParameters().get("testParam"));
        Assertions.assertTrue(result.getMessage().contains("\"columns\":[{\"name\":\"First Name\"," +
                "\"type\":\"String\",\"relationalType\":\"VARCHAR(200)\"}]}"));
        Assertions.assertTrue(result.getMessage().contains("\"result\" : {\"columns\" : [\"First Name\"], \"rows\" : " +
                "[{\"values\": [\"Doe\"]}]}"));
    }

    @Test
    public void testConvertGrammarToJSON_lambda()
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();
        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        SectionState sectionState =
                sectionStates.select(x -> x.getExtension() instanceof ServiceLSPGrammarExtension).getOnly();
        String grammar = "x|$x.hireType";
        Map<String, String> executableArgs = Map.of("code", grammar);

        String expected = "{\"_type\":\"lambda\",\"body\":[{\"_type\":\"property\"," + "\"parameters\":[{\"_type" +
                "\":\"var\",\"name\":\"x\",\"sourceInformation\":{\"endColumn\":4," + "\"endLine\":1," +
                "\"sourceId\":\"\",\"startColumn\":3,\"startLine\":1}}],\"property\":\"hireType\"," +
                "\"sourceInformation\":{\"endColumn\":13,\"endLine\":1,\"sourceId\":\"\",\"startColumn\":6," +
                "\"startLine\":1}}],\"parameters\":[{\"_type\":\"var\",\"name\":\"x\"}]," + "\"sourceInformation" +
                "\":{\"endColumn\":13,\"endLine\":1,\"sourceId\":\"\",\"startColumn\":2," + "\"startLine\":1}}";
        Iterable<? extends LegendExecutionResult> actual = testCommand(sectionState, "vscodelsp::test::TestService2",
                GRAMMAR_TO_JSON_LAMBDA_ID, executableArgs);

        Assertions.assertEquals(1, Iterate.sizeOf(actual));
        LegendExecutionResult result = actual.iterator().next();
        Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, result.getType(), result.getMessage());
        Assertions.assertEquals(expected, result.getMessage());
    }

    @Test
    public void testConvertJSONToGrammar_lambda_batch()
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();
        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        SectionState sectionState =
                sectionStates.select(x -> x.getExtension() instanceof ServiceLSPGrammarExtension).getOnly();
        String lambdaString1 =
                "{" +
                        "   \"_type\": \"lambda\"," +
                        "   \"body\": [" +
                        "       {" +
                        "           \"_type\": \"string\"," +
                        "           \"value\": \"testValue\"" +
                        "       }" +
                        "   ]," +
                        "   \"parameters\": [" +
                        "       {" +
                        "           \"_type\": \"var\"," +
                        "           \"name\": \"x\"" +
                        "       }" +
                        "   ]" +
                        "}";
        String lambdaString2 =
                "{" +
                        "   \"_type\": \"lambda\"," +
                        "   \"body\": [" +
                        "       {" +
                        "           \"_type\": \"property\"," +
                        "           \"property\": \"hireType\"," +
                        "           \"parameters\": [" +
                        "               {" +
                        "                   \"_type\": \"var\"," +
                        "                   \"name\": \"x\"" +
                        "               }" +
                        "           ]" +
                        "       }" +
                        "   ]," +
                        "   \"parameters\": [" +
                        "       {" +
                        "           \"_type\": \"var\"," +
                        "           \"name\": \"x\"" +
                        "       }" +
                        "   ]" +
                        "}";
        Map<String, String> executableArgs = Map.of("lambdas", "{\"query-builder@projection@1\":" + lambdaString1 +
                ",\"query-builder@projection@2\":" + lambdaString2 + "}", "renderStyle", "STANDARD");

        String expected = "{\"query-builder@projection@1\":\"x|'testValue'\",\"query-builder@projection@2\":\"x|$x" +
                ".hireType\"}";
        Iterable<? extends LegendExecutionResult> actual = testCommand(sectionState, "vscodelsp::test::TestService2",
                JSON_TO_GRAMMAR_LAMBDA_BATCH_ID, executableArgs);

        Assertions.assertEquals(1, Iterate.sizeOf(actual));
        LegendExecutionResult result = actual.iterator().next();
        Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, result.getType(), result.getMessage());
        Assertions.assertEquals(expected, result.getMessage());
    }

    @Test
    public void testGetLambdaReturnType()
    {
        MutableMap<String, String> codeFiles = this.getCodeFilesThatParseCompile();
        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        SectionState sectionState =
                sectionStates.select(x -> x.getExtension() instanceof ServiceLSPGrammarExtension).getOnly();
        String lambdaString =
                "{" +
                "   \"_type\": \"lambda\"," +
                "   \"body\": [" +
                "       {" +
                "           \"_type\": \"property\"," +
                "           \"parameters\": [" +
                "               {" +
                "                   \"_type\": \"var\"," +
                "                   \"name\": \"x\"" +
                "               }" +
                "           ]," +
                "           \"property\": \"hireType\"" +
                "       }" +
                "   ]," +
                "   \"parameters\": [" +
                "       {" +
                "           \"_type\":\"var\"," +
                "           \"class\": \"vscodelsp::test::Employee\"," +
                "           \"multiplicity\": { " +
                "               \"lowerBound\": 1," +
                "               \"upperBound\": 1" +
                "           }," +
                "           \"name\": \"x\"" +
                "       }" +
                "   ]" +
                "}";
        Map<String, String> executableArgs = Map.of("lambda", lambdaString);

        String expected = "{\"returnType\":\"String\"}";
        Iterable<? extends LegendExecutionResult> actual = testCommand(sectionState, "vscodelsp::test::TestService2",
                GET_LAMBDA_RETURN_TYPE_ID, executableArgs);

        Assertions.assertEquals(1, Iterate.sizeOf(actual));
        LegendExecutionResult result = actual.iterator().next();
        Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, result.getType(), result.getMessage());
        Assertions.assertEquals(expected, result.getMessage());
    }
}
