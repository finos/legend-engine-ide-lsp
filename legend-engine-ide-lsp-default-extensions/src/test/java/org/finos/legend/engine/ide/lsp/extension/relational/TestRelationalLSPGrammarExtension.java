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

package org.finos.legend.engine.ide.lsp.extension.relational;

import java.util.Set;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtensionTest;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Kind;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic.Source;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.pure.m2.relational.M2RelationalPaths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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
                LegendDeclaration.builder().withIdentifier("test::store::TestDatabase").withClassifier(M2RelationalPaths.Database).withLocation(DOC_ID_FOR_TEXT,3, 0, 18, 0)
                        .withChild(LegendDeclaration.builder().withIdentifier("S1").withClassifier(M2RelationalPaths.Schema).withLocation(DOC_ID_FOR_TEXT,10, 4, 17, 4)
                                .withChild(LegendDeclaration.builder().withIdentifier("T2").withClassifier(M2RelationalPaths.Table).withLocation(DOC_ID_FOR_TEXT,12, 8, 16, 8)
                                        .withChild(LegendDeclaration.builder().withIdentifier("ID").withClassifier(M2RelationalPaths.Column).withLocation(DOC_ID_FOR_TEXT,14, 12, 14, 17).build())
                                        .withChild(LegendDeclaration.builder().withIdentifier("NAME").withClassifier(M2RelationalPaths.Column).withLocation(DOC_ID_FOR_TEXT,15, 12, 15, 28).build())
                                        .build())
                                .build())
                        .withChild(LegendDeclaration.builder().withIdentifier("T1").withClassifier(M2RelationalPaths.Table).withLocation(DOC_ID_FOR_TEXT,5, 4, 8, 4)
                                .withChild(LegendDeclaration.builder().withIdentifier("ID").withClassifier(M2RelationalPaths.Column).withLocation(DOC_ID_FOR_TEXT,7, 8, 7, 13).build())
                                .withChild(LegendDeclaration.builder().withIdentifier("NAME").withClassifier(M2RelationalPaths.Column).withLocation(DOC_ID_FOR_TEXT,7, 16, 7, 32).build())
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
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT,4, 3, 4, 7), "Unexpected token", Kind.Error, Source.Parser)
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
                LegendDiagnostic.newDiagnostic(TextLocation.newTextSource(DOC_ID_FOR_TEXT,9, 49, 9, 60), "Can't find table 'UnknownTable' in schema 'default' and database 'EmployeeDatabase'", Kind.Error, Source.Compiler)
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
        testDiagnostics(codeFiles, "vscodelsp::test::EmployeeDatabase", LegendDiagnostic.newDiagnostic(TextLocation.newTextSource("vscodelsp::test::EmployeeDatabase",9, 49, 9, 60), "Can't find table 'UnknownTable' in schema 'default' and database 'EmployeeDatabase'", Kind.Error, Source.Compiler));
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
    public void testCompletion()
    {
        String code = "###Relational" +
                        "Database package::path::storeName\n" +
                        "(\n" +
                        "Schema schemaName\n" +
                        "(\n" +
                        "Table TableName(column1 INT PRIMARY KEY, column2 DATE)\n" +
                        "View ViewName(column3 VARCHAR(10) PRIMARY KEY)\n" +
                        ")\n" +
                        "Join \n" +
                        "Filter \n" +
                ")\n";

        String boilerPlate = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(2, 0)).iterator().next().getDescription();
        Assertions.assertEquals("Relational boilerplate", boilerPlate);

        Iterable<? extends LegendCompletion> noCompletion = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(2, 1));
        Assertions.assertFalse(noCompletion.iterator().hasNext());

        String schemaSuggestions = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(2, 7)).iterator().next().getDescription();
        Assertions.assertEquals("Schema definition", schemaSuggestions);

        String tableSuggestions = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(4, 6)).iterator().next().getDescription();
        Assertions.assertEquals("Table definition", tableSuggestions);

        String viewSuggestions = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(5, 5)).iterator().next().getDescription();
        Assertions.assertEquals("View definition", viewSuggestions);

        String joinSuggestions = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(7, 5)).iterator().next().getDescription();
        Assertions.assertEquals("Join definition", joinSuggestions);

        String filterSuggestions = this.extension.getCompletions(newSectionState("", code), TextPosition.newPosition(8, 7)).iterator().next().getDescription();
        Assertions.assertEquals("Filter definition", filterSuggestions);
    }

    @Test
    void testAntlrExpectedTokens()
    {
        Set<String> antlrExpectedTokens = this.extension.getAntlrExpectedTokens();
        Assertions.assertEquals(Set.of("Database"), antlrExpectedTokens);
    }

    @Test
    public void testMappingLegendReference()
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

        codeFiles.put("vscodelsp::test::EmployeeDatabase",
                "###Relational\n" +
                        "Database vscodelsp::test::EmployeeDatabase\n" +
                        "(\n" +
                        "   Table EmployeeTable(id INT PRIMARY KEY, hireDate DATE, hireType VARCHAR(10), fteFactor DOUBLE)\n" +
                        "   Table EmployeeDetailsTable(id INT PRIMARY KEY, birthDate DATE, yearsOfExperience DOUBLE)\n" +
                        "   Table FirmTable(firmName VARCHAR(100) PRIMARY KEY, employeeId INT PRIMARY KEY)\n" +
                        "\n" +
                        "   Join JoinEmployeeToFirm(EmployeeTable.id = FirmTable.employeeId)\n" +
                        "   Join JoinEmployeeToemployeeDetails(EmployeeTable.id = EmployeeDetailsTable.id)\n" +
                        "   Filter EmployeeFilter(EmployeeTable.hireType != sqlNull())\n" +
                        ")");

        codeFiles.put("vscodelsp::test::EmployeeMapping",
                "###Mapping\n" +
                        "Mapping vscodelsp::test::EmployeeMapping\n" +
                        "(\n" +
                        "   vscodelsp::test::Employee[emp] : Relational\n" +
                        "   {\n" +
                        "      ~mainTable [vscodelsp::test::EmployeeDatabase]EmployeeTable\n" +
                        "      hireDate : [vscodelsp::test::EmployeeDatabase]EmployeeTable.hireDate,\n" +
                        "      hireType : [vscodelsp::test::EmployeeDatabase]EmployeeTable.hireType\n" +
                        "   }\n" +
                        ")");


        LegendReference mainTableReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::EmployeeMapping",5,  52, 5, 64))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::EmployeeDatabase", 3, 3, 3, 96))
                .build();

        testReferenceLookup(codeFiles, "vscodelsp::test::EmployeeMapping", TextPosition.newPosition(5, 60), mainTableReference, "main table reference");

        LegendReference columnReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource("vscodelsp::test::EmployeeMapping",6,   17, 6, 73))
                .withDeclarationLocation(TextLocation.newTextSource("vscodelsp::test::EmployeeDatabase", 3, 43, 3, 55))
                .build();

        testReferenceLookup(codeFiles, "vscodelsp::test::EmployeeMapping", TextPosition.newPosition(6, 21), columnReference, "Property mapped reference");
    }

    @Test
    public void testLegendReferenceStoreConnectionsWithRelationalDatabaseConnection()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_RUNTIME_DOC_ID = "vscodelsp::test::H2Runtime";
        final String TEST_MAPPING_DOC_ID = "vscodelsp::test::EmployeeMapping";
        final String TEST_STORE_DOC_ID = "vscodelsp::test::TestDB1";
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

        codeFiles.put(TEST_MAPPING_DOC_ID,
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

        codeFiles.put(TEST_STORE_DOC_ID,
                "###Relational\n" +
                "Database vscodelsp::test::TestDB1\n" +
                "(\n" +
                "   Table PersonTable\n" +
                "   (\n" +
                "       id INTEGER PRIMARY KEY,\n" +
                "       firm_id INTEGER,\n" +
                "       firstName VARCHAR(200),\n" +
                "       lastName VARCHAR(200)\n" +
                "   )\n" +
                ")");

        codeFiles.put(TEST_RUNTIME_DOC_ID,
                "###Runtime\n" +
                "Runtime vscodelsp::test::H2Runtime\n" +
                "{\n" +
                "   mappings:\n" +
                "   [\n" +
                "       vscodelsp::test::EmployeeMapping\n" +
                "   ];\n" +
                "   connections:\n" +
                "   [\n" +
                "       vscodelsp::test::TestDB1:\n" +
                "       [\n" +
                "           connection_1:\n" +
                "           #{\n" +
                "               RelationalDatabaseConnection\n" +
                "               {\n" +
                "                   store: vscodelsp::test::TestDB1;\n" +
                "                   type: H2;\n" +
                "                   specification: LocalH2\n" +
                "                   {\n" +
                "                       testDataSetupSqls: [\n" +
                "                           'Drop table if exists FirmTable;\\nDrop table if exists PersonTable;\\nCreate Table FirmTable(id INT, Type VARCHAR(200), Legal_Name VARCHAR(200));\\nCreate Table PersonTable(id INT, firm_id INT, lastName VARCHAR(200), firstName VARCHAR(200));\\nInsert into FirmTable (id, Type, Legal_Name) values (1,\\'LLC\\',\\'FirmA\\');\\nInsert into FirmTable (id, Type, Legal_Name) values (2,\\'CORP\\',\\'Apple\\');\\nInsert into PersonTable (id, firm_id, lastName, firstName) values (1, 1, \\'John\\', \\'Doe\\');\\n\\n\\n'\n" +
                "                           ];\n" +
                "                   };\n" +
                "                   auth: DefaultH2;\n" +
                "               }\n" +
                "           }#\n" +
                "       ]\n" +
                "   ];\n" +
                "}");

        LegendReference mappedMappingReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 5, 7, 5, 38))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID, 1, 0, 9, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(4, 2), null, "Outside of mappedMappingReference-able element should yield nothing");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 6), null, "Outside of mappedMappingReference-able element (before mapping name) should yield nothing");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 7), mappedMappingReference, "Start of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 25), mappedMappingReference, "Within the mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(5, 38), mappedMappingReference, "End of mapping name has been mapped, referring to mapping definition");
        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(6, 3), null, "Outside of mappedMappingReference-able element should yield nothing");

        LegendReference mappedStoreReference1 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 9, 7, 9, 30))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_STORE_DOC_ID, 1, 0, 10, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(9, 20), mappedStoreReference1, "Within the store name has been mapped, referring to store definition");

        LegendReference mappedStoreReference2 = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_RUNTIME_DOC_ID, 15, 26, 15, 49))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_STORE_DOC_ID, 1, 0, 10, 0))
                .build();

        testReferenceLookup(codeFiles, TEST_RUNTIME_DOC_ID, TextPosition.newPosition(15, 31), mappedStoreReference2, "Within the store name has been mapped, referring to store definition");
    }

    @Test
    @Disabled("Enable once m3 source information is fixed")
    public void testLegendReferenceForRelationalAssociationMapping()
    {
        MutableMap<String, String> codeFiles = Maps.mutable.empty();
        final String TEST_MAPPING_DOC_ID = "vscodelsp::test::TestIncludeMapping";
        final String TEST_ASSOCIATION_DOC_ID = "vscodelsp::test::TestAssociation";
        codeFiles.put("vscodelsp::test::Person",
                "###Pure\n" +
                        "Class vscodelsp::test::Person\n" +
                        "{\n" +
                        "    name: String[1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::Firm",
                "###Pure\n" +
                        "Class vscodelsp::test::Firm\n" +
                        "{\n" +
                        "    name: String[1];\n" +
                        "}");

        codeFiles.put(TEST_ASSOCIATION_DOC_ID,
                "###Pure\n" +
                        "Association vscodelsp::test::TestAssociation\n" +
                        "{\n" +
                        "   firmPersonPerson : vscodelsp::test::Person[1];\n" +
                        "   firmPersonFirm : vscodelsp::test::Firm[1];\n" +
                        "}");

        codeFiles.put("vscodelsp::test::TestDB",
                "###Relational\n" +
                        "Database vscodelsp::test::TestDB\n" +
                        "(\n" +
                        "   Table People\n" +
                        "   (\n" +
                        "       id INTEGER PRIMARY KEY,\n" +
                        "       firm_id INTEGER,\n" +
                        "       name VARCHAR(200)\n" +
                        "   )\n" +
                        "   Table Firms\n" +
                        "   (\n" +
                        "       id INTEGER PRIMARY KEY,\n" +
                        "       name VARCHAR(200)\n" +
                        "   )\n" +
                        "\n" +
                        "   Join FirmPerson(People.firm_id = Firms.id)\n" +
                        ")");

        codeFiles.put(TEST_MAPPING_DOC_ID,
                "###Mapping\n" +
                        "Mapping vscodelsp::test::TestIncludeMapping\n" +
                        "(\n" +
                        "   vscodelsp::test::Person[vscodelsp_test_Person]: Relational\n" +
                        "   {\n" +
                        "       ~primaryKey\n" +
                        "       (\n" +
                        "           [vscodelsp::test::TestDB]People.id\n" +
                        "       )\n" +
                        "       ~mainTable [vscodelsp::test::TestDB]People\n" +
                        "       name: [vscodelsp::test::TestDB]People.name\n" +
                        "   }\n" +
                        "   vscodelsp::test::Firm[vscodelsp_test_Firm]: Relational\n" +
                        "   {\n" +
                        "       ~primaryKey\n" +
                        "       (\n" +
                        "           [vscodelsp::test::TestDB]Firms.id\n" +
                        "       )\n" +
                        "       ~mainTable [vscodelsp::test::TestDB]Firms\n" +
                        "       name: [vscodelsp::test::TestDB]Firms.name\n" +
                        "   }\n" +
                        "   vscodelsp::test::TestAssociation : Relational\n" +
                        "   {\n" +
                        "       AssociationMapping\n" +
                        "       (\n" +
                        "           firmPersonPerson[vscodelsp_test_Firm,vscodelsp_test_Person] : [vscodelsp::test::TestDB]@FirmPerson,\n" +
                        "           firmPersonFirm[vscodelsp_test_Person,vscodelsp_test_Firm] : [vscodelsp::test::TestDB]@FirmPerson\n" +
                        "       )\n" +
                        "   }\n" +
                        ")");

        LegendReference mappedAssociationReference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(TEST_MAPPING_DOC_ID, 25, 11, 25, 26))
                .withDeclarationLocation(TextLocation.newTextSource(TEST_ASSOCIATION_DOC_ID, 3, 3, 3, 48))
                .build();

        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID, TextPosition.newPosition(24, 2), null, "Outside of mappedAssociationReference-able element should yield nothing");
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID, TextPosition.newPosition(25, 10), null, "Outside of mappedAssociationReference-able element (before association name) should yield nothing");
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID, TextPosition.newPosition(25, 11), mappedAssociationReference, "Start of association name has been mapped, referring to association definition");
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID, TextPosition.newPosition(25, 20), mappedAssociationReference, "Within the association name has been mapped, referring to association definition");
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID, TextPosition.newPosition(25, 26), mappedAssociationReference, "End of association name has been mapped, referring to association definition");
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID, TextPosition.newPosition(25, 27), null, "Outside of mappedAssociationReference-able element should yield nothing");
        testReferenceLookup(codeFiles, TEST_MAPPING_DOC_ID, TextPosition.newPosition(26, 3), null, "Outside of mappedAssociationReference-able element should yield nothing");
    }
}
