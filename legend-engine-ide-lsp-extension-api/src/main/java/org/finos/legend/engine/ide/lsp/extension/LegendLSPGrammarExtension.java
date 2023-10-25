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

import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;

import java.util.Collections;

/**
 * An LSP extension representing a Legend Engine top level grammar.
 */
public interface LegendLSPGrammarExtension extends LegendLSPExtension
{
    /**
     * Return the Legend declarations for the given section.
     *
     * @param section grammar section
     * @return Legend declarations
     */
    default Iterable<? extends LegendDeclaration> getDeclarations(GrammarSection section)
    {
        return Collections.emptyList();
    }

    default Exception getParsingErrors(GrammarSection section)
    {
        return Collections.emptyList();
    }
}
