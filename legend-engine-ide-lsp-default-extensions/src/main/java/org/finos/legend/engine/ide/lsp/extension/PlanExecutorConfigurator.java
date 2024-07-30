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

package org.finos.legend.engine.ide.lsp.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.engine.plan.execution.PlanExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface PlanExecutorConfigurator extends LegendLSPFeature
{
    PlanExecutor.Builder configure(PlanExecutor.Builder builder, Map<String, Object> config);

    static PlanExecutor create(Path planExecutorConfigurationJsonPath, List<LegendLSPFeature> features)
    {
        Map<String, Object> planExecutorConfiguration;
        try
        {
            planExecutorConfiguration = planExecutorConfigurationJsonPath == null
                    ? Maps.mutable.empty()
                    : new ObjectMapper().readValue(new String(Files.readAllBytes(planExecutorConfigurationJsonPath)), new TypeReference<>(){});
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        PlanExecutor.Builder builder = PlanExecutor.newPlanExecutorBuilder();
        for (LegendLSPFeature feature : features)
        {
            if (feature instanceof PlanExecutorConfigurator)
            {
                builder = ((PlanExecutorConfigurator) feature).configure(builder, planExecutorConfiguration);
            }
        }

        return builder.withAvailableStoreExecutors().build();
    }
}
