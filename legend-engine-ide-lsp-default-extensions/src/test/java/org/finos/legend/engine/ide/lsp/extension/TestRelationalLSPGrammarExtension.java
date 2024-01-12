// Copyright 2023 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.engine.ide.lsp.extension;

import java.util.Set;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Kind;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Source;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.pure.m2.relational.M2RelationalPaths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.finos.legend.engine.ide.lsp.extension.RelationalLSPGrammarExtension.GENERATE_MODEL_MAPPING_COMMAND_ID;

public class TestRelationalLSPGrammarExtension extends AbstractLSPGrammarExtensionTest<RelationalLSPGrammarExtension>
{
    @Test
    public void testGetName()
    {
        testGetName("Relational");
    }

    @Test
    public void testGetDeclarations()
    {
        testGetDeclarations(
                "###Relational\n" +
                        "\r\n" +
                        "\n" +
                        "Database test::store::TestDatabase\n" +
                        "(\r\n" +
                        "    Table T1\n" +
                        "    (\n" +
                        "        ID INT, NAME VARCHAR(200)\n" +
                        "    )\n" +
                        "\n" +
                        "    Schema S1\n" +
                        "    (\n" +
                        "        Table T2\n" +
                        "        (\n" +
                        "            ID INT,\n" +
                        "            NAME VARCHAR(200)\n" +
                        "        )\n" +
                        "    )\n" +
                        ")\n",
                LegendDeclaration.builder().withIdentifier("test::store::TestDatabase").withClassifier(M2RelationalPaths.Database).withLocation(3, 0, 18, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("S1").withClassifier(M2RelationalPaths.Schema).withLocation(10, 4, 17, 4)
                                .withChild(LegendDeclaration.builder().withIdentifier("T2").withClassifier(M2RelationalPaths.Table).withLocation(12, 8, 16, 8)
                                        .withChild(LegendDeclaration.builder().withIdentifier("ID").withClassifier(M2RelationalPaths.Column).withLocation(14, 12, 14, 17).build())
                                        .withChild(LegendDeclaration.builder().withIdentifier("NAME").withClassifier(M2RelationalPaths.Column).withLocation(15, 12, 15, 28).build())
                                        .build())
                                .build())
                        .withChild(LegendDeclaration.builder().withIdentifier("default").withClassifier(M2RelationalPaths.Schema).withLocation(3, 0, 18, 0)
                                .withChild(LegendDeclaration.builder().withIdentifier("T1").withClassifier(M2RelationalPaths.Table).withLocation(5, 4, 8, 4)
                                        .withChild(LegendDeclaration.builder().withIdentifier("ID").withClassifier(M2RelationalPaths.Column).withLocation(7, 8, 7, 13).build())
                                        .withChild(LegendDeclaration.builder().withIdentifier("NAME").withClassifier(M2RelationalPaths.Column).withLocation(7, 16, 7, 32).build())
                                        .build())
                                .build())
                        .build()
        );
    }

    @Test
    public void testDiagnostics_parserError()
    {
        testDiagnostics(
                "###Relational\n" +
                        "Database vscodelsp::test::EmployeeDatabase\n" +
                        "(\n" +
                        "   Table EmployeeTable(id INT PRIMARY KEY, hireDate DATE, hireType VARCHAR(10), fteFactor DOUBLE \n" +
                        "   Table EmployeeDetailsTable(id INT PRIMARY KEY, birthDate DATE, yearsOfExperience DOUBLE)\n" +
                        "   Table FirmTable(firmName VARCHAR(100) PRIMARY KEY, employeeId INT PRIMARY KEY)\n" +
                        "\n" +
                        "   Join JoinEmployeeToFirm(EmployeeTable.id = FirmTable.employeeId)\n" +
                        "   Join JoinEmployeeToemployeeDetails(EmployeeTable.id = EmployeeDetailsTable.id)\n" +
                        ")",
                LegendDiagnostic.newDiagnostic(TextInterval.newInterval(4, 3, 4, 7), "Unexpected token", Kind.Error, Source.Parser)
        );
    }

