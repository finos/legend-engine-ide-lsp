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

import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.finos.legend.engine.ide.lsp.extension.agGrid.ColumnType;
import org.finos.legend.engine.ide.lsp.extension.agGrid.Filter;
import org.finos.legend.engine.ide.lsp.extension.agGrid.FilterOperation;
import org.finos.legend.engine.ide.lsp.server.service.FunctionTDSRequest;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSAggregation;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSGroupBy;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSRequest;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSSort;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSSortOrder;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.server.service.LegendLanguageServiceContract;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
// all tests should finish but in case of some uncaught deadlock, timeout whole test
public class TestLegendLanguageServerFunctionExecutionIntegration
{
    @RegisterExtension
    static LegendLanguageServerIntegrationExtension extension = new LegendLanguageServerIntegrationExtension();
    private LegendLanguageServiceContract legendLanguageService;

    private static class TDSRow
    {
        private final Object values;

        public TDSRow(Object values)
        {
            this.values = values;
        }

        public Object getValues()
        {
            return this.values;
        }
    }

    private static class TabularDataSet
    {
        private final List<String> columns;
        private final List<TDSRow> rows;

        public TabularDataSet(List<String> columns, List<TDSRow> rows)
        {
            this.columns = columns;
            this.rows = rows;
        }

        public List<String> getColumns()
        {
            return this.columns;
        }

        public List<TDSRow> getRows()
        {
            return this.rows;
        }
    }

    private static class TDSResult
    {
        private final TabularDataSet result;

        public TDSResult(TabularDataSet result)
        {
            this.result = result;
        }

        public TabularDataSet getResult()
        {
            return this.result;
        }
    }

    private TabularDataSet getTabularDataSet(Object result)
    {
        if (result instanceof LegendExecutionResult)
        {
            Gson gson = new Gson();
            return gson.fromJson(((LegendExecutionResult) result).getMessage(), TDSResult.class).getResult();
        }
        return null;
    }

    @BeforeEach
    void setUp()
    {
        this.legendLanguageService = extension.getServer().getLegendLanguageService();
    }

    @Test
    void testFunction() throws Exception
    {
        int sectionNum = 0;
        String entity = "model1::testReturnTDS__TabularDataSet_1_";
        testFunctionExecutionOnEntity(sectionNum, entity);
    }

    @Test
    void testService() throws Exception
    {
        int sectionNum = 4;
        String entity = "service::SampleService";
        testFunctionExecutionOnEntity(sectionNum, entity);
    }

