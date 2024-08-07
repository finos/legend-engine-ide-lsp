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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

public class TestDefaultExtensions
{
    @Test
    public void testGetDefaultExtensions()
    {
        MutableList<LegendLSPGrammarExtension> extensions = Lists.mutable.withAll(ServiceLoader.load(LegendLSPGrammarExtension.class));
        Assertions.assertEquals(
                Sets.mutable.with("Pure", "Mapping", "Service", "Runtime", "Relational", "Connection", "Snowflake", "HostedService", "DataSpace"),
                extensions.collect(LegendLSPExtension::getName, Sets.mutable.empty())
        );
    }

    @Test
    void testGetFeatures()
    {
        MutableList<LegendLSPFeature> extensions = Lists.mutable.withAll(ServiceLoader.load(LegendLSPFeature.class));
        Assertions.assertEquals(
                Sets.mutable.with("Handles Legend SDLC features", "Legend TDS Request Handler", "Legend REPL", "SDLC Features", "Relational Store Executor Configurator"),
                extensions.collect(LegendLSPFeature::description, Sets.mutable.empty())
        );
    }
}
