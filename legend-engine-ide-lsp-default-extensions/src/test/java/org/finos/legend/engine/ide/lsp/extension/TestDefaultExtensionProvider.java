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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TestDefaultExtensionProvider
{
    @Test
    public void testGetDefaultExtensions()
    {
        List<LegendLSPGrammarExtension> extensions = DefaultExtensionProvider.getDefaultExtensions();
        Assertions.assertEquals(
                Set.of("Pure", "Mapping", "Service", "Runtime", "Relational"),
                extensions.stream().map(LegendLSPExtension::getName).collect(Collectors.toSet())
        );

        // verify that the list is unmodifiable
        Assertions.assertThrows(UnsupportedOperationException.class, () -> extensions.add(() -> "new grammar"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> extensions.remove(0));
        Assertions.assertThrows(UnsupportedOperationException.class, extensions::clear);
    }
}