    private void testFunctionExecutionOnEntity(int sectionNum, String entity) throws Exception
    {
        Path pureFile1 = prepareWorkspaceFiles();
        String uri = pureFile1.toUri().toString();

        List<TDSSort> sort = new ArrayList<>();
        List<Filter> filter = new ArrayList<>();
        List<String> columns = List.of("Legal Name", "Employees/ First Name", "Employees/ Last Name");
        List<String> groupByColumns = new ArrayList<>();
        List<String> groupKeys = new ArrayList<>();
        List<TDSAggregation> aggregations = new ArrayList<>();
        TDSGroupBy groupBy = new TDSGroupBy(groupByColumns, groupKeys, aggregations);
        TDSRequest request = new TDSRequest(0, 0, columns, filter, sort, groupBy);
        FunctionTDSRequest functionTDSRequest = new FunctionTDSRequest(uri, sectionNum, entity, request, Collections.emptyMap());

        // No push down operations
        Object resultObject = extension.futureGet(legendLanguageService.legendTDSRequest(functionTDSRequest));
        TabularDataSet result = getTabularDataSet(resultObject);
        Assertions.assertEquals(result.getColumns().size(), 3);
        Assertions.assertEquals(result.getRows().size(), 3);
        Assertions.assertEquals(result.getRows().get(0).getValues(), List.of("FirmA", "Doe", "John"));
        Assertions.assertEquals(result.getRows().get(1).getValues(), List.of("Apple", "Smith", "Tim"));
        Assertions.assertEquals(result.getRows().get(2).getValues(), List.of("FirmB", "Doe", "Nicole"));

        // Sort operation on first row
        sort.add(new TDSSort("Legal Name", TDSSortOrder.ASCENDING));
        resultObject = extension.futureGet(legendLanguageService.legendTDSRequest(functionTDSRequest));
        result = getTabularDataSet(resultObject);
        Assertions.assertEquals(result.getColumns().size(), 3);
        Assertions.assertEquals(result.getRows().size(), 3);
        Assertions.assertEquals(result.getRows().get(0).getValues(), List.of("Apple", "Smith", "Tim"));
        Assertions.assertEquals(result.getRows().get(1).getValues(), List.of("FirmA", "Doe", "John"));
        Assertions.assertEquals(result.getRows().get(2).getValues(), List.of("FirmB", "Doe", "Nicole"));

        // Filter operation on second row
        sort.clear();
        filter.add(new Filter("Employees/ First Name", ColumnType.String, FilterOperation.EQUALS, "Doe"));
        resultObject = extension.futureGet(legendLanguageService.legendTDSRequest(functionTDSRequest));
        result = getTabularDataSet(resultObject);
        Assertions.assertEquals(result.getColumns().size(), 3);
        Assertions.assertEquals(result.getRows().size(), 2);
        Assertions.assertEquals(result.getRows().get(0).getValues(), List.of("FirmA", "Doe", "John"));
        Assertions.assertEquals(result.getRows().get(1).getValues(), List.of("FirmB", "Doe", "Nicole"));

        // Groupby operation
        filter.clear();
        groupByColumns.add("Legal Name");
        resultObject = extension.futureGet(legendLanguageService.legendTDSRequest(functionTDSRequest));
        result = getTabularDataSet(resultObject);
        Assertions.assertEquals(result.getColumns().size(), 1);
        Assertions.assertEquals(result.getRows().size(), 3);
        Assertions.assertEquals(result.getRows().get(0).getValues(), List.of("Apple"));
        Assertions.assertEquals(result.getRows().get(1).getValues(), List.of("FirmA"));
        Assertions.assertEquals(result.getRows().get(2).getValues(), List.of("FirmB"));

        // Expand groupBy
        groupKeys.add("Apple");
        resultObject = extension.futureGet(legendLanguageService.legendTDSRequest(functionTDSRequest));
        result = getTabularDataSet(resultObject);
        Assertions.assertEquals(result.getColumns().size(), 3);
        Assertions.assertEquals(result.getRows().size(), 1);
        Assertions.assertEquals(result.getRows().get(0).getValues(), List.of("Apple", "Smith", "Tim"));
    }

