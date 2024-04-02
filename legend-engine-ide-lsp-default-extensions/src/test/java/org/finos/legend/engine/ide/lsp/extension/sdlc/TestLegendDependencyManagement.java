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

        String expectedContent = "// READ ONLY (sourced from workspace dependencies)\n\n" +
                "###Pure\n" +
                "Class vscodelsp::test::dependency::Employee\n" +
                "{\n" +
                "  foobar1: Float[1];\n" +
                "  foobar2: Float[1];\n" +
                "}\n" +
                "\n" +
                "###Pure\n" +
                "Class vscodelsp::test::dependency::Employee2\n" +
                "{\n" +
                "  foobar1: Float[1];\n" +
                "  foobar2: Float[1];\n" +
                "}\n" +
                "\n" +
                "###Mapping\n" +
                "Mapping vscodelsp::test::dependency::Mapping\n" +
                "(\n" +
                "  model::domain::TargetClass1[tc1]: Pure\n" +
                "  {\n" +
                "    ~src model::domain::SourceClass1\n" +
                "    id: $src.id,\n" +
                "    type: EnumerationMapping TestEnumerationMappingInt: $src.type,\n" +
                "    otherType: EnumerationMapping TestEnumerationMappingString: $src.otherType,\n" +
                "    other[tc2]: $src.other\n" +
                "  }\n" +
                ")\n" +
                "\n" +
                "/* Failed to load grammar for dependency element: vscodelsp::test::dependency::Unparsable\n" +
                "java.lang.NullPointerException: Cannot read field \"lowerBound\" because \"multiplicity\" is null";

        Assertions.assertEquals(1, files.size());
        Assertions.assertEquals(Path.of("dependencies.pure"), files.get(0).getPath());
        Assertions.assertTrue(files.get(0).getContent().startsWith(expectedContent),
                () -> expectedContent + "\n\n!=\n\n" + files.get(0).getContent()
        );
    }
}
