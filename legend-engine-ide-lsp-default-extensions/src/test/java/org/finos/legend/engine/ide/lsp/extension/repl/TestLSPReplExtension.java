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

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.repl.client.Client;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
public class TestLSPReplExtension
{
    @Test
    public void testInitialize(@TempDir Path dir) throws Exception
    {
        Path filePath = dir.resolve("SourceModelTestGrammar.pure");
        try (InputStream is = Objects.requireNonNull(TestLSPReplExtension.class.getResourceAsStream("/entities/vscodelsp/test/dependency/SourceModelTestGrammar.pure"));
             OutputStream os = Files.newOutputStream(filePath, StandardOpenOption.CREATE)
        )
        {
            is.transferTo(os);
        }

        LSPReplExtension lspReplExtension = new LSPReplExtension(Lists.fixedSize.of(dir.toString()));
        lspReplExtension.initialize(new Client(Lists.fixedSize.empty(), Lists.fixedSize.empty(), PlanExecutor.newPlanExecutorBuilder().build()));
        MutableList<String> fileContent = lspReplExtension.generateDynamicContent("");
        Assertions.assertEquals(1, fileContent.size());
        String actualFileContent = fileContent.get(0);
        if (!System.lineSeparator().equals("\n"))
        {
            actualFileContent = actualFileContent.replaceAll(System.lineSeparator(), "\n");
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
                "//End of models sourced from " + filePath + "\n", actualFileContent);
    }
}
