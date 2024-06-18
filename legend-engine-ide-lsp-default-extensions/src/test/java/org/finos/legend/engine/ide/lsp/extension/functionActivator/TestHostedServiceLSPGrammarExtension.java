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

public class TestHostedServiceLSPGrammarExtension extends AbstractLSPGrammarExtensionTest<HostedServiceLSPGrammarExtension>
{
    @Test
    public void testGetDeclarations()
    {
        testGetDeclarations(
                "###HostedService\n" +
                        "HostedService test::hosted::SampleHostedService\n" +
                        "{\n" +
                        "  pattern: '/path';\n" +
                        "  ownership: Deployment { identifier: '1234' };\n" +
                        "  function: showcase::model::testFunction(String[1],Boolean[*]):RelationStoreAccessor[*];\n" +
                        "  documentation: 'Sample hosted service';\n" +
                        "  autoActivateUpdates: true;\n" +
                        "}",
                LegendDeclaration.builder().withIdentifier("test::hosted::SampleHostedService")
                        .withClassifier("meta::external::function::activator::hostedService::HostedService")
                        .withLocation(DOC_ID_FOR_TEXT,1, 0, 8, 0).build()
        );
    }


    @Test
    public void testGetReferenceResolvers()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.of("hostedService.pure",
                "###HostedService\n" +
                        "HostedService test::hosted::SampleHostedService\n" +
                        "{\n" +
                        "  pattern: '/path';\n" +
                        "  ownership: Deployment { identifier: '1234' };\n" +
                        "  function: showcase::model::testFunction():Any[1];\n" +
                        "  documentation: 'Sample hosted service';\n" +
                        "  autoActivateUpdates: true;\n" +
                        "}",
                "function.pure",
                "function showcase::model::testFunction():Any[1]\n" +
                        "{\n" +
                        "  1;" +
                        "}"
        );

        LegendReference reference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("hostedService.pure", 5, 12, 5, 49))
                .withDeclarationLocation(TextLocation.newTextSource("function.pure", 0, 0, 2, 4))
                .build();

        testReferenceLookup(codeFiles, "hostedService.pure", TextPosition.newPosition(5, 25), reference, "hosted service should reference function");
    }
}
