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
import org.eclipse.collections.api.list.ImmutableList;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.language.pure.grammar.from.runtime.RuntimeParser;

import java.util.List;

/**
 * Extension for the Runtime grammar.
 */
public class RuntimeLSPGrammarExtension extends AbstractLegacyParserLSPGrammarExtension
{
    private static final List<String> KEYWORDS = List.of("Runtime", "import", "mappings", "connections");

    private static final ImmutableList<String> BOILERPLATE_SUGGESTIONS = Lists.immutable.with(
            "Runtime package::path::runtimeName\n" +
                "{\n" +
                "  mappings:\n" +
                "  [\n" +
                "    package::path::mapping1,\n" +
                "    package::path::mapping2\n" +
                "  ];\n" +
                "  connections:\n" +
                "  [\n" +
                "    package::path::store1:\n" +
                "    [\n" +
                "    connection_1: package::path::connection1\n" +
                "    ]\n" +
                "  ];\n" +
                "}\n"
    );

    public RuntimeLSPGrammarExtension()
    {
        super(RuntimeParser.newInstance(PureGrammarParserExtensions.fromAvailableExtensions()));
    }

    @Override
    public Iterable<? extends String> getKeywords()
    {
        return KEYWORDS;
    }

    @Override
    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        String codeLine = section.getSection().getLine(location.getLine()).substring(0, location.getColumn());

        if (codeLine.isEmpty())
        {
            return BOILERPLATE_SUGGESTIONS.collect(s -> new LegendCompletion("Runtime boilerplate", s.replaceAll("\n",System.lineSeparator())));
        }

        return List.of();
    }
}