    @Test
    public void testDiagnostics_compileError()
    {
        testDiagnostics(
                "###Relational\n" +
                        "Database vscodelsp::test::EmployeeDatabase\n" +
                        "(\n" +
                        "   Table EmployeeTable(id INT PRIMARY KEY, hireDate DATE, hireType VARCHAR(10), fteFactor DOUBLE)\n" +
                        "   Table EmployeeDetailsTable(id INT PRIMARY KEY, birthDate DATE, yearsOfExperience DOUBLE)\n" +
                        "   Table FirmTable(firmName VARCHAR(100) PRIMARY KEY, employeeId INT PRIMARY KEY)\n" +
                        "\n" +
                        "   Join JoinEmployeeToFirm(EmployeeTable.id = FirmTable.employeeId)\n" +
                        "   Join JoinEmployeeToEmployeeDetails(EmployeeTable.id = EmployeeDetailsTable.id)\n" +
                        "   Join JoinEmployeeToNowhere(EmployeeTable.id = UnknownTable.id)\n" +
                        ")",
                LegendDiagnostic.newDiagnostic(TextInterval.newInterval(9, 49, 9, 60), "Can't find table 'UnknownTable' in schema 'default' and database 'EmployeeDatabase'", Kind.Error, Source.Compiler)
        );
    }

