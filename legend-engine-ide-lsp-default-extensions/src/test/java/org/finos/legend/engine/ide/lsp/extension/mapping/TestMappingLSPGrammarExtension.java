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

package org.finos.legend.engine.ide.lsp.extension.mapping;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtensionTest;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Kind;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Source;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Objects;

public class TestMappingLSPGrammarExtension extends AbstractLSPGrammarExtensionTest<MappingLSPGrammarExtension>
{
    @Test
    public void testGetName()
    {
        testGetName("Mapping");
    }

    @Test
    public void testGetKeywords()
    {
        MutableSet<String> missingKeywords = Sets.mutable.with("AggregationAware", "AggregateSpecification", "EnumerationMapping", "include", "Mapping", "MappingTests", "Operation", "Pure", "Relational", "XStore");
        this.extension.getKeywords().forEach(missingKeywords::remove);
        Assertions.assertEquals(Sets.mutable.empty(), missingKeywords);
    }

    @Test
    public void testCompletion()
    {
        String code = "\n" +
                "###Mapping\n" +
                "\n" +
                "Mapping test::mapping::TestMapping\n" +
                "(\n" +
                "~mainTable [package::path::storeName]schemaName.TableName1\n" +
                " )\n";
        String boilerPlate = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(2, 0)).iterator().next().getDescription();
        Assertions.assertEquals("Mapping boilerplate", boilerPlate);

