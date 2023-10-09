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

public class TestLegendLSPInlineDSLLibrary extends AbstractTestLegendLSPExtensionLibrary<LegendLSPInlineDSLExtension, LegendLSPInlineDSLLibrary>
{
    @Test
    public void testNonEmpty()
    {
        super.testNonEmpty();
        Assertions.assertEquals(this.library.getInlineDSLs(), this.library.getExtensionNames());
    }

    @Override
    protected LegendLSPExtensionLibrary.AbstractBuilder<LegendLSPInlineDSLExtension, LegendLSPInlineDSLLibrary> libraryBuilder()
    {
        return LegendLSPInlineDSLLibrary.builder();
    }

    @Override
    protected LegendLSPInlineDSLExtension newExtension(String name)
    {
        return () -> name;
    }
}
