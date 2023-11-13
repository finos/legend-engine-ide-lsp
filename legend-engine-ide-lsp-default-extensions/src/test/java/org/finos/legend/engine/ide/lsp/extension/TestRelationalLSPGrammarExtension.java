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

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Kind;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Source;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.pure.m2.relational.M2RelationalPaths;
import org.junit.jupiter.api.Test;

public class TestRelationalLSPGrammarExtension extends AbstractLSPGrammarExtensionTest
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

    @Override
    protected LegendLSPGrammarExtension newExtension()
    {
        return new RelationalLSPGrammarExtension();
    }
}
