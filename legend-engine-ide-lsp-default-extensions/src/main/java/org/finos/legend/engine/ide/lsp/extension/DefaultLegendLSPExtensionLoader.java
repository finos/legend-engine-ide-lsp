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

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtension;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensionLoader;
import org.finos.legend.engine.language.pure.grammar.from.extension.SectionParser;

/**
 * Loads extensions first from classpath, discovering explicitly defined extensions,
 * then collects all available grammar parser on classpath, and for those
 * parser that an explicit extension does not exist, a catch-all extension is created.
 * This will ensure we can at minimum parse any grammar the project define.
 */
public class DefaultLegendLSPExtensionLoader implements LegendLSPExtensionLoader
{
    @Override
    public Iterable<LegendLSPGrammarExtension> loadLegendLSPGrammarExtension(ClassLoader classLoader)
    {
        ServiceLoader<LegendLSPGrammarExtension> extensions = ServiceLoader.load(LegendLSPGrammarExtension.class, classLoader);

        List<LegendLSPGrammarExtension> extensionList = new ArrayList<>();

        extensions.forEach(extensionList::add);

        Set<String> grammars = extensionList.stream().map(LegendLSPExtension::getName).collect(Collectors.toSet());

        PureGrammarParserExtensionLoader.extensions().stream()
                .map(PureGrammarParserExtension::getExtraSectionParsers)
                .map(Iterable::spliterator)
                .flatMap(x -> StreamSupport.stream(x, false))
                .filter(x -> !grammars.contains(x.getSectionTypeName()))
                .map(CatchAllSectionParserLSPGrammarExtension::new)
                .forEach(extensionList::add);

        return extensionList;
    }

    @Override
    public Iterable<LegendLSPInlineDSLExtension> loadLegendLSPInlineDSLExtension(ClassLoader classLoader)
    {
        return ServiceLoader.load(LegendLSPInlineDSLExtension.class, classLoader);
    }

    static class CatchAllSectionParserLSPGrammarExtension extends AbstractSectionParserLSPGrammarExtension
    {
        public CatchAllSectionParserLSPGrammarExtension(SectionParser parser)
        {
            super(parser);
        }
    }
}
