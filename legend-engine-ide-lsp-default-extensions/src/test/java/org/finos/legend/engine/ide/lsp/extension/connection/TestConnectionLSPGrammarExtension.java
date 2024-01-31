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
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtensionTest;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
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
}
