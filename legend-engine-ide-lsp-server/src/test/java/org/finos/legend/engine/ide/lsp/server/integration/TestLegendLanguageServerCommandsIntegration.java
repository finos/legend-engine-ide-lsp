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

import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.finos.legend.engine.ide.lsp.commands.RunAllTestCasesCommandExecutionHandler;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
// all tests should finish but in case of some uncaught deadlock, timeout whole test
public class TestLegendLanguageServerCommandsIntegration
{
    @RegisterExtension
    static LegendLanguageServerIntegrationExtension extension = new LegendLanguageServerIntegrationExtension();

    @BeforeAll
    static void beforeAll() throws Exception
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
                "            assert1:\n" +
                "              EqualToJson\n" +
                "              #{\n" +
                "                expected :\n" +
                "                  ExternalFormat\n" +
                "                  #{\n" +
                "                    contentType: 'application/json';\n" +
                "                    data: '{\"id\" : 77, \"name\" : \"john doe\"}';\n" +
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
    void runAllTestCommand() throws Exception
    {
        List<LegendExecutionResult> legendExecutionResults = extension.futureGet(extension.getServer().getWorkspaceService().executeCommand(new ExecuteCommandParams(RunAllTestCasesCommandExecutionHandler.RUN_ALL_TESTS_COMMAND, List.of())), new TypeToken<List<LegendExecutionResult>>()
        {
        });

        Map<String, LegendExecutionResult.Type> resultMap = legendExecutionResults.stream().collect(Collectors.toMap(x -> String.join(".", x.getIds()), LegendExecutionResult::getType));

        Map<Object, Object> expected = Map.of(
                "model::HelloAgain_String_1__String_1_.testSuite_1.testFail.default", LegendExecutionResult.Type.FAILURE,
                "model::Hello_String_1__String_1_.testSuite_1.testPass.default", LegendExecutionResult.Type.SUCCESS,
                "test::modelToModelMapping.testSuite1.test1.assert1", LegendExecutionResult.Type.SUCCESS,
                "test::MyMapping.test_1", LegendExecutionResult.Type.SUCCESS
        );

        Assertions.assertEquals(expected, resultMap);
    }
}
