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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.collections.api.factory.Maps;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.plan.execution.stores.StoreExecutorBuilderLoader;
import org.finos.legend.engine.plan.execution.stores.StoreExecutorConfiguration;
import org.finos.legend.engine.plan.execution.stores.StoreType;
import org.finos.legend.engine.shared.core.operational.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface PlanExecutorConfigurator extends LegendLSPFeature
{
    Logger LOGGER = LoggerFactory.getLogger(PlanExecutorConfigurator.class);

    StoreExecutorConfiguration configure(Map<String, Object> config);

    static PlanExecutor create(Path planExecutorConfigurationJsonPath, List<LegendLSPFeature> features)
    {
        Map<String, Object> planExecutorConfiguration = Maps.mutable.empty();
        if (planExecutorConfigurationJsonPath != null)
        {
            try (InputStream is = Files.newInputStream(planExecutorConfigurationJsonPath))
            {
                planExecutorConfiguration = new ObjectMapper().readValue(is, Map.class);
            }
            catch (IOException e)
            {
                LOGGER.error("Unable to parse JSON content on config file: {}", planExecutorConfigurationJsonPath, e);
            }
        }

        Map<StoreType, StoreExecutorConfiguration> storeTypeStoreExecutorConfigurationMap = Maps.mutable.empty();

        for (LegendLSPFeature feature : features)
        {
            if (feature instanceof PlanExecutorConfigurator)
            {
                StoreExecutorConfiguration storeExecutorConfiguration = ((PlanExecutorConfigurator) feature).configure(planExecutorConfiguration);
                if (storeExecutorConfiguration != null)
                {
                    StoreType storeType = storeExecutorConfiguration.getStoreType();
                    StoreExecutorConfiguration existing = storeTypeStoreExecutorConfigurationMap.put(storeType, storeExecutorConfiguration);
                    Assert.assertTrue(existing == null, () -> "Found multiple configurations for store type: " + storeType);
                }
            }
        }

        PlanExecutor.Builder builder = PlanExecutor.newPlanExecutorBuilder();

        StoreExecutorBuilderLoader.extensions().stream().map(s ->
        {
            StoreExecutorConfiguration storeExecutorConfiguration = storeTypeStoreExecutorConfigurationMap.remove(s.getStoreType());
            if (storeExecutorConfiguration == null)
            {
                return s.build();
            }
            else
            {
                return s.build(storeExecutorConfiguration);
            }
        }).forEach(builder::withStoreExecutors);

        Assert.assertTrue(storeTypeStoreExecutorConfigurationMap.isEmpty(), () -> "Collected store configuration with no store executor builder: " + storeTypeStoreExecutorConfigurationMap.keySet());

        return builder.build();
    }
}
