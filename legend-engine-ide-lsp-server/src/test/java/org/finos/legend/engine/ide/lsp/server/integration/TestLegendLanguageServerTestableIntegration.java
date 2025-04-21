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

package org.finos.legend.engine.ide.lsp.server.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTest;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestAssertionResult;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.server.service.ExecuteTestRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 5, unit = TimeUnit.MINUTES)
// all tests should finish but in case of some uncaught deadlock, timeout whole test
public class TestLegendLanguageServerTestableIntegration
{
    @RegisterExtension
    static LegendLanguageServerIntegrationExtension extension = new LegendLanguageServerIntegrationExtension();

    void workspaceWithTestables() throws Exception
    {
        String code1 = "function model::Hello(name: String[1]): String[1]\n" +
                "{\n" +
                "  'Hello World! My name is ' + $name + '.';\n" +
                "}\n" +
                "{\n" +
                "  testSuite_1\n" +
                "  (\n" +
                "    testPass | Hello('John') => 'Hello World! My name is John.';\n" +
                "  )\n" +
                "}\n";
        extension.addToWorkspace("file1.pure", code1);

        String code2 = "function model::HelloAgain(name: String[1]): String[1]\n" +
                "{\n" +
                "  'Hello World! My name is ' + $name + '.';\n" +
                "}\n" +
                "{\n" +
                "  testSuite_1\n" +
                "  (\n" +
                "    testFail | HelloAgain('John') => 'Hello World! My name is Johnx.';\n" +
                "  )\n" +
                "}\n";
        extension.addToWorkspace("file2.pure", code2);

        String legacyMapping = "###Relational\n" +
                "Database test::DB\n" +
                "(\n" +
                "  Table PersonTable\n" +
                "  (\n" +
                "    id INTEGER PRIMARY KEY,\n" +
                "    firmId INTEGER,\n" +
                "    lastName VARCHAR(200)\n" +
                "  )\n" +
                "  Table FirmTable\n" +
                "  (\n" +
                "    id INTEGER PRIMARY KEY,\n" +
                "    legalName VARCHAR(200)\n" +
                "  )\n" +
                "\n" +
                "  Join FirmPerson(PersonTable.firmId = FirmTable.id)\n" +
                ")\n" +
                "\n" +
                "\n" +
                "###Pure\n" +
                "Class test::Firm\n" +
                "{\n" +
                "  employees: test::Person[*];\n" +
                "  legalName: String[1];\n" +
                "}\n" +
                "\n" +
                "Class test::Person\n" +
                "{\n" +
                "  firstName: String[1];\n" +
                "  lastName: String[1];\n" +
                "}\n" +
                "###Mapping\n" +
                "Mapping test::MyMapping\n" +
                "(\n" +
                "  *test::Person: Relational\n" +
                "  {\n" +
                "    ~primaryKey\n" +
                "    (\n" +
                "      [test::DB]PersonTable.id\n" +
                "    )\n" +
                "    ~mainTable [test::DB]PersonTable\n" +
                "    lastName: [test::DB]PersonTable.lastName,\n" +
                "    firstName: [test::DB]PersonTable.lastName\n" +
                "  }\n" +
                "  *test::Firm: Relational\n" +
                "  {\n" +
                "    ~primaryKey\n" +
                "    (\n" +
                "      [test::DB]FirmTable.id\n" +
                "    )\n" +
                "    ~mainTable [test::DB]FirmTable\n" +
                "    employees[test_Person]: [test::DB]@FirmPerson,\n" +
                "    legalName: [test::DB]FirmTable.legalName\n" +
                "  }\n" +
                "  MappingTests\n" +
                "  [\n" +
                "    test_1\n" +
                "    (\n" +
                "      query: |test::Person.all()->project([p|$p.lastName],['lastName']);\n" +
                "      data:\n" +
                "      [\n" +
                "        <Relational, SQL, test::DB, 'Drop table if exists PersonTable;\\nCreate Table PersonTable(id INT, firmId INT, lastName VARCHAR(200));\\nInsert into PersonTable (id, firmId, lastName) values (1, 1, \\'Doe\\;\\');\\nInsert into PersonTable (id, firmId, lastName) values (2, 1, \\'Doe2\\');'>\n" +
                "      ];\n" +
                "      assert: '[ {\\n  \"values\" : [ \"Doe;\" ]\\n}, {\\n  \"values\" : [ \"Doe2\" ]\\n} ]';\n" +
                "    )\n" +
                "  ]\n" +
                ")\n";

        extension.addToWorkspace("file3.pure", legacyMapping);

        String testableMapping = "###Pure\n" +
                "Class test::model\n" +
                "{\n" +
                "    name: String[1];\n" +
                "    id: String[1];\n" +
                "}\n" +
                "\n" +
                "Class test::changedModel{    name: String[1];    id: Integer[1];}\n" +
                "###Data\n" +
                "Data test::data::MyData\n" +
                "{\n" +
                "  ExternalFormat\n" +
                "  #{\n" +
                "    contentType: 'application/json';\n" +
                "    data: '{\"name\":\"john doe\",\"id\":\"77\"}';\n" +
                "  }#\n" +
                "}\n" +
                "\n" +
                "###Mapping\n" +
                "Mapping test::modelToModelMapping\n" +
                "(\n" +
                "    *test::changedModel: Pure\n" +
                "{\n" +
                "    ~src test::model\n" +
                "    name: $src.name,\n" +
                "    id: $src.id->parseInteger()\n" +
                "}\n" +
                "\n" +
                "  testSuites:\n" +
                "  [\n" +
                "    testSuite1:\n" +
                "    {\n" +
                "      function: |test::changedModel.all()->graphFetch(#{test::changedModel{id,name}}#)->serialize(#{test::changedModel{id,name}}#);\n" +
                "      tests:\n" +
                "      [\n" +
                "        test1:\n" +
                "        {\n" +
                "         data:\n" +
                "         [\n" +
                "           ModelStore: ModelStore\n" +
                "             #{\n" +
                "               test::model:\n" +
                "                Reference\n" +
                "                #{\n" +
                "                  test::data::MyData\n" +
                "                }#\n" +
                "             }#\n" +
                "           ];\n" +
                "          asserts:\n" +
                "          [\n" +
                "            thisWillFail:\n" +
                "              EqualToJson\n" +
                "              #{\n" +
                "                expected :\n" +
                "                  ExternalFormat\n" +
                "                  #{\n" +
                "                    contentType: 'application/json';\n" +
                "                    data: '{\"id\" : 75, \"name\" : \"john doe\"}';\n" +
                "                  }#;\n" +
                "              }#\n" +
                "          ];\n" +
                "        }\n" +
                "      ];\n" +
                "    }\n" +
                "  ]\n" +
                ")\n";

        extension.addToWorkspace("file4.pure", testableMapping);

        extension.assertWorkspaceParseAndCompiles();
    }

