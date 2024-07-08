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

package org.finos.legend.engine.ide.lsp.extension.dataSpace;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtensionTest;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.junit.jupiter.api.Test;

public class TestDataSpaceLSPGrammarExtension extends AbstractLSPGrammarExtensionTest<DataSpaceLSPGrammarExtension>
{
    private MutableMap<String, String> getCodeFilesForDataSpace()
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

        codeFiles.put("test::dataspace",
                "###DataSpace\n" +
                "DataSpace test::dataspace\n" +
                "{\n" +
                "  executionContexts:\n" +
                "  [\n" +
                "    {\n" +
                "      name: 'dummyContext';\n" +
                "      mapping: test::mapping;\n" +
                "      defaultRuntime: test::runtime;\n" +
                "    }\n" +
                "  ];\n" +
                "  defaultExecutionContext: 'dummyContext';\n" +
                "  title: 'testTitle';\n" +
                "  description: 'test description';\n" +
                "  executables:\n" +
                "  [\n" +
                "    {\n" +
                "      title: 'exec1';\n" +
                "      description: 'd';\n" +
                "      executable: test::service;\n" +
                "    }\n" +
                "  ];\n" +
                "}");

        codeFiles.put("test::dataspace2",
                "###DataSpace\n" +
                "DataSpace test::dataspace2\n" +
                "{\n" +
                "  executionContexts:\n" +
                "  [\n" +
                "    {\n" +
                "      name: 'default';\n" +
                "      mapping: test::mapping;\n" +
                "      defaultRuntime: test::runtime;\n" +
                "    }\n" +
                "  ];\n" +
                "  defaultExecutionContext: 'default';\n" +
                "  executables:\n" +
                "  [\n" +
                "    {\n" +
                "      id: my_id;\n" +
                "      title: 'exec1';\n" +
                "      query: test::class.all()->project([col(p|$p.prop1, 'prop1')]);\n" +
                "      executionContextKey: 'default';\n" +
                "    }\n" +
                "  ];\n" +
                "}");

        return codeFiles;
    }

    @Test
    public void testGetReferenceResolversForDataSpaceWithPackageableElementExecutables()
    {
        LegendReference mappingReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("test::dataspace", 7, 6, 7, 28))
                .withDeclarationLocation(TextLocation.newTextSource("test::mapping", 1, 0, 3, 0))
                .build();

        testReferenceLookup(getCodeFilesForDataSpace(), "test::dataspace", TextPosition.newPosition(7, 20), mappingReference, "Within mapping has been mapped, referring to mapping definition");

        LegendReference runtimeReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("test::dataspace", 8, 6, 8, 35))
                .withDeclarationLocation(TextLocation.newTextSource("test::runtime", 1, 0, 4, 0))
                .build();

        testReferenceLookup(getCodeFilesForDataSpace(), "test::dataspace", TextPosition.newPosition(8, 25), runtimeReference, "Within runtime has been mapped, referring to runtime definition");

        LegendReference serviceReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("test::dataspace", 19, 6, 19, 31))
                .withDeclarationLocation(TextLocation.newTextSource("test::service", 1, 0, 23, 0))
                .build();

        testReferenceLookup(getCodeFilesForDataSpace(), "test::dataspace", TextPosition.newPosition(19, 25), serviceReference, "Within service has been mapped, referring to service definition");
    }

    @Test
    public void testGetReferenceResolversForDataSpaceWithTemplateExecutables()
    {
        LegendReference classPropertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("test::dataspace2", 17, 50, 17, 54))
                .withDeclarationLocation(TextLocation.newTextSource("test::class", 3, 4, 3, 20))
                .build();

        testReferenceLookup(getCodeFilesForDataSpace(), "test::dataspace2", TextPosition.newPosition(17, 52), classPropertyReference, "Within class property has been mapped, referring to class property definition");
    }
}
