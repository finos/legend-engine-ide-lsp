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
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.language.pure.compiler.Compiler;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.grammar.from.ParseTreeWalkerSourceInformation;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParserContext;
import org.finos.legend.engine.language.pure.grammar.from.SectionSourceCode;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.protocol.pure.v1.model.SourceInformation;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.function.Consumer;

abstract class AbstractLSPGrammarExtension implements LegendLSPGrammarExtension
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLSPGrammarExtension.class);

    private static final String PARSE_RESULT = "parse";
    private static final String COMPILE_RESULT = "compile";

    @Override
    public void initialize(SectionState section)
    {
        getParseResult(section);
    }

    @Override
    public Iterable<? extends LegendDeclaration> getDeclarations(SectionState sectionState)
    {
        MutableList<LegendDeclaration> declarations = Lists.mutable.empty();
        getParseResult(sectionState).forEachElement(element ->
        {
            LegendDeclaration declaration = getDeclaration(element);
            if (declaration != null)
            {
                declarations.add(declaration);
            }
        });
        return declarations;
    }

    @Override
    public Iterable<? extends LegendDiagnostic> getDiagnostics(SectionState sectionState)
    {
        MutableList<LegendDiagnostic> diagnostics = Lists.mutable.empty();
        collectParserDiagnostics(sectionState, diagnostics::add);
        collectCompilerDiagnostics(sectionState, diagnostics::add);
        return diagnostics;
    }

    private void collectParserDiagnostics(SectionState sectionState, Consumer<? super LegendDiagnostic> consumer)
    {
        ParseResult parseResult = getParseResult(sectionState);
        if (parseResult.hasEngineException())
        {
            EngineException e = parseResult.getEngineException();
            SourceInformation sourceInfo = e.getSourceInformation();
            if (isValidSourceInfo(sourceInfo))
            {
                consumer.accept(LegendDiagnostic.newDiagnostic(toLocation(sourceInfo), e.getMessage(), LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Parser));
            }
            else
            {
                LOGGER.warn("Invalid source information in parser exception in section {} of {}: cannot create diagnostic", sectionState.getSectionNumber(), sectionState.getDocumentState().getDocumentId(), e);
            }
        }
    }

    private void collectCompilerDiagnostics(SectionState sectionState, Consumer<? super LegendDiagnostic> consumer)
    {
        CompileResult compileResult = getCompileResult(sectionState);
        if (compileResult.hasEngineException())
        {
            EngineException e = compileResult.getEngineException();
            SourceInformation sourceInfo = e.getSourceInformation();
            String docId = sectionState.getDocumentState().getDocumentId();
            if (!isValidSourceInfo(sourceInfo))
            {
                if ((sourceInfo != null) && docId.equals(sourceInfo.sourceId))
                {
                    LOGGER.warn("Invalid source information in compiler exception in {}: cannot create diagnostic", docId, e);
                }
            }
            else if (docId.equals(sourceInfo.sourceId))
            {
                consumer.accept(LegendDiagnostic.newDiagnostic(toLocation(sourceInfo), e.getMessage(), LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Compiler));
            }
        }
    }

    protected ParseResult getParseResult(SectionState sectionState)
    {
        return sectionState.getProperty(PARSE_RESULT, () ->
        {
            sectionState.getDocumentState().getGlobalState().removeProperty(COMPILE_RESULT);
            return tryParse(sectionState);
        });
    }

    protected ParseResult tryParse(SectionState sectionState)
    {
        MutableList<PackageableElement> elements = Lists.mutable.empty();
        try
        {
            parse(sectionState, elements::add);
            return new ParseResult(elements.toImmutable());
        }
        catch (EngineException e)
        {
            SourceInformation sourceInfo = e.getSourceInformation();
            if (!isValidSourceInfo(sourceInfo))
            {
                LOGGER.warn("Invalid source information in parsing exception in section {} of {}: {}", sectionState.getSectionNumber(), sectionState.getDocumentState().getDocumentId(), (sourceInfo == null) ? null : sourceInfo.getMessage(), e);
            }
            return new ParseResult(elements.toImmutable(), e);
        }
        catch (Exception e)
        {
            LOGGER.error("Unexpected exception when parsing section {} of {}", sectionState.getSectionNumber(), sectionState.getDocumentState().getDocumentId(), e);
            return new ParseResult(elements.toImmutable(), e);
        }
    }

    protected void parse(SectionState section, Consumer<PackageableElement> elementConsumer)
    {
        parse(section, elementConsumer, new PureGrammarParserContext(PureGrammarParserExtensions.fromAvailableExtensions()));
    }

    protected void parse(SectionState section, Consumer<PackageableElement> elementConsumer, PureGrammarParserContext pureGrammarParserContext)
    {
        parse(toSectionSourceCode(section), elementConsumer, pureGrammarParserContext);
    }

    protected abstract void parse(SectionSourceCode section, Consumer<PackageableElement> elementConsumer, PureGrammarParserContext pureGrammarParserContext);

    protected CompileResult getCompileResult(SectionState sectionState)
    {
        DocumentState documentState = sectionState.getDocumentState();
        GlobalState globalState = documentState.getGlobalState();
        return globalState.getProperty(COMPILE_RESULT, () -> tryCompile(globalState, documentState, sectionState));
    }

    protected CompileResult tryCompile(GlobalState globalState, DocumentState documentState, SectionState sectionState)
    {
        try
        {
            PureModelContextData.Builder builder = PureModelContextData.newBuilder();
            globalState.forEachDocumentState(docState -> docState.forEachSectionState(secState ->
            {
                ParseResult parseResult = secState.getProperty(PARSE_RESULT);
                if (parseResult != null)
                {
                    builder.addElements(parseResult.getElements());
                }
            }));
            PureModel pureModel = Compiler.compile(builder.build(), DeploymentMode.PROD, Collections.emptyList());
            return new CompileResult(pureModel);
        }
        catch (EngineException e)
        {
            SourceInformation sourceInfo = e.getSourceInformation();
            if (!isValidSourceInfo(sourceInfo))
            {
                LOGGER.warn("Invalid source information in exception during compilation requested for section {} of {}: {}", sectionState.getSectionNumber(), documentState.getDocumentId(), (sourceInfo == null) ? null : sourceInfo.getMessage(), e);
            }
            return new CompileResult(e);
        }
        catch (Exception e)
        {
            LOGGER.error("Unexpected exception during compilation requested for section {} of {}", sectionState.getSectionNumber(), documentState.getDocumentId(), e);
            return new CompileResult(e);
        }
    }

    protected LegendDeclaration getDeclaration(PackageableElement element)
    {
        String path = element.getPath();
        if (!isValidSourceInfo(element.sourceInformation))
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
        forEachChild(element, c -> addChildIfNonNull(builder, c));
        return builder.build();
    }

    protected abstract String getClassifier(PackageableElement element);

    protected void forEachChild(PackageableElement element, Consumer<LegendDeclaration> consumer)
    {
        // Do nothing by default
    }

    private SectionSourceCode toSectionSourceCode(SectionState sectionState)
    {
        String sourceId = sectionState.getDocumentState().getDocumentId();
        GrammarSection section = sectionState.getSection();
        SourceInformation sectionSourceInfo = new SourceInformation(sourceId, section.getStartLine(), 0, section.getEndLine(), section.getLineLength(section.getEndLine()));
        ParseTreeWalkerSourceInformation walkerSourceInfo = new ParseTreeWalkerSourceInformation.Builder(sourceId, section.getStartLine(), 0).build();
        return new SectionSourceCode(section.getText(true), section.getGrammar(), sectionSourceInfo, walkerSourceInfo);
    }

    protected static void addChildIfNonNull(LegendDeclaration.Builder builder, LegendDeclaration declaration)
    {
        if (declaration != null)
        {
            builder.withChild(declaration);
        }
    }

    /**
     * Check if the source information is valid.
     *
     * @param sourceInfo source information
     * @return whether source information is valid
     */
    protected static boolean isValidSourceInfo(SourceInformation sourceInfo)
    {
        return (sourceInfo != null) &&
                (sourceInfo.startLine >= 0) &&
                (sourceInfo.startColumn > 0) &&
                (sourceInfo.startLine <= sourceInfo.endLine) &&
                ((sourceInfo.startLine == sourceInfo.endLine) ? (sourceInfo.startColumn <= sourceInfo.endColumn) : (sourceInfo.endColumn > 0));
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

    protected static class Result<T>
    {
        protected final T result;
        protected final Exception e;

        protected Result(T result, Exception e)
        {
            this.result = result;
            this.e = e;
        }

        public boolean hasResult()
        {
            return this.result != null;
        }

        public T getResult()
        {
            return this.result;
        }

        public boolean hasException()
        {
            return this.e != null;
        }

        public Exception getException()
        {
            return this.e;
        }

        public boolean hasEngineException()
        {
            return this.e instanceof EngineException;
        }

        public EngineException getEngineException()
        {
            return hasEngineException() ? (EngineException) this.e : null;
        }
    }

    protected static class ParseResult extends Result<ImmutableList<PackageableElement>>
    {
        private ParseResult(ImmutableList<PackageableElement> elements, Exception e)
        {
            super(elements, e);
        }

        private ParseResult(ImmutableList<PackageableElement> elements)
        {
            this(elements, null);
        }

        public ImmutableList<PackageableElement> getElements()
        {
            return getResult();
        }

        public void forEachElement(Consumer<? super PackageableElement> consumer)
        {
            this.result.forEach(consumer);
        }
    }

    protected static class CompileResult extends Result<PureModel>
    {
        private CompileResult(PureModel pureModel)
        {
            super(pureModel, null);
        }

        private CompileResult(Exception e)
        {
            super(null, e);
        }

        public boolean successful()
        {
            return hasResult();
        }

        public PureModel getPureModel()
        {
            return getResult();
        }
    }
}