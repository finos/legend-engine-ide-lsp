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

package org.finos.legend.engine.ide.lsp.extension.sdlc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.finos.legend.engine.ide.lsp.extension.StateForTestFactory;
import org.finos.legend.engine.ide.lsp.extension.features.LegendSDLCFeature;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class TestLegendSDLCFeature
{
    @Test
    void testEntityJsonToPureText() throws IOException
    {
        LegendSDLCFeatureImpl legendSDLCFeature = new LegendSDLCFeatureImpl();
        String pureText = legendSDLCFeature.entityJsonToPureText("{\n" +
                "  \"content\": {\n" +
                "    \"_type\": \"class\",\n" +
                "    \"name\": \"A\",\n" +
                "    \"package\": \"model\",\n" +
                "    \"properties\": [\n" +
                "      {\n" +
                "        \"multiplicity\": {\n" +
                "          \"lowerBound\": 1,\n" +
                "          \"upperBound\": 1\n" +
                "        },\n" +
                "        \"name\": \"name\",\n" +
                "        \"type\": \"String\"\n" +
                "      }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"classifierPath\": \"meta::pure::metamodel::type::Class\"\n" +
                "}\n");

        Assertions.assertEquals(
                "Class model::A\n" +
                        "{\n" +
                        "  name: String[1];\n" +
                        "}\n", pureText);
    }

    @TestFactory
    Collection<DynamicTest> testConvertToOneElementPerFile(@TempDir Path root)
    {
        LegendSDLCFeatureImpl legendSDLCFeature = new LegendSDLCFeatureImpl();
        StateForTestFactory stateForTestFactory = new StateForTestFactory();

        return Arrays.asList(
                DynamicTest.dynamicTest("Convert file with one element",
                        () ->
                        {
                            DocumentState documentState = stateForTestFactory.newSectionState("file1.pure", "Class abc::abc { a: Integer[1]; }").getDocumentState();
                            Map<Path, String> result = legendSDLCFeature.convertToOneElementPerFile(root, documentState);
                            Assertions.assertEquals(Map.of(root.resolve("abc/abc.pure"), "Class abc::abc { a: Integer[1]; }"), result);
                        }),
                DynamicTest.dynamicTest("Convert file with multiple element",
                        () ->
                        {
                            DocumentState documentState = stateForTestFactory.newSectionState("file2.pure",
                                    "// there is a comment here\n" +
                                            "Class abc::abc { a: Integer[1]; }\n" +
                                            "// there is another comment here\n" +
                                            "Class aei::aei { a: Integer[1]; }\n" +
                                            "Class xyz::xyz { a: Integer[1]; }"
                            ).getDocumentState();
                            Map<Path, String> result = legendSDLCFeature.convertToOneElementPerFile(root, documentState);
                            Assertions.assertEquals(Map.of(
                                    root.resolve("abc/abc.pure"), "// there is a comment here\nClass abc::abc { a: Integer[1]; }",
                                    root.resolve("aei/aei.pure"), "// there is another comment here\nClass aei::aei { a: Integer[1]; }",
                                    root.resolve("xyz/xyz.pure"), "Class xyz::xyz { a: Integer[1]; }"
                            ), result);
                        }),
                DynamicTest.dynamicTest("Convert file with multiple elements on same line",
                        () ->
                        {
                            DocumentState documentState = stateForTestFactory.newSectionState("file3.pure", "Class abc::abc { a: Integer[1]; } Class aei::aei { a: Integer[1]; }").getDocumentState();
                            UnsupportedOperationException unsupportedOperationException = Assertions.assertThrows(UnsupportedOperationException.class,
                                    () -> legendSDLCFeature.convertToOneElementPerFile(root, documentState));
                            Assertions.assertEquals("Refactoring elements that are defined on the same line not supported.  Element 'aei::aei' defined next to previous element 'abc::abc'", unsupportedOperationException.getMessage());
                        }),
                DynamicTest.dynamicTest("Convert file, removing redundant ###Pure",
                        () ->
                        {
                            DocumentState documentState = stateForTestFactory.newSectionState("file4.pure",
                                    "###Pure\n" +
                                            "Class abc::abc { a: Integer[1]; }"
                            ).getDocumentState();
                            Map<Path, String> result = legendSDLCFeature.convertToOneElementPerFile(root, documentState);
                            Assertions.assertEquals(Map.of(
                                    root.resolve("abc/abc.pure"), "Class abc::abc { a: Integer[1]; }"
                            ), result);
                        }),
                DynamicTest.dynamicTest("Convert file fails if document has parsing failures",
                        () ->
                        {
                            DocumentState documentState = stateForTestFactory.newSectionState("file5.pure",
                                    "###Pure\n" +
                                            "Class abc::abc { a: Integer[1] }"
                            ).getDocumentState();
                            IllegalStateException e = Assertions.assertThrows(IllegalStateException.class, () -> legendSDLCFeature.convertToOneElementPerFile(root, documentState));
                            Assertions.assertEquals("Unable to refactor document file5.pure given the parser errors (ie. Unexpected token '}'. Valid alternatives: ['=', ';']).  Fix and try again!", e.getMessage());
                        }),
                DynamicTest.dynamicTest("Convert file, add required ###_DSL_NAME_",
                        () ->
                        {
                            DocumentState documentState = stateForTestFactory.newSectionState("file6.pure",
                                    "###Relational\n" +
                                            "Database abc::abc()\n" +
                                            "Database xyz::xyz()"
                            ).getDocumentState();
                            Map<Path, String> result = legendSDLCFeature.convertToOneElementPerFile(root, documentState);
                            Assertions.assertEquals(Map.of(
                                    root.resolve("abc/abc.pure"), "###Relational\nDatabase abc::abc()",
                                    root.resolve("xyz/xyz.pure"), "###Relational\nDatabase xyz::xyz()"
                            ), result);
                        }),
                DynamicTest.dynamicTest("Convert file, keeps trailing lines",
                        () ->
                        {
                            DocumentState documentState = stateForTestFactory.newSectionState("file6.pure",
                                    "###Relational\n" +
                                            "Database abc::abc()\n" +
                                            "Database xyz::xyz()\n" +
                                            "// this is a comment that will be kept on xyz file"
                            ).getDocumentState();
                            Map<Path, String> result = legendSDLCFeature.convertToOneElementPerFile(root, documentState);
                            Assertions.assertEquals(Map.of(
                                    root.resolve("abc/abc.pure"), "###Relational\nDatabase abc::abc()",
                                    root.resolve("xyz/xyz.pure"), "###Relational\nDatabase xyz::xyz()\n// this is a comment that will be kept on xyz file"
                            ), result);
                        })
        );
    }

    @Test
    public void testGetClassifierPathMap()
    {
        LegendSDLCFeatureImpl legendSDLCFeature = new LegendSDLCFeatureImpl();
        String actual = legendSDLCFeature.getClassifierPathMap();
        Assertions.assertTrue(actual.contains("{\"type\":\"association\",\"classifierPath\":\"meta::pure::metamodel::relationship::Association\"}"));
    }

    @Test
    public void testGetSubtypeInfo()
    {
        LegendSDLCFeatureImpl legendSDLCFeature = new LegendSDLCFeatureImpl();
        LegendSDLCFeature.SubtypeInfoResult actual = legendSDLCFeature.getSubtypeInfo();
        Assertions.assertFalse(actual.functionActivatorSubtypes.isEmpty());
        Assertions.assertFalse(actual.storeSubtypes.isEmpty());
    }
}