    @Test
    void testDiscoveringAndExecutionOfTestCases() throws Exception
    {
        this.workspaceWithTestables();
        List<LegendTest> legendTests = extension.futureGet(extension.getServer().getLegendLanguageService().testCases());

        Assertions.assertEquals(List.of("model::HelloAgain_String_1__String_1_", "model::Hello_String_1__String_1_", "test::modelToModelMapping"), legendTests.stream().map(LegendTest::getId).sorted().collect(Collectors.toList()));

        List<LegendTestExecutionResult> results = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<Void> allOf = CompletableFuture.allOf(legendTests.stream().map(test -> extension.getServer()
                        .getLegendLanguageService().executeTests(new ExecuteTestRequest(test.getLocation(), test.getId(), List.of()))
                        .thenAccept(results::addAll))
                .toArray(CompletableFuture[]::new)
        );

        extension.futureGet(allOf);

        results.sort(Comparator.comparing(LegendTestExecutionResult::getId));

        String doc2Id = extension.resolveWorkspacePath("file2.pure").toUri().toString();
        String doc4Id = extension.resolveWorkspacePath("file4.pure").toUri().toString();

        LegendTestAssertionResult failure1 = LegendTestAssertionResult.failure("default", TextLocation.newTextSource(doc2Id, 7, 37, 7, 68), "expected:Hello World! My name is Johnx., Found : Hello World! My name is John.",null, null);
        LegendTestExecutionResult expectedResult1 = LegendTestExecutionResult.failures(List.of(failure1), "model::HelloAgain_String_1__String_1_", "testSuite_1", "testFail");
        LegendTestExecutionResult expectedResult2 = LegendTestExecutionResult.success("model::Hello_String_1__String_1_", "testSuite_1", "testPass");
        LegendTestAssertionResult failure3 = LegendTestAssertionResult.failure("thisWillFail",
                TextLocation.newTextSource(doc4Id, 51, 14, 59, 15),
                "Actual result does not match Expected result",
                "{" + System.lineSeparator() +
                "  \"id\" : 75," + System.lineSeparator() +
                "  \"name\" : \"john doe\"" + System.lineSeparator() +
                "}",
                "{" + System.lineSeparator() +
                "  \"id\" : 77," + System.lineSeparator() +
                "  \"name\" : \"john doe\"" + System.lineSeparator() +
                "}");
        LegendTestExecutionResult expectedResult3 = LegendTestExecutionResult.failures(List.of(failure3), "test::modelToModelMapping", "testSuite1", "test1");

        Assertions.assertEquals(List.of(expectedResult1, expectedResult2, expectedResult3), results);

    }
}
