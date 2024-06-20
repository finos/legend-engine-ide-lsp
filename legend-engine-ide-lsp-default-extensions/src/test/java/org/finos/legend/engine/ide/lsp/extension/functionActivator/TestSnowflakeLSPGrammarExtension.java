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

package org.finos.legend.engine.ide.lsp.extension.functionActivator;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtensionTest;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.junit.jupiter.api.Test;

public class TestSnowflakeLSPGrammarExtension extends AbstractLSPGrammarExtensionTest<SnowflakeLSPGrammarExtension>
{
    @Test
    public void testGetDeclarations()
    {
        testGetDeclarations(
                "###Snowflake\n" +
                        "SnowflakeApp app::pack::MyApp\n" +
                        "{" +
                        "   applicationName : 'name';\n" +
                        "   function : a::f():String[1];" +
                        "   ownership : Deployment { identifier: 'MyAppOwnership'};\n" +
                        "}\n",
                LegendDeclaration.builder().withIdentifier("app::pack::MyApp")
                        .withClassifier("meta::external::function::activator::snowflakeApp::SnowflakeApp")
                        .withLocation(DOC_ID_FOR_TEXT, 1, 0, 4, 0).build()
        );
    }


    @Test
    public void testGetReferenceResolvers()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.of("snowflakeApp.pure",
                "###Snowflake\n" +
                        "SnowflakeApp app::pack::MyApp\n" +
                        "{" +
                        "   applicationName : 'name';\n" +
                        "   function : a::f():String[1];\n" +
                        "   ownership : Deployment { identifier: 'MyAppOwnership'};\n" +
                        "   activationConfiguration: a::connection;\n" +
                        "}\n",
                "function.pure",
                "function a::f():String[1]{'ok';}\n",
                "connection.pure",
                "###Connection\n" +
                        "RelationalDatabaseConnection a::connection\n" +
                        "{\n" +
                        "  type: Snowflake;\n" +
                        "  specification: Snowflake\n" +
                        "  {\n" +
                        "    name: 'test';\n" +
                        "    account: 'account';\n" +
                        "    warehouse: 'warehouseName';\n" +
                        "    region: 'us-east2';\n" +
                        "    proxyHost: 'sampleHost';\n" +
                        "    proxyPort: 'samplePort';\n" +
                        "    nonProxyHosts: 'sample';\n" +
                        "    accountType: MultiTenant;\n" +
                        "    organization: 'sampleOrganization';\n" +
                        "    role: 'DB_ROLE_123';\n" +
                        "  };\n" +
                        "  auth: SnowflakePublic\n" +
                        "  {" +
                        "       publicUserName: 'name';\n" +
                        "       privateKeyVaultReference: 'privateKey';\n" +
                        "       passPhraseVaultReference: 'passPhrase';\n" +
                        "  };\n" +
                        "}\n"
        );

        LegendReference funcReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("snowflakeApp.pure", 3, 14, 3, 29))
                .withDeclarationLocation(TextLocation.newTextSource("function.pure", 0, 0, 0, 31))
                .build();

        testReferenceLookup(codeFiles, "snowflakeApp.pure", TextPosition.newPosition(3, 17), funcReference, "snowflakeApp should reference function");

        LegendReference connReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("snowflakeApp.pure", 5, 28, 5, 40))
                .withDeclarationLocation(TextLocation.newTextSource("connection.pure", 1, 0, 22, 0))
                .build();

        testReferenceLookup(codeFiles, "snowflakeApp.pure", TextPosition.newPosition(5, 33), connReference, "snowflakeApp should reference connection under deployment config");
    }
}
