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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.finos.legend.engine.ide.lsp.extension.PlanExecutorConfigurator;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.plan.execution.stores.relational.config.RelationalExecutionConfiguration;
import org.finos.legend.engine.plan.execution.stores.relational.plugin.Relational;
import org.finos.legend.engine.plan.execution.stores.relational.plugin.RelationalStoreExecutor;

import java.util.Map;

public class RelationalStoreExecutorConfigurator implements PlanExecutorConfigurator
{
    @Override
    public String description()
    {
        return "Relational Store Executor Configurator";
    }

    @Override
    public PlanExecutor.Builder configure(PlanExecutor.Builder builder, Map<String, Object> config)
    {
        Object relationalExecutionConfigurationObject = config.get("relationalExecutionConfiguration");
        if (relationalExecutionConfigurationObject == null)
        {
            return builder;
        }
        RelationalExecutionConfiguration relationalExecutionConfiguration = new ObjectMapper().convertValue(relationalExecutionConfigurationObject, RelationalExecutionConfiguration.class);
        RelationalStoreExecutor relationalStoreExecutor = (RelationalStoreExecutor) Relational.build(relationalExecutionConfiguration);
        return builder.withStoreExecutors(relationalStoreExecutor);
    }
}