        Iterable<? extends LegendCompletion> noCompletion = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(2, 1));
        Assertions.assertFalse(noCompletion.iterator().hasNext());

        String storeObjectSuggestion = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(5, 1)).iterator().next().getDescription();
        Assertions.assertEquals("Store object type", storeObjectSuggestion);
    }

    @Test
    public void testGetDeclarations()
    {
        testGetDeclarations(
                "###Mapping\n" +
                        "\r\n" +
                        "\n" +
                        "Mapping test::mapping::TestMapping\n" +
                        "(\r\n" +
                        "   )\n",
                LegendDeclaration.builder().withIdentifier("test::mapping::TestMapping").withClassifier("meta::pure::mapping::Mapping").withLocation(DOC_ID_FOR_TEXT,3, 0, 5, 3).build()
        );
    }


    @Test
    public void testDiagnostics_parserError()
    {
        testDiagnostics(
                "###Mapping\n" +
                        "Mapping vscodelsp::test::EmployeeMapping\n" +
                        "(\n" +
                        "   Employee[emp] : Relational\n" +
                        "   {\n" +
                        "      hireDate   [EmployeeDatabase]EmployeeTable.hireDate,\n" +
                        "      hireType : [EmployeeDatabase]EmployeeTable.hireType\n" +
                        "   }\n" +
                        ")",
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT, 5, 35, 5, 47), "Unexpected token 'EmployeeTable'. Valid alternatives: ['(', ':']", Kind.Error, Source.Parser)
        );
    }

    @Test
    public void testDiagnostics_compilerError()
    {
        testDiagnostics(
                "###Mapping\n" +
                        "Mapping vscodelsp::test::EmployeeMapping\n" +
                        "(\n" +
                        "   Employee[emp] : Relational\n" +
                        "   {\n" +
                        "      hireDate : [EmployeeDatabase]EmployeeTable.hireDate,\n" +
                        "      hireType : [EmployeeDatabase]EmployeeTable.hireType\n" +
                        "   }\n" +
                        ")",
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT, 3, 3, 3, 10), "Can't find class 'Employee'", Kind.Error, Source.Compiler)
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
        codeFiles.put("vscodelsp::test::EmployeeMapping", "###Mapping\n" +
                "Mapping vscodelsp::test::EmployeeMapping\n" +
                "(\n" +
                "   Employee[emp] : Relational\n" +
                "   {\n" +
                "      hireDate : [EmployeeDatabase]EmployeeTable.hireDate,\n" +
                "      hireType : [EmployeeDatabase]EmployeeTable.hireType\n" +
                "   }\n" +
                ")");
        testDiagnostics(codeFiles, "vscodelsp::test::EmployeeMapping",
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource("vscodelsp::test::EmployeeMapping", 3, 3, 3, 10), "Can't find class 'Employee'", Kind.Error, Source.Compiler)
        );
    }

    @Test
    public void testLegendReference()
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

        codeFiles.put("vscodelsp::test::EmployeeMapping",
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

        LegendReference targetMappedClassReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::EmployeeMapping",3,  3, 3, 27))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::Employee", 1, 0, 6, 0))
                .build();

        testReferenceLookup(codeFiles, "vscodelsp::test::EmployeeMapping", TextPosition.newPosition(2, 1), null, "Outside of targetMappedClassReference-able element should yield nothing");
        testReferenceLookup(codeFiles, "vscodelsp::test::EmployeeMapping", TextPosition.newPosition(3, 2), null, "Outside of targetMappedClassReference-able element (before class name) should yield nothing");
        testReferenceLookup(codeFiles, "vscodelsp::test::EmployeeMapping", TextPosition.newPosition(3, 3), targetMappedClassReference, "Start of class been mapped, references to class definition");
        testReferenceLookup(codeFiles, "vscodelsp::test::EmployeeMapping", TextPosition.newPosition(3, 20), targetMappedClassReference, "Within the class name been mapped, references to class definition");
        testReferenceLookup(codeFiles, "vscodelsp::test::EmployeeMapping", TextPosition.newPosition(3, 27), targetMappedClassReference, "End of class name been mapped, references to class definition");
        testReferenceLookup(codeFiles, "vscodelsp::test::EmployeeMapping", TextPosition.newPosition(3, 28), null, "Outside of targetMappedClassReference-able element (after class name) should yield nothing");
        testReferenceLookup(codeFiles, "vscodelsp::test::EmployeeMapping", TextPosition.newPosition(4, 1), null, "Outside of targetMappedClassReference-able element should yield nothing");

        LegendReference srcMappedClassReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::EmployeeMapping",5,  11, 5, 38))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::EmployeeSrc", 1, 0, 6, 0))
                .build();

        testReferenceLookup(codeFiles, "vscodelsp::test::EmployeeMapping", TextPosition.newPosition(5, 12), srcMappedClassReference, "Source class reference");

        LegendReference propertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::EmployeeMapping",6,  6, 6, 13))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::Employee", 4, 4, 4, 22))
                .build();

        testReferenceLookup(codeFiles, "vscodelsp::test::EmployeeMapping", TextPosition.newPosition(6, 10), propertyReference, "Property mapped reference");
    }

    @Test
    public void testGetReferenceResolvers()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_CLASS_DOC_ID = "vscodelsp::test::Person";
        final String TEST_MAPPING_DOC_ID_1 = "vscodelsp::test::TestIncludeMapping";
        final String TEST_MAPPING_DOC_ID_2 = "vscodelsp::test::TestBaseMapping";
        codeFiles.put(TEST_CLASS_DOC_ID,
                "###Pure\n" +
                        "Class vscodelsp::test::Person\n" +
                        "{\n" +
                        "   name: String[1];\n" +
                        "   id: String[1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::Firm",
                "###Pure\n" +
                        "Class vscodelsp::test::Firm\n" +
                        "{\n" +
                        "   name: String[1];\n" +
                        "}");

        codeFiles.put(TEST_MAPPING_DOC_ID_1,
                "###Mapping\n" +
                        "Mapping vscodelsp::test::TestIncludeMapping\n" +
                        "(\n" +
                        "   include mapping vscodelsp::test::TestBaseMapping\n" +
                        "\n" +
                        "   vscodelsp::test::Person[per]: Pure\n" +
                        "   {\n" +
                        "       ~src vscodelsp::test::Person\n" +
                        "       ~filter if($src.id =='23', |if($src.name == 'test', |true, |true), |false)\n" +
                        "       id: '123',\n" +
                        "       name: 'John Doe'\n" +
                        "   }\n" +
                        ")");

        codeFiles.put(TEST_MAPPING_DOC_ID_2,
                "###Mapping\n" +
                        "Mapping vscodelsp::test::TestBaseMapping\n" +
                        "(\n" +
                        "   vscodelsp::test::Firm[firm]: Pure\n" +
                        "   {\n" +
                        "       ~src vscodelsp::test::Firm\n" +
                        "       name: 'ABC'\n" +
                        "   }\n" +
                        ")");

        LegendReference mappedIncludedMappingReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID_1, 3, 3, 3, 50))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID_2, 1, 0, 8, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID_1, TextPosition.newPosition(2, 2), null, "Outside of mappedIncludedMappingReference-able element should yield nothing");
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID_1, TextPosition.newPosition(3, 2), null, "Outside of mappedIncludedMappingReference-able element (before mapping name) should yield nothing");
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID_1, TextPosition.newPosition(3, 3), mappedIncludedMappingReference, "Start of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID_1, TextPosition.newPosition(3, 25), mappedIncludedMappingReference, "Within the mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID_1, TextPosition.newPosition(3, 50), mappedIncludedMappingReference, "End of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID_1, TextPosition.newPosition(4, 3), null, "Outside of mappedIncludedMappingReference-able element should yield nothing");

        LegendReference mappedClassPropertyReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID_1, 8, 23, 8, 24))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID, 4, 3, 4, 16))
                .build();

        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID_1, TextPosition.newPosition(8, 24), mappedClassPropertyReference, "Within the class property name has been mapped, referring to class property definition");
    }

    @Test
    public void testGetReferenceResolversMappingTestSuite()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_CLASS_DOC_ID_1 = "example::util::model::DateTimeWrapper";
        final String TEST_CLASS_DOC_ID_2 = "example::util::model::StrictDateWrapper";
        final String TEST_MAPPING_DOC_ID = "example::m2mExamplesByFunction::datePart::StrictDatesFromDateTimes";

        codeFiles.put(TEST_CLASS_DOC_ID_1,
                "###Pure\n" +
                        "Class example::util::model::DateTimeWrapper\n" +
                        "{\n" +
                        "  values: DateTime[*];\n" +
                        "}");

        codeFiles.put(TEST_CLASS_DOC_ID_2,
                "###Pure\n" +
                        "Class example::util::model::StrictDateWrapper\n" +
                        "{\n" +
                        "  values: StrictDate[*];\n" +
                        "}");

        codeFiles.put(TEST_MAPPING_DOC_ID,
                "###Mapping\n" +
                        "Mapping example::m2mExamplesByFunction::datePart::StrictDatesFromDateTimes\n" +
                        "(\n" +
                        "  *example::util::model::StrictDateWrapper: Pure\n" +
                        "  {\n" +
                        "    ~src example::util::model::DateTimeWrapper\n" +
                        "    values: $src.values->map(\n" +
                        "  dt|$dt->datePart()->cast(\n" +
                        "    @StrictDate\n" +
                        "  )\n" +
                        ")\n" +
                        "  }\n" +
                        "\n" +
                        "  testSuites:\n" +
                        "  [\n" +
                        "    TestSuite1:\n" +
                        "    {\n" +
                        "      function: |example::util::model::StrictDateWrapper.all()->graphFetch(\n" +
                        "  #{\n" +
                        "    example::util::model::StrictDateWrapper{\n" +
                        "      values\n" +
                        "    }\n" +
                        "  }#\n" +
                        ")->serialize(\n" +
                        "  #{\n" +
                        "    example::util::model::StrictDateWrapper{\n" +
                        "      values\n" +
                        "    }\n" +
                        "  }#\n" +
                        ");\n" +
                        "      tests:\n" +
                        "      [\n" +
                        "        Test1:\n" +
                        "        {\n" +
                        "          doc: 'Conversion of DateTime values to the StrictDate representing the date part of the value. Note that at read time, all dates are converted to UTC, and so date part will represent the value of the date component after this conversion.';\n" +
                        "          data:\n" +
                        "          [\n" +
                        "            ModelStore:\n" +
                        "              ModelStore\n" +
                        "              #{\n" +
                        "                example::util::model::DateTimeWrapper:\n" +
                        "                  ExternalFormat\n" +
                        "                  #{\n" +
                        "                    contentType: 'application/json';\n" +
                        "                    data: '{\\n  \"values\": [\\n    \"2023-11-22T23:58:44+0000\",\\n    \"2023-11-05T10:10:10+0000\",\\n    \"2023-11-05T02:10:10+0500\"\\n  ]\\n}';\n" +
                        "                  }#\n" +
                        "              }#\n" +
                        "          ];\n" +
                        "          asserts:\n" +
                        "          [\n" +
                        "            expectedAssertion:\n" +
                        "              EqualToJson\n" +
                        "              #{\n" +
                        "                expected:\n" +
                        "                  ExternalFormat\n" +
                        "                  #{\n" +
                        "                    contentType: 'application/json';\n" +
                        "                    data: '{\\n  \"values\": [\\n    \"2023-11-22\",\\n    \"2023-11-05\",\\n    \"2023-11-04\"\\n  ]\\n}';\n" +
                        "                  }#;\n" +
                        "              }#\n" +
                        "          ];\n" +
                        "        }\n" +
                        "      ];\n" +
                        "    }\n" +
                        "  ]\n" +
                        ")");

        LegendReference mappedClassReference1 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID, 19, 4, 19, 42))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID_2, 1, 0, 4, 0))
                .build();
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID, TextPosition.newPosition(19, 30), mappedClassReference1, "Within the mapping test suite class name has been mapped, referring to class definition");

        LegendReference mappedClassReference2 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID, 25, 4, 25, 42))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_CLASS_DOC_ID_2, 1, 0, 4, 0))
                .build();
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID, TextPosition.newPosition(25, 40), mappedClassReference2, "Within the mapping test suite class name has been mapped, referring to class definition");
    }

    @Test
    void testAnalyzeMappingModelCoverage()
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

        codeFiles.put("vscodelsp::test::EmployeeMapping",
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
        MutableList<SectionState> sectionStates = newSectionStates(codeFiles);
        SectionState mappingSectionState = sectionStates.detect(sectionState -> Objects.equals(sectionState.getDocumentState().getDocumentId(), "vscodelsp::test::EmployeeMapping"));

        String expected = "{\"mappedEntities\":[{\"info\":null,\"path\":\"vscodelsp::test::Employee\",\"properties\":[{\"mappedPropertyInfo\":null,\"name\":\"hireDate\"},{\"mappedPropertyInfo\":null,\"name\":\"hireType\"},{\"mappedPropertyInfo\":null,\"name\":\"foobar\"}]}]}";
        Iterable<? extends LegendExecutionResult> actual = testCommand(mappingSectionState, "vscodelsp::test::EmployeeMapping", "legend.mapping.analyzeMappingModelCoverage");

        Assertions.assertEquals(1, Iterate.sizeOf(actual));
        LegendExecutionResult result = actual.iterator().next();
        Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, result.getType(), result.getMessage());
        Assertions.assertEquals(expected, result.getMessage());
    }
}
