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

import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.language.pure.grammar.from.mapping.MappingParser;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;

import java.util.List;

/**
 * Extension for the Mapping grammar.
 */
class MappingLSPGrammarExtension extends AbstractLegacyParserLSPGrammarExtension<MappingParser>
{
    private static final List<String> KEYWORDS = List.of("Mapping", "EnumerationMapping", "include");

    MappingLSPGrammarExtension()
    {
        super(MappingParser.newInstance(PureGrammarParserExtensions.fromAvailableExtensions()));
    }

    @Override
    public Iterable<? extends String> getKeywords()
    {
        return KEYWORDS;
    }

    @Override
    protected String getClassifier(PackageableElement element)
    {
        return (element instanceof Mapping) ? "meta::pure::mapping::Mapping" : null;
    }
}
