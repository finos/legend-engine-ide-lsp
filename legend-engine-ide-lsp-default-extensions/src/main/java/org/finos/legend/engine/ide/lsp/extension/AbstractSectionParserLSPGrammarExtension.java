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

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.grammar.from.ParseTreeWalkerSourceInformation;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParserContext;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParserUtility;
import org.finos.legend.engine.language.pure.grammar.from.SectionSourceCode;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtension;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.language.pure.grammar.from.extension.SectionParser;
import org.finos.legend.engine.protocol.pure.m3.PackageableElement;
import org.finos.legend.engine.protocol.pure.m3.SourceInformation;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;

public abstract class AbstractSectionParserLSPGrammarExtension extends AbstractLSPGrammarExtension
{
    protected final SectionParser parser;
    private final Set<String> grammarSupportedTypes;

    protected AbstractSectionParserLSPGrammarExtension(SectionParser parser)
    {
        this.parser = parser;
        this.grammarSupportedTypes = this.getAntlrExpectedTokens();
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

    /**
     * The default implementation set the types supported by the grammar as keywords
     *
     * @return Keywords on this section parser
     */
    @Override
    public Iterable<? extends String> getKeywords()
    {
        return this.grammarSupportedTypes;
    }

    /**
     * Implementation that defaults completion suggestions to grammar supported types
     *
     * @param section  grammar section state where completion triggered
     * @param location location where completion triggered
     * @return Completion suggestion contextual to section and location
     */
    @Override
    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        return this.computeCompletionsForSupportedTypes(section, location, this.grammarSupportedTypes);
    }

    public Set<String> getAntlrExpectedTokens()
    {
        Set<String> expectedTokens = Collections.emptySet();

        try
        {
            this.parse(
                    new SectionSourceCode(
                            "~bad~code~bad~code~",
                            this.parser.getSectionTypeName(),
                            SourceInformation.getUnknownSourceInformation(),
                            new ParseTreeWalkerSourceInformation.Builder("memory", 0, 0).build()
                    ),
                    x ->
                    {
                    },
                    new PureGrammarParserContext(PureGrammarParserExtensions.fromAvailableExtensions())
            );
        }
        catch (EngineException e)
        {
            if (e.getCause() instanceof RecognitionException)
            {
                RecognitionException re = (RecognitionException) e.getCause();
                expectedTokens = getExpectedTokens(re);

            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return expectedTokens;
    }

    private Set<String> getExpectedTokens(RecognitionException re)
    {
        IntervalSet expectedTokensIds = re.getExpectedTokens();
        Optional<Vocabulary> vocabulary = Optional.ofNullable(re.getRecognizer())
                .map(Recognizer::getVocabulary);
        return Optional.ofNullable(expectedTokensIds)
                .map(IntervalSet::toList)
                .<Set<String>>flatMap(x -> vocabulary.map(v -> ListIterate
                        .collect(x, v::getLiteralName)
                        .select(Objects::nonNull)
                        .collect(t -> PureGrammarParserUtility.fromGrammarString(t, true))
                        .toSet()
                ))
                .orElse(Collections.emptySet());
    }
}
