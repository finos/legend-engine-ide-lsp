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

package org.finos.legend.engine.ide.lsp.extension.repl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

public class TestLegendFile
{
    @Test
    public void testLegendFile()
    {
        Path filePath = Path.of("src/test/resources/entities/vscodelsp/test/dependency/SourceModelTestGrammar.pure");
        FileTime fileTime = FileTime.from(Instant.now());
        LegendFile legendFile = new LegendFile(filePath, fileTime);
        String fileContent = legendFile.getFileContent();
        if (!System.lineSeparator().equals("\n"))
        {
            fileContent = fileContent.replaceAll(System.lineSeparator(), "\n");
        }
        Assertions.assertEquals("\n//Start of models sourced from " + filePath + "\n" +
                "###Pure\n" +
                "###Pure\n" +
                "Class model::Person\n" +
                "{\n" +
                "  firstName: String[1];\n" +
                "  lastName: String[1];\n" +
                "}\n" +
                "\n" +
                "Enum model::EmployeeType\n" +
                "{\n" +
                "    CONTRACT,\n" +
                "    FULL_TIME\n" +
                "}\n" +
                "\n" +
                "Class model::Firm\n" +
                "{\n" +
                "  legalName: String[1];\n" +
                "}\n" +
                "\n" +
                "Association model::Person_Firm\n" +
                "{\n" +
                "  employees: model::Person[*];\n" +
                "  firm: model::Firm[1];\n" +
                "}\n" +
                "//End of models sourced from " + filePath + "\n", fileContent);
    }

    @Test
    public void testInvalidLegendFile()
    {
        try
        {
            new LegendFile(Path.of("src/test/resources/entities/vscodelsp/test/dependency/wrongPath.txt"), FileTime.from(Instant.now()));
        }
        catch (RuntimeException e)
        {
            Assertions.assertEquals("Valid filePath is required", e.getMessage());
        }
    }
}
