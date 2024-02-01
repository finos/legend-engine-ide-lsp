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

package org.finos.legend.engine.ide.lsp.extension.sdlc;

import org.finos.legend.engine.ide.lsp.extension.LegendDependencyManagement;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.sdlc.protocol.pure.v1.PureModelContextDataBuilder;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LegendDependencyManagementImpl implements LegendDependencyManagement
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendDependencyManagementImpl.class);

    private final PureModelContextData dependenciesPMCD;

    public LegendDependencyManagementImpl()
    {
        this.dependenciesPMCD = loadDependenciesPMCD();
    }

    private static PureModelContextData loadDependenciesPMCD()
    {
        LOGGER.info("Processing dependencies from classpath");
        try (EntityLoader loader = EntityLoader.newEntityLoader(LegendDependencyManagementImpl.class.getClassLoader()))
        {
            // todo | we need to reprocess the PMCD so we can have source information
            // todo | and we need to find a mechanism to display this source code when user is navigating to it

            PureModelContextDataBuilder fromClasspathBuilder = PureModelContextDataBuilder.newBuilder();
            fromClasspathBuilder.addEntitiesIfPossible(loader.getAllEntities());
            return fromClasspathBuilder.build();
        }
        catch (Exception e)
        {
            LOGGER.warn("Failed to load dependencies from classpath", e);
            return PureModelContextData.newBuilder().build();
        }
    }

    @Override
    public String description()
    {
        return "Handles Legend SDLC features";
    }

    @Override
    public PureModelContextData getDependenciesPMCD()
    {
        return this.dependenciesPMCD;
    }
}
