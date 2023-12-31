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

import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParserContext;
import org.finos.legend.engine.language.pure.grammar.from.SectionSourceCode;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtension;
import org.finos.legend.engine.language.pure.grammar.from.extension.SectionParser;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;

import java.util.function.Consumer;

abstract class AbstractSectionParserLSPGrammarExtension extends AbstractLSPGrammarExtension
{
    protected final SectionParser parser;

    protected AbstractSectionParserLSPGrammarExtension(SectionParser parser)
    {
        this.parser = parser;
    }

    protected AbstractSectionParserLSPGrammarExtension(String parserName, PureGrammarParserExtension extension)
    {
        this(findSectionParser(parserName, extension));
    }

    @Override
    public String getName()
    {
        return this.parser.getSectionTypeName();
    }

    @Override
    protected void parse(SectionSourceCode section, Consumer<PackageableElement> elementConsumer, PureGrammarParserContext parserContext)
    {
        this.parser.parse(section, elementConsumer, parserContext);
    }

    private static SectionParser findSectionParser(String name, PureGrammarParserExtension extension)
    {
        SectionParser parser = Iterate.detect(extension.getExtraSectionParsers(), p -> name.equals(p.getSectionTypeName()));
        if (parser == null)
        {
            throw new RuntimeException("Cannot find parser: " + name);
        }
        return parser;
    }
}
