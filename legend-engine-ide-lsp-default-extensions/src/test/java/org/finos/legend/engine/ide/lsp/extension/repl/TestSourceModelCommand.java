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

import org.eclipse.collections.impl.factory.Lists;
import org.finos.legend.engine.ide.lsp.extension.PlanExecutorConfigurator;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.repl.autocomplete.CompleterExtension;
import org.finos.legend.engine.repl.client.Client;
import org.finos.legend.engine.repl.relational.autocomplete.RelationalCompleterExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestSourceModelCommand
{
    @Test
    public void testSourceModel()
    {
        try
        {
            PlanExecutor planExecutor = PlanExecutorConfigurator.create(null, Lists.fixedSize.empty());
            Client client = new Client(Lists.mutable.with(new LSPReplExtension()), Lists.mutable.with(new CompleterExtension[]{new RelationalCompleterExtension()}), planExecutor);
            SourceModelCommand sourceModelCommand = new SourceModelCommand(client);
            Assertions.assertTrue(sourceModelCommand.process("sourceModel src/test/resources/entities/vscodelsp/test/dependency/SourceModelTestGrammar.pure"));
            Assertions.assertEquals("###Pure\n" +
                    "//Sourced from src/test/resources/entities/vscodelsp/test/dependency/SourceModelTestGrammar.pure\n" +
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
                    "//End of models sourced from src/test/resources/entities/vscodelsp/test/dependency/SourceModelTestGrammar.pure\n", client.getModelState().getText());
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
