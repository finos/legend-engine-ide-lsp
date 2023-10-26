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
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.language.pure.grammar.from.ParseTreeWalkerSourceInformation;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParserContext;
import org.finos.legend.engine.language.pure.grammar.from.SectionSourceCode;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.protocol.pure.v1.model.SourceInformation;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

abstract class AbstractLSPGrammarExtension implements LegendLSPGrammarExtension
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLSPGrammarExtension.class);

    @Override
    public Iterable<? extends LegendDeclaration> getDeclarations(GrammarSection section)
    {
        if (!getName().equals(section.getGrammar()))
        {
            LOGGER.warn("Cannot handle grammar {} in extension {}", section.getGrammar(), getName());
            return Collections.emptyList();
        }

        PureGrammarParserContext parserContext = new PureGrammarParserContext(PureGrammarParserExtensions.fromAvailableExtensions());
        MutableList<LegendDeclaration> declarations = Lists.mutable.empty();
        parse(toSectionSourceCode(section), element ->
        {
            LegendDeclaration declaration = getDeclaration(element);
            if (declaration != null)
            {
                declarations.add(declaration);
            }
        }, parserContext);
        return declarations;
    }

    protected abstract void parse(SectionSourceCode section, Consumer<PackageableElement> elementConsumer, PureGrammarParserContext pureGrammarParserContext);

    protected LegendDeclaration getDeclaration(PackageableElement element)
    {
        String path = element.getPath();
        if (invalidSourceInfo(element.sourceInformation))
        {
            LOGGER.warn("Invalid source information for {}", path);
            return null;
        }

        String classifier = getClassifier(element);
        if (classifier == null)
        {
            LOGGER.warn("No classifier for {}", path);
            return null;
        }

        LegendDeclaration.Builder builder = LegendDeclaration.builder()
                .withIdentifier(path)
                .withClassifier(classifier)
                .withLocation(toLocation(element.sourceInformation));
        forEachChild(element, child ->
        {
            if (child != null)
            {
                builder.withChild(child);
            }
        });
        return builder.build();
    }

    protected abstract String getClassifier(PackageableElement element);

    protected void forEachChild(PackageableElement element, Consumer<LegendDeclaration> consumer)
    {
        // Do nothing by default
    }

    private SectionSourceCode toSectionSourceCode(GrammarSection section)
    {
        String sourceId = "section_" + section.getGrammar() + ".pure";
        SourceInformation sectionSourceInfo = new SourceInformation(sourceId, section.getStartLine(), 0, section.getEndLine(), section.getLineLength(section.getEndLine()));
        ParseTreeWalkerSourceInformation walkerSourceInfo = new ParseTreeWalkerSourceInformation.Builder(sourceId, section.getStartLine(), 0).build();
        return new SectionSourceCode(section.getText(true), section.getGrammar(), sectionSourceInfo, walkerSourceInfo);
    }

    /**
     * Check if a {@link SourceInformation} is invalid.
     *
     * @param sourceInfo source information
     * @return whether source information is invalid
     */
    protected static boolean invalidSourceInfo(SourceInformation sourceInfo)
    {
        return (sourceInfo == null) ||
                (sourceInfo.startLine < 0) ||
                (sourceInfo.startColumn < 1) ||
                (sourceInfo.endLine < sourceInfo.startLine) ||
                (sourceInfo.endColumn < ((sourceInfo.endLine == sourceInfo.startLine) ? sourceInfo.startColumn : 0));
    }

    /**
     * Transform a (valid) {@link SourceInformation} to a {@link TextInterval} location.
     *
     * @param sourceInfo source information
     * @return location
     */
    protected static TextInterval toLocation(SourceInformation sourceInfo)
    {
        return TextInterval.newInterval(sourceInfo.startLine, sourceInfo.startColumn - 1, sourceInfo.endLine, sourceInfo.endColumn - 1);
    }

    //@Override
    public Iterable<? extends LegendDiagnostic> getDiagnostics(GrammarSection section)
    {
        try
        {
            parse(toSectionSourceCode(section), e ->
            { }, new PureGrammarParserContext(PureGrammarParserExtensions.fromAvailableExtensions()));
            return null;
        }
        catch (Exception e)
        {
            String message = e.getMessage();
            SourceInformation sourceInformation = ((EngineException) e).getSourceInformation();
            TextInterval textInterval = TextInterval.newInterval(sourceInformation.startLine, sourceInformation.startColumn - 1, sourceInformation.endLine, sourceInformation.endColumn);
            DiagnosticSeverity severity = DiagnosticSeverity.Error;
            LegendDiagnostic.Type type = LegendDiagnostic.Type.Parser;

            LegendDiagnostic diagnostic = new LegendDiagnostic(textInterval, message, severity, type);
            return Set.of(diagnostic);
        }
    }
}