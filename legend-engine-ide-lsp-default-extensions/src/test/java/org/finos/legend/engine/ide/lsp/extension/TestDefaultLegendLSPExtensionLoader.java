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

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.Sets;
import org.finos.legend.engine.ide.lsp.extension.connection.ConnectionLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.core.PureLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.dataSpace.DataSpaceLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.functionActivator.HostedServiceLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.functionActivator.SnowflakeLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.mapping.MappingLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.relational.RelationalLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.runtime.RuntimeLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.service.ServiceLSPGrammarExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestDefaultLegendLSPExtensionLoader
{
    @Test
    void loadGrammarExtensions()
    {
        ClassLoader classLoader = this.getClass().getClassLoader();
        DefaultLegendLSPExtensionLoader loader = new DefaultLegendLSPExtensionLoader();

        Iterable<LegendLSPGrammarExtension> grammarExtensions = loader.loadLegendLSPGrammarExtensions(classLoader);

        Map<String, Class<? extends LegendLSPGrammarExtension>> grammarsMap = StreamSupport.stream(grammarExtensions.spliterator(), false).collect(Collectors.toMap(LegendLSPGrammarExtension::getName, LegendLSPGrammarExtension::getClass));

        Map<String, Class<? extends LegendLSPGrammarExtension>> expected = new HashMap<>();

        expected.put("Pure", PureLSPGrammarExtension.class);
        expected.put("Connection", ConnectionLSPGrammarExtension.class);
        expected.put("Mapping", MappingLSPGrammarExtension.class);
        expected.put("Relational", RelationalLSPGrammarExtension.class);
        expected.put("Runtime", RuntimeLSPGrammarExtension.class);
        expected.put("Service", ServiceLSPGrammarExtension.class);
        expected.put("Data", DefaultLegendLSPExtensionLoader.CatchAllSectionParserLSPGrammarExtension.class);
        expected.put("QueryPostProcessor", DefaultLegendLSPExtensionLoader.CatchAllSectionParserLSPGrammarExtension.class);
        expected.put("Snowflake", SnowflakeLSPGrammarExtension.class);
        expected.put("HostedService", HostedServiceLSPGrammarExtension.class);
        expected.put("DataSpace", DataSpaceLSPGrammarExtension.class);

        MutableSet<String> missingOnActual = Sets.adapt(expected.keySet()).difference(Sets.adapt(grammarsMap.keySet()));
        Assertions.assertTrue(missingOnActual.isEmpty(), "Expected but missed on actual: " + missingOnActual);

        MutableSet<String> missingOnExpected = Sets.adapt(grammarsMap.keySet()).difference(Sets.adapt(expected.keySet()));
        Assertions.assertTrue(missingOnExpected.isEmpty(), "Actual but missed on expected: " + missingOnExpected);

        Assertions.assertEquals(expected, grammarsMap);
    }
}
