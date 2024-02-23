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

package org.finos.legend.engine.ide.lsp.extension.connection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.apache.http.HttpStatus;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtensionTest;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.shared.core.ObjectMapperFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.finos.legend.engine.ide.lsp.extension.connection.ConnectionLSPGrammarExtension.GENERATE_DB_COMMAND_ID;


public class TestConnectionLSPGrammarExtension extends AbstractLSPGrammarExtensionTest<ConnectionLSPGrammarExtension>
{
    @Test
    public void testGetName()
    {
        testGetName("Connection");
    }

    @Test
    public void testGetKeywords()
    {
        MutableSet<String> missingKeywords = Sets.mutable.with("JsonModelConnection", "XmlModelConnection", "ModelChainConnection", "RelationalDatabaseConnection");
        this.extension.getKeywords().forEach(missingKeywords::remove);
        Assertions.assertEquals(Sets.mutable.empty(), missingKeywords);
    }

    @Test
    public void testGetDeclarations()
    {
        testGetDeclarations(
                "###Connection\n" +
                        "\n" +
                        "JsonModelConnection test::connection::TestConnection\n" +
                        "{\r\n" +
                        "    class: test::model::Person;\r\n" +
                        "    url: 'test_url';\n" +
                        " }\n",
                LegendDeclaration.builder().withIdentifier("test::connection::TestConnection").withClassifier("meta::pure::runtime::PackageableConnection").withLocation(DOC_ID_FOR_TEXT,2, 0, 6, 1).build()
        );
    }