    private static Path prepareWorkspaceFiles() throws Exception
    {
        return extension.addToWorkspace("file1.pure",
                "Class model::Person\n" +
                "{\n" +
                "  firstName: String[1];\n" +
                "  lastName: String[1];\n" +
                "}\n" +
                "Class model::Firm\n" +
                "{\n" +
                "  legalName: String[1];\n" +
                "  employees: model::Person[*];\n" +
                "}\n" +
                "function model1::testReturnTDS(): meta::pure::tds::TabularDataSet[1]\n" +
                "{\n" +
                "  model::Firm.all()->project([x | $x.legalName,x | $x.employees.firstName, x |$x.employees.lastName], ['Legal Name', 'Employees/ First Name', 'Employees/ Last Name'])->from(execution::RelationalMapping, execution::Runtime);\n" +
                "}\n" +
                "\n" +
                "###Mapping\n" +
                "Mapping execution::RelationalMapping\n" +
                "(\n" +
                "  *model::Person: Relational\n" +
                "  {\n" +
                "    ~primaryKey\n" +
                "    (\n" +
                "      [store::TestDB]PersonTable.id\n" +
                "    )\n" +
                "    ~mainTable [store::TestDB]PersonTable\n" +
                "    firstName: [store::TestDB]PersonTable.firstName,\n" +
                "    lastName: [store::TestDB]PersonTable.lastName\n" +
                "  }\n" +
                "  *model::Firm: Relational\n" +
                "  {\n" +
                "    ~primaryKey\n" +
                "    (\n" +
                "      [store::TestDB]FirmTable.id\n" +
                "    )\n" +
                "    ~mainTable [store::TestDB]FirmTable\n" +
                "    legalName: [store::TestDB]FirmTable.legal_name,\n" +
                "    employees[model_Person]: [store::TestDB]@FirmPerson\n" +
                "  }\n" +
                ")\n" +
                "\n" +
                "###Runtime\n" +
                "Runtime execution::Runtime\n" +
                "{\n" +
                "  mappings:\n" +
                "  [\n" +
                "    execution::RelationalMapping\n" +
                "  ];\n" +
                "  connections:\n" +
                "  [\n" +
                "    store::TestDB:\n" +
                "    [\n" +
                "      connection_1:\n" +
                "      #{\n" +
                "        RelationalDatabaseConnection\n" +
                "        {\n" +
                "          store: store::TestDB;\n" +
                "          type: H2;\n" +
                "          specification: LocalH2\n" +
                "          {\n" +
                "            testDataSetupSqls: [\n" +
                "              'Drop table if exists FirmTable;\\nDrop table if exists PersonTable;\\nCreate Table FirmTable(id INT, Legal_Name VARCHAR(200));\\nCreate Table PersonTable(id INT, firm_id INT, lastName VARCHAR(200), firstName VARCHAR(200));\\nInsert into FirmTable (id, Legal_Name) values (1, \\'FirmA\\');\\nInsert into FirmTable (id, Legal_Name) values (2, \\'Apple\\');\\nInsert into FirmTable (id, Legal_Name) values (3, \\'FirmB\\');\\nInsert into PersonTable (id, firm_id, lastName, firstName) values (1, 1, \\'John\\', \\'Doe\\');\\nInsert into PersonTable (id, firm_id, lastName, firstName) values (2, 2, \\'Tim\\', \\'Smith\\');\\nInsert into PersonTable (id, firm_id, lastName, firstName) values (3, 3, \\'Nicole\\', \\'Doe\\');\\n\\n'\n" +
                "              ];\n" +
                "          };\n" +
                "          auth: DefaultH2;\n" +
                "        }\n" +
                "      }#\n" +
                "    ]\n" +
                "  ];\n" +
                "}\n" +
                "\n" +
                "###Relational\n" +
                "Database store::TestDB\n" +
                "(\n" +
                "  Table FirmTable\n" +
                "  (\n" +
                "    id INTEGER PRIMARY KEY,\n" +
                "    legal_name VARCHAR(200)\n" +
                "  )\n" +
                "  Table PersonTable\n" +
                "  (\n" +
                "    id INTEGER PRIMARY KEY,\n" +
                "    firm_id INTEGER,\n" +
                "    firstName VARCHAR(200),\n" +
                "    lastName VARCHAR(200)\n" +
                "  )\n" +
                "\n" +
                "  Join FirmPerson(PersonTable.firm_id = FirmTable.id)\n" +
                ")\n" +
                "\n" +
                "###Service\n" +
                "Service service::SampleService\n" +
                "{\n" +
                "    pattern : 'test';\n" +
                "    documentation : 'service for testing';\n" +
                "    execution : Single\n" +
                "    {\n" +
                "        query : model::Firm.all()->project([x | $x.legalName,x | $x.employees.firstName, x |$x.employees.lastName], ['Legal Name', 'Employees/ First Name', 'Employees/ Last Name'])->from(execution::RelationalMapping, execution::Runtime);" +
                "    }\n" +
                "}\n");
    }
}
