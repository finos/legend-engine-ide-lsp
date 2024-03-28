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

import java.nio.file.Path;
import java.util.List;
import org.finos.legend.engine.ide.lsp.extension.features.LegendVirtualFileSystemContentInitializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestLegendDependencyManagement
{
    @Test
    void testDependenciesDiscovered()
    {
        LegendDependencyManagement legendDependencyManagement = new LegendDependencyManagement();
        List<LegendVirtualFileSystemContentInitializer.LegendVirtualFile> files = legendDependencyManagement.getVirtualFilePureGrammars();

        LegendVirtualFileSystemContentInitializer.LegendVirtualFile expected = LegendVirtualFileSystemContentInitializer.newVirtualFile(Path.of("dependencies.pure"),
                "// READ ONLY (sourced from workspace dependencies)\n\n" +
                "Class vscodelsp::test::dependency::Employee\n" +
                "{\n" +
                "  foobar1: Float[1];\n" +
                "  foobar2: Float[1];\n" +
                "}\n");

        Assertions.assertEquals(List.of(expected), files);
    }
}