    @Test
    public void testDiagnostics_parserError()
    {
        testDiagnostics(
                "###Connection\n" +
                        "\n" +
                        "JsonModelConnection test::connection::TestConnection\n" +
                        "{\r\n" +
                        "    class: test::model::Person;\r\n" +
                        "    url: ;\n" +
                        " }\n",
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT, 5, 9, 5, 9), "Unexpected token", LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Parser)
        );
    }

    @Test
    void testGenerateDBFromConnectionCommand() throws IOException
    {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        try
        {
            httpServer.createContext("/pure/v1/utilities/database/schemaExploration", exchange ->
            {
                ObjectMapper objectMapper = ObjectMapperFactory.getNewStandardObjectMapperWithPureProtocolExtensionSupports();
                try
                {
                    String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    Assertions.assertEquals(
                            "{\"config\":{\"enrichTables\":true,\"enrichPrimaryKeys\":true,\"enrichColumns\":true,\"patterns\":[{\"catalog\":\"%\",\"schemaPattern\":\"%\",\"tablePattern\":\"%\"}]},\"connection\":{\"_type\":\"RelationalDatabaseConnection\",\"element\":\"model::MyStore\",\"elementSourceInformation\":{\"sourceId\":\"file.pure\",\"startLine\":4,\"startColumn\":10,\"endLine\":4,\"endColumn\":23},\"sourceInformation\":{\"sourceId\":\"file.pure\",\"startLine\":2,\"startColumn\":1,\"endLine\":12,\"endColumn\":1},\"type\":\"H2\",\"timeZone\":null,\"quoteIdentifiers\":null,\"postProcessorWithParameter\":[],\"datasourceSpecification\":{\"_type\":\"h2Local\",\"sourceInformation\":{\"sourceId\":\"file.pure\",\"startLine\":6,\"startColumn\":3,\"endLine\":10,\"endColumn\":4},\"testDataSetupCsv\":null,\"testDataSetupSqls\":[]},\"authenticationStrategy\":{\"_type\":\"h2Default\",\"sourceInformation\":{\"sourceId\":\"file.pure\",\"startLine\":11,\"startColumn\":3,\"endLine\":11,\"endColumn\":18}},\"databaseType\":\"H2\",\"postProcessors\":null,\"localMode\":null},\"targetDatabase\":{\"name\":\"MyConnectionDatabase\",\"package\":\"model\"}}",
                            requestBody
                    );
                    ConnectionLSPGrammarExtension.DatabaseBuilderInput body = objectMapper.readValue(requestBody, ConnectionLSPGrammarExtension.DatabaseBuilderInput.class);
                    Assertions.assertEquals("model::MyStore", body.connection.element);
                    Assertions.assertEquals("model", body.targetDatabase._package);
                    Assertions.assertEquals("MyConnectionDatabase", body.targetDatabase.name);
                    exchange.sendResponseHeaders(HttpStatus.SC_OK, 0);
                    objectMapper.writeValue(exchange.getResponseBody(), PureModelContextData.newPureModelContextData());
                }
                catch (Throwable e)
                {
                    exchange.sendResponseHeaders(HttpStatus.SC_INTERNAL_SERVER_ERROR, 0);
                    exchange.getResponseBody().write(e.getMessage().getBytes(StandardCharsets.UTF_8));
                }
                finally
                {
                    exchange.close();
                }
            });
            httpServer.start();

            System.setProperty("legend.engine.server.url", "http://localhost:" + httpServer.getAddress().getPort());

            Iterable<? extends LegendExecutionResult> legendExecutionResults = testCommand(
                    "###Connection\n" +
                            "RelationalDatabaseConnection model::MyConnection\n" +
                            "{\n" +
                            "  store: model::MyStore;\n" +
                            "  type: H2;\n" +
                            "  specification: LocalH2\n" +
                            "  {\n" +
                            "    testDataSetupSqls: [\n" +
                            "      ];\n" +
                            "  };\n" +
                            "  auth: DefaultH2;\n" +
                            "}\n",
                    "model::MyConnection",
                    GENERATE_DB_COMMAND_ID
            );

            Assertions.assertEquals(1, Iterate.sizeOf(legendExecutionResults));
            LegendExecutionResult result = legendExecutionResults.iterator().next();
            Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, result.getType(), result.getMessage());
        }
        finally
        {
            System.clearProperty("legend.engine.server.url");
            httpServer.stop(0);
        }
    }

    @Test
    public void testGetReferenceResolvers()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_CONNECTION_DOC_ID = "vscodelsp::test::FirmConnection";
        final String TEST_CLASS_DOC_ID = "vscodelsp::test::Firm";
        codeFiles.put("vscodelsp::test::Person",
                "###Pure\n" +
                "Class vscodelsp::test::Person\n" +
                "{\n" +
                "    firstName: String[1];\n" +
                "    lastName : String[1];\n" +
                "}");

        codeFiles.put(TEST_CLASS_DOC_ID,
                "###Pure\n" +
                "Class vscodelsp::test::Firm\n" +
                "{\n" +
                "    employees: vscodelsp::test::Person[1..*];\n" +
                "    legalName : String[1];\n" +
                "}");

        codeFiles.put(TEST_CONNECTION_DOC_ID,
                "###Connection\n" +
                "JsonModelConnection vscodelsp::test::FirmConnection\n" +
                "{\n" +
                "    class: vscodelsp::test::Firm;\n" +
                "    url: 'my_url';\n" +
                "}");

        LegendReference mappedClassReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_CONNECTION_DOC_ID, 3, 11, 3, 31))
                .withReferencedLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID, 1, 0, 5, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_CONNECTION_DOC_ID, TextPosition.newPosition(2, 2), null, "Outside of mappedClassReference-able element should yield nothing");
        testReferenceLookup(codeFiles, TEST_CONNECTION_DOC_ID, TextPosition.newPosition(3, 10), null, "Outside of mappedClassReference-able element (before class name) should yield nothing");
        testReferenceLookup(codeFiles, TEST_CONNECTION_DOC_ID, TextPosition.newPosition(3, 11), mappedClassReference, "Start of class name has been mapped, referring to class definition");
        testReferenceLookup(codeFiles, TEST_CONNECTION_DOC_ID, TextPosition.newPosition(3, 25), mappedClassReference, "Within the class name has been mapped, referring to class definition");
        testReferenceLookup(codeFiles, TEST_CONNECTION_DOC_ID, TextPosition.newPosition(3, 31), mappedClassReference, "End of class name has been mapped, referring to class definition");
        testReferenceLookup(codeFiles, TEST_CONNECTION_DOC_ID, TextPosition.newPosition(4, 3), null, "Outside of mappedClassReference-able element should yield nothing");
    }

    @Test
    public void testGetConnectionReferencesStoreConnectionsWithConnectionPointer()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_RUNTIME_DOC_ID = "vscodelsp::test::H2Runtime";
        final String TEST_MAPPING_DOC_ID = "vscodelsp::test::EmployeeMapping";
        final String TEST_STORE_DOC_ID_1 = "vscodelsp::test::TestDB1";
        final String TEST_STORE_DOC_ID_2 = "vscodelsp::test::TestDB2";
        final String TEST_CONNECTION_DOC_ID_1 = "vscodelsp::test::LocalH2Connection1";
        final String TEST_CONNECTION_DOC_ID_2 = "vscodelsp::test::LocalH2Connection2";
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

        LegendReference mappedMappingReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 5, 7, 5, 38))
                .withReferencedLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID, 1, 0, 9, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(4, 2), null, "Outside of mappedMappingReference-able element should yield nothing");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 6), null, "Outside of mappedMappingReference-able element (before mapping name) should yield nothing");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 7), mappedMappingReference, "Start of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 25), mappedMappingReference, "Within the mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 38), mappedMappingReference, "End of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(6, 3), null, "Outside of mappedMappingReference-able element should yield nothing");

        LegendReference mappedStoreReference1 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 9, 7, 9, 30))
                .withReferencedLocation(TextLocation.newTextSource(TEST_STORE_DOC_ID_1, 1, 0, 10, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(9, 20), mappedStoreReference1, "Within the store name has been mapped, referring to store definition");

        LegendReference mappedConnectionReference1 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 11, 25, 11, 59))
                .withReferencedLocation(TextLocation.newTextSource(TEST_CONNECTION_DOC_ID_1, 1, 0, 12, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(11, 30), mappedConnectionReference1, "Within the connection name has been mapped, referring to connection definition");

        LegendReference mappedStoreReference2 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 13, 7, 13, 30))
                .withReferencedLocation(TextLocation.newTextSource(TEST_STORE_DOC_ID_2, 1, 0, 9, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(13, 20), mappedStoreReference2, "Within the store name has been mapped, referring to store definition");

        LegendReference mappedConnectionReference2 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 15, 25, 15, 59))
                .withReferencedLocation(TextLocation.newTextSource(TEST_CONNECTION_DOC_ID_2, 1, 0, 12, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(15, 38), mappedConnectionReference2, "Within the connection name has been mapped, referring to connection definition");
    }

    @Test
    public void testGetConnectionReferencesStoreConnectionsWithJsonModelConnection()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_CLASS_DOC_ID = "vscodelsp::test::Employee";
        final String TEST_RUNTIME_DOC_ID = "vscodelsp::test::H2Runtime";
        final String TEST_MAPPING_DOC_ID = "vscodelsp::test::EmployeeMapping";
        codeFiles.put(TEST_CLASS_DOC_ID,
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

        codeFiles.put("vscodelsp::test::TestDB1",
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
                "       ModelStore:\n" +
                "       [\n" +
                "           connection_1:\n" +
                "           #{\n" +
                "               JsonModelConnection\n" +
                "               {\n" +
                "                   class: vscodelsp::test::Employee;\n" +
                "                   url: 'my_url';\n" +
                "               }\n" +
                "           }#\n" +
                "       ]\n" +
                "   ];\n" +
                "}");

        LegendReference mappedMappingReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 5, 7, 5, 38))
                .withReferencedLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID, 1, 0, 9, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(4, 2), null, "Outside of mappedMappingReference-able element should yield nothing");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 6), null, "Outside of mappedMappingReference-able element (before mapping name) should yield nothing");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 7), mappedMappingReference, "Start of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 25), mappedMappingReference, "Within the mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 38), mappedMappingReference, "End of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(6, 3), null, "Outside of mappedMappingReference-able element should yield nothing");

        LegendReference mappedClassReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 15, 26, 15, 50))
                .withReferencedLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID, 1, 0, 6, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(15, 46), mappedClassReference, "Within the class name has been mapped, referring to class definition");
    }

    @Test
    public void testGetConnectionReferencesStoreConnectionsWithXmlModelConnection()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_CLASS_DOC_ID = "vscodelsp::test::Employee";
        final String TEST_RUNTIME_DOC_ID = "vscodelsp::test::H2Runtime";
        final String TEST_MAPPING_DOC_ID = "vscodelsp::test::EmployeeMapping";
        codeFiles.put(TEST_CLASS_DOC_ID,
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

        codeFiles.put("vscodelsp::test::TestDB1",
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
                "       ModelStore:\n" +
                "       [\n" +
                "           connection_1:\n" +
                "           #{\n" +
                "               XmlModelConnection\n" +
                "               {\n" +
                "                   class: vscodelsp::test::Employee;\n" +
                "                   url: 'my_url';\n" +
                "               }\n" +
                "           }#\n" +
                "       ]\n" +
                "   ];\n" +
                "}");

        LegendReference mappedMappingReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 5, 7, 5, 38))
                .withReferencedLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID, 1, 0, 9, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(4, 2), null, "Outside of mappedMappingReference-able element should yield nothing");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 6), null, "Outside of mappedMappingReference-able element (before mapping name) should yield nothing");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 7), mappedMappingReference, "Start of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 25), mappedMappingReference, "Within the mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 38), mappedMappingReference, "End of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(6, 3), null, "Outside of mappedMappingReference-able element should yield nothing");

        LegendReference mappedClassReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 15, 26, 15, 50))
                .withReferencedLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID, 1, 0, 6, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(15, 46), mappedClassReference, "Within the class name has been mapped, referring to class definition");
    }

    @Test
    public void testGetConnectionReferencesConnectionStores()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_RUNTIME_DOC_ID = "vscodelsp::test::H2Runtime";
        final String TEST_MAPPING_DOC_ID = "vscodelsp::test::EmployeeMapping";
        final String TEST_STORE_DOC_ID_1 = "vscodelsp::test::TestDB1";
        final String TEST_STORE_DOC_ID_2 = "connections::TestDB2";
        final String TEST_CONNECTION_DOC_ID = "vscodelsp::test::LocalH2Connection1";
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
                "   include connections::TestDB2\n" +
                "\n" +
                "   Table PersonTable\n" +
                "   (\n" +
                "       id INTEGER PRIMARY KEY,\n" +
                "       firm_id INTEGER,\n" +
                "       firstName VARCHAR(200),\n" +
                "       lastName VARCHAR(200)\n" +
                "   )\n" +
                "   Join FirmPerson(PersonTable.firm_id = FirmTable.id)\n" +
                ")");

        codeFiles.put(TEST_STORE_DOC_ID_2,
                "###Relational\n" +
                "Database connections::TestDB2\n" +
                "(\n" +
                "   Table FirmTable\n" +
                "   (\n" +
                "       id INTEGER PRIMARY KEY,\n" +
                "       Type VARCHAR(200),\n" +
                "       Legal_name VARCHAR(200)\n" +
                "   )\n" +
                ")");

        codeFiles.put(TEST_CONNECTION_DOC_ID,
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

        codeFiles.put(TEST_RUNTIME_DOC_ID,
                "###Runtime\n" +
                "Runtime vscodelsp::test::H2Runtime\n" +
                "{\n" +
                "   mappings:\n" +
                "   [\n" +
                "       vscodelsp::test::EmployeeMapping\n" +
                "   ];\n" +
                "   connectionStores:\n" +
                "   [\n" +
                "       vscodelsp::test::LocalH2Connection1:\n" +
                "       [\n" +
                "           vscodelsp::test::TestDB1,\n" +
                "           connections::TestDB2\n" +
                "       ]\n" +
                "   ];\n" +
                "}");

        LegendReference mappedMappingReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 5, 7, 5, 38))
                .withReferencedLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID, 1, 0, 9, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(4, 2), null, "Outside of mappedMappingReference-able element should yield nothing");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 6), null, "Outside of mappedMappingReference-able element (before mapping name) should yield nothing");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 7), mappedMappingReference, "Start of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 25), mappedMappingReference, "Within the mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 38), mappedMappingReference, "End of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(6, 3), null, "Outside of mappedMappingReference-able element should yield nothing");

        LegendReference mappedConnectionReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 9, 7, 9, 41))
                .withReferencedLocation(TextLocation.newTextSource(TEST_CONNECTION_DOC_ID, 1, 0, 12, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(9, 30), mappedConnectionReference, "Within the connection name has been mapped, referring to connection definition");

        LegendReference mappedStoreReference1 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 11, 11, 11, 34))
                .withReferencedLocation(TextLocation.newTextSource(TEST_STORE_DOC_ID_1, 1, 0, 13, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(11, 20), mappedStoreReference1, "Within the store name has been mapped, referring to store definition");

        LegendReference mappedStoreReference2 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 12, 11, 12, 30))
                .withReferencedLocation(TextLocation.newTextSource(TEST_STORE_DOC_ID_2, 1, 0, 9, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(12, 20), mappedStoreReference2, "Within the store name has been mapped, referring to store definition");
    }
}