    @Test
    public void testDiagnostics_multipleFiles_compilerError()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        codeFiles.put("vscodelsp::test::EmployeeDatabase", "###Relational\n" +
                "Database vscodelsp::test::EmployeeDatabase\n" +
                "(\n" +
                "   Table EmployeeTable(id INT PRIMARY KEY, hireDate DATE, hireType VARCHAR(10), fteFactor DOUBLE)\n" +
                "   Table EmployeeDetailsTable(id INT PRIMARY KEY, birthDate DATE, yearsOfExperience DOUBLE)\n" +
                "   Table FirmTable(firmName VARCHAR(100) PRIMARY KEY, employeeId INT PRIMARY KEY)\n" +
                "\n" +
                "   Join JoinEmployeeToFirm(EmployeeTable.id = FirmTable.employeeId)\n" +
                "   Join JoinEmployeeToEmployeeDetails(EmployeeTable.id = EmployeeDetailsTable.id)\n" +
                "   Join JoinEmployeeToNowhere(EmployeeTable.id = UnknownTable.id)\n" +
                ")");
        codeFiles.put("vscodelsp::test::StudentDatabase", "###Relational\n" +
                "Database vscodelsp::test::StudentDatabase\n" +
                "(\n" +
                ")");
        testDiagnostics(codeFiles, "vscodelsp::test::EmployeeDatabase", LegendDiagnostic.newDiagnostic(TextInterval.newInterval(9, 49, 9, 60), "Can't find table 'UnknownTable' in schema 'default' and database 'EmployeeDatabase'", Kind.Error, Source.Compiler));
    }

    @Test
    public void testDiagnostics_noError()
    {
        testDiagnostics(
                "###Relational\n" +
                        "Database vscodelsp::test::EmployeeDatabase\n" +
                        "(\n" +
                        "   Table EmployeeTable(id INT PRIMARY KEY, hireDate DATE, hireType VARCHAR(10), fteFactor DOUBLE)\n" +
                        "   Table EmployeeDetailsTable(id INT PRIMARY KEY, birthDate DATE, yearsOfExperience DOUBLE)\n" +
                        "   Table FirmTable(firmName VARCHAR(100) PRIMARY KEY, employeeId INT PRIMARY KEY)\n" +
                        "\n" +
                        "   Join JoinEmployeeToFirm(EmployeeTable.id = FirmTable.employeeId)\n" +
                        "   Join JoinEmployeeToemployeeDetails(EmployeeTable.id = EmployeeDetailsTable.id)\n" +
                        ")"
        );
    }

    @Test
    void testGenerateSampleModelsCommand()
    {
        Iterable<? extends LegendExecutionResult> legendExecutionResults = testCommand(
                "###Relational\n" +
                        "Database vscodelsp::test::EmployeeDatabase\n" +
                        "(\n" +
                        "   Table EmployeeTable(id INT PRIMARY KEY, hireDate DATE, hireType VARCHAR(10), fteFactor DOUBLE)\n" +
                        "   Table EmployeeDetailsTable(id INT PRIMARY KEY, birthDate DATE, yearsOfExperience DOUBLE)\n" +
                        "   Table FirmTable(firmName VARCHAR(100) PRIMARY KEY, employeeId INT PRIMARY KEY)\n" +
                        "\n" +
                        "   Join JoinEmployeeToFirm(EmployeeTable.id = FirmTable.employeeId)\n" +
                        "   Join JoinEmployeeToemployeeDetails(EmployeeTable.id = EmployeeDetailsTable.id)\n" +
                        ")",
                "vscodelsp::test::EmployeeDatabase",
                GENERATE_MODEL_MAPPING_COMMAND_ID
        );

        Assertions.assertEquals(1, Iterate.sizeOf(legendExecutionResults));
        LegendExecutionResult result = legendExecutionResults.iterator().next();
        Assertions.assertEquals(LegendExecutionResult.Type.SUCCESS, result.getType());
        Assertions.assertEquals("***WARNING***\n" +
                "These models and mappings are intended only as examples.\n" +
                "They should not be considered a replacement for thoughtful modeling.\n" +
                "Please review carefully before making any use of them.\n" +
                "***WARNING***\n" +
                "\n" +
                "\n" +
                "Class {meta::pure::profiles::doc.doc = 'Generated Element'} vscodelsp::test::default::EmployeeTable\n" +
                "{\n" +
                "  id: Integer[1];\n" +
                "  hireDate: StrictDate[0..1];\n" +
                "  hireType: String[0..1];\n" +
                "  fteFactor: Float[0..1];\n" +
                "}\n" +
                "\n" +
                "Class {meta::pure::profiles::doc.doc = 'Generated Element'} vscodelsp::test::default::EmployeeDetailsTable\n" +
                "{\n" +
                "  id: Integer[1];\n" +
                "  birthDate: StrictDate[0..1];\n" +
                "  yearsOfExperience: Float[0..1];\n" +
                "}\n" +
                "\n" +
                "Class {meta::pure::profiles::doc.doc = 'Generated Element'} vscodelsp::test::default::FirmTable\n" +
                "{\n" +
                "  firmName: String[1];\n" +
                "  employeeId: Integer[1];\n" +
                "}\n" +
                "\n" +
                "Association {meta::pure::profiles::doc.doc = 'Generated Element'} vscodelsp::test::JoinEmployeeToFirm\n" +
                "{\n" +
                "  joinEmployeeToFirmDefaultEmployeeTable: vscodelsp::test::default::EmployeeTable[1];\n" +
                "  joinEmployeeToFirmDefaultFirmTable: vscodelsp::test::default::FirmTable[1];\n" +
                "}\n" +
                "\n" +
                "Association {meta::pure::profiles::doc.doc = 'Generated Element'} vscodelsp::test::JoinEmployeeToemployeeDetails\n" +
                "{\n" +
                "  joinEmployeeToemployeeDetailsDefaultEmployeeTable: vscodelsp::test::default::EmployeeTable[1];\n" +
                "  joinEmployeeToemployeeDetailsDefaultEmployeeDetailsTable: vscodelsp::test::default::EmployeeDetailsTable[1];\n" +
                "}\n" +
                "\n" +
                "\n" +
                "###Mapping\n" +
                "Mapping vscodelsp::test::EmployeeDatabaseMapping\n" +
                "(\n" +
                "  *vscodelsp::test::default::EmployeeTable[vscodelsp_test_default_EmployeeTable]: Relational\n" +
                "  {\n" +
                "    ~primaryKey\n" +
                "    (\n" +
                "      [vscodelsp::test::EmployeeDatabase]EmployeeTable.id\n" +
                "    )\n" +
                "    ~mainTable [vscodelsp::test::EmployeeDatabase]EmployeeTable\n" +
                "    id: [vscodelsp::test::EmployeeDatabase]EmployeeTable.id,\n" +
                "    hireDate: [vscodelsp::test::EmployeeDatabase]EmployeeTable.hireDate,\n" +
                "    hireType: [vscodelsp::test::EmployeeDatabase]EmployeeTable.hireType,\n" +
                "    fteFactor: [vscodelsp::test::EmployeeDatabase]EmployeeTable.fteFactor\n" +
                "  }\n" +
                "  *vscodelsp::test::default::EmployeeDetailsTable[vscodelsp_test_default_EmployeeDetailsTable]: Relational\n" +
                "  {\n" +
                "    ~primaryKey\n" +
                "    (\n" +
                "      [vscodelsp::test::EmployeeDatabase]EmployeeDetailsTable.id\n" +
                "    )\n" +
                "    ~mainTable [vscodelsp::test::EmployeeDatabase]EmployeeDetailsTable\n" +
                "    id: [vscodelsp::test::EmployeeDatabase]EmployeeDetailsTable.id,\n" +
                "    birthDate: [vscodelsp::test::EmployeeDatabase]EmployeeDetailsTable.birthDate,\n" +
                "    yearsOfExperience: [vscodelsp::test::EmployeeDatabase]EmployeeDetailsTable.yearsOfExperience\n" +
                "  }\n" +
                "  *vscodelsp::test::default::FirmTable[vscodelsp_test_default_FirmTable]: Relational\n" +
                "  {\n" +
                "    ~primaryKey\n" +
                "    (\n" +
                "      [vscodelsp::test::EmployeeDatabase]FirmTable.firmName,\n" +
                "      [vscodelsp::test::EmployeeDatabase]FirmTable.employeeId\n" +
                "    )\n" +
                "    ~mainTable [vscodelsp::test::EmployeeDatabase]FirmTable\n" +
                "    firmName: [vscodelsp::test::EmployeeDatabase]FirmTable.firmName,\n" +
                "    employeeId: [vscodelsp::test::EmployeeDatabase]FirmTable.employeeId\n" +
                "  }\n" +
                "\n" +
                "  vscodelsp::test::JoinEmployeeToFirm: Relational\n" +
                "  {\n" +
                "    AssociationMapping\n" +
                "    (\n" +
                "      joinEmployeeToFirmDefaultEmployeeTable[vscodelsp_test_default_FirmTable,vscodelsp_test_default_EmployeeTable]: [vscodelsp::test::EmployeeDatabase]@JoinEmployeeToFirm,\n" +
                "      joinEmployeeToFirmDefaultFirmTable[vscodelsp_test_default_EmployeeTable,vscodelsp_test_default_FirmTable]: [vscodelsp::test::EmployeeDatabase]@JoinEmployeeToFirm\n" +
                "    )\n" +
                "  }\n" +
                "  vscodelsp::test::JoinEmployeeToemployeeDetails: Relational\n" +
                "  {\n" +
                "    AssociationMapping\n" +
                "    (\n" +
                "      joinEmployeeToemployeeDetailsDefaultEmployeeTable[vscodelsp_test_default_EmployeeDetailsTable,vscodelsp_test_default_EmployeeTable]: [vscodelsp::test::EmployeeDatabase]@JoinEmployeeToemployeeDetails,\n" +
                "      joinEmployeeToemployeeDetailsDefaultEmployeeDetailsTable[vscodelsp_test_default_EmployeeTable,vscodelsp_test_default_EmployeeDetailsTable]: [vscodelsp::test::EmployeeDatabase]@JoinEmployeeToemployeeDetails\n" +
                "    )\n" +
                "  }\n" +
                ")\n", result.getMessage());
    }

    @Test
    void testAntlrExpectedTokens()
    {
        Set<String> antlrExpectedTokens = this.extension.getAntlrExpectedTokens();
        Assertions.assertEquals(Set.of("Database"), antlrExpectedTokens);
    }

    @Override
    protected RelationalLSPGrammarExtension newExtension()
    {
        return new RelationalLSPGrammarExtension();
    }
}
