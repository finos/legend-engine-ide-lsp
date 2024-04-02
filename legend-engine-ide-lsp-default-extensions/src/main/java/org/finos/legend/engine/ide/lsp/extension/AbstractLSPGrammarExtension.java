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

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.block.function.checked.ThrowingFunction;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendClientCommand;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommand;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommandType;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult.Type;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendInputParamter;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTest;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.compiler.Compiler;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.SourceInformationHelper;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.Warning;
import org.finos.legend.engine.language.pure.grammar.from.ParseTreeWalkerSourceInformation;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParserContext;
import org.finos.legend.engine.language.pure.grammar.from.SectionSourceCode;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposer;
import org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposerContext;
import org.finos.legend.engine.protocol.pure.v1.ProtocolToClassifierPathLoader;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.SourceInformation;
import org.finos.legend.engine.protocol.pure.v1.model.context.EngineErrorType;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.test.TestSuite;
import org.finos.legend.engine.shared.core.api.grammar.RenderStyle;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractLSPGrammarExtension implements LegendLSPGrammarExtension
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLSPGrammarExtension.class);

    private static final String PARSE_RESULT = "parse";
    private static final String REFERENCE_RESULT = "reference";
    private static final String COMPILE_RESULT = "compile";

    private final Map<Class<? extends PackageableElement>, String> classToClassifier = ProtocolToClassifierPathLoader.getProtocolClassToClassifierMap();
    private final LegendEngineServerClient engineServerClient = newEngineServerClient();

    private final JsonMapper protocolMapper = PureProtocolObjectMapperFactory.withPureProtocolExtensions(JsonMapper.builder()
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .build());

    private final PureGrammarComposer composer = PureGrammarComposer.newInstance(
            PureGrammarComposerContext.Builder.newInstance()
                    .withRenderStyle(RenderStyle.PRETTY)
                    .build()
    );

    private final TestableCommandsSupport testableCommandsSupport;
    private final List<CommandsSupport> commandsSupports = new ArrayList<>();

    public AbstractLSPGrammarExtension()
    {
        this.testableCommandsSupport = new TestableCommandsSupport(this);
        this.commandsSupports.add(new FunctionActivatorCommandsSupport(this));
    }

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
                consumer.accept(LegendDiagnostic.newDiagnostic(SourceInformationUtil.toLocation(sourceInfo), e.getMessage(), LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Parser));
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
                consumer.accept(LegendDiagnostic.newDiagnostic(SourceInformationUtil.toLocation(sourceInfo), e.getMessage(), LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Compiler));
            }
        }
        if (compileResult.getPureModel() != null)
        {
            MutableList<Warning> warnings = compileResult.getPureModel().getWarnings();
            warnings.forEach(warning ->
            {
                SourceInformation sourceInfo = warning.sourceInformation;
                String docId = sectionState.getDocumentState().getDocumentId();
                if (!isValidSourceInfo(sourceInfo))
                {
                    if ((sourceInfo != null) && docId.equals(sourceInfo.sourceId))
                    {
                        LOGGER.warn("Invalid source information in compiler warning in {}: cannot create diagnostic", docId);
                    }
                }
                else if (docId.equals(sourceInfo.sourceId))
                {
                    consumer.accept(LegendDiagnostic.newDiagnostic(SourceInformationUtil.toLocation(sourceInfo), warning.message, LegendDiagnostic.Kind.Warning, LegendDiagnostic.Source.Compiler));
                }
            });
        }
    }

    @Override
    public Iterable<? extends LegendCommand> getCommands(SectionState section)
    {
        ParseResult result = getParseResult(section);
        if (result.hasException() || result.getElements().isEmpty())
        {
            return Collections.emptyList();
        }

        MutableList<LegendCommand> commands = Lists.mutable.empty();
        result.getElements().forEach(element ->
        {
            String path = element.getPath();
            collectCommands(section, element, (id, title, sourceInfo, args, inputParameters, type) ->
            {
                if (isValidSourceInfo(sourceInfo))
                {
                    commands.add(LegendCommandFactory.newCommand(type, path, id, title, SourceInformationUtil.toLocation(sourceInfo), args, inputParameters));
                }
            });
        });
        return commands;
    }

    protected void collectCommands(SectionState sectionState, PackageableElement element, CommandConsumer consumer)
    {
        this.commandsSupports.forEach(x -> x.collectCommands(sectionState, element, consumer));
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs)
    {
        return execute(section, entityPath, commandId, executableArgs, Maps.mutable.empty());
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs, Map<String, Object> inputParameters)
    {
        ParseResult parseResult = getParseResult(section);
        PackageableElement element = parseResult.getElement(entityPath);

        if (element == null)
        {
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find " + entityPath, null));
        }

        return this.commandsSupports.stream()
                .filter(x -> x.getSupportedCommands().contains(commandId))
                .findAny()
                .map(x -> x.executeCommand(section, element, commandId, executableArgs, inputParameters))
                .orElseGet(() ->
                {
                    LOGGER.warn("Unknown command id for {}: {}", entityPath, commandId);
                    return Collections.emptyList();
                });
    }

    public LegendExecutionResult errorResult(Throwable t, String entityPath)
    {
        return errorResult(t, null, entityPath, (t instanceof EngineException) ? SourceInformationUtil.toLocation(((EngineException) t).getSourceInformation()) : null);
    }

    protected LegendExecutionResult errorResult(Throwable t, String entityPath, TextLocation location)
    {
        return errorResult(t, null, entityPath, location);
    }

    protected LegendExecutionResult errorResult(Throwable t, String message, String entityPath, TextLocation location)
    {
        return LegendExecutionResult.errorResult(t, message, entityPath, location);
    }


    protected ParseResult getParseResult(SectionState sectionState)
    {
        return sectionState.getProperty(PARSE_RESULT, () -> tryParse(sectionState));
    }

    private ParseResult tryParse(SectionState sectionState)
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
            SourceInformation sectionSourceInfo = getSourceInformation(sectionState);
            LOGGER.error("Unexpected exception when parsing section {} of {}", sectionState.getSectionNumber(), sectionState.getDocumentState().getDocumentId(), e);
            return new ParseResult(elements.toImmutable(), new EngineException(e.getMessage(), sectionSourceInfo, EngineErrorType.PARSER, e));
        }
    }

    private static SourceInformation getSourceInformation(SectionState sectionState)
    {
        GrammarSection section = sectionState.getSection();
        int startLine = section.hasGrammarDeclaration() ? (section.getStartLine() + 1) : section.getStartLine();
        return new SourceInformation(sectionState.getDocumentState().getDocumentId(), startLine, 0, section.getEndLine(), section.getLineLength(section.getEndLine()));
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

    public CompileResult getCompileResult(SectionState sectionState)
    {
        DocumentState documentState = sectionState.getDocumentState();
        GlobalState globalState = documentState.getGlobalState();
        return globalState.getProperty(COMPILE_RESULT, () -> tryCompile(globalState, documentState, sectionState));
    }

    protected CompileResult tryCompile(GlobalState globalState, DocumentState documentState, SectionState sectionState)
    {
        globalState.logInfo("Starting compilation");
        PureModelContextData pureModelContextData = null;
        try
        {
            pureModelContextData = buildPureModelContextData(globalState);
            PureModel pureModel = Compiler.compile(pureModelContextData, DeploymentMode.PROD, "");
            globalState.logInfo("Compilation completed successfully");
            return new CompileResult(pureModel, pureModelContextData);
        }
        catch (EngineException e)
        {
            SourceInformation sourceInfo = e.getSourceInformation();
            if (isValidSourceInfo(sourceInfo))
            {
                globalState.logInfo("Compilation completed with error " + "(" + sourceInfo.sourceId + " " + SourceInformationUtil.toLocation(sourceInfo) + "): " + e.getMessage());
            }
            else
            {
                globalState.logInfo("Compilation completed with error: " + e.getMessage());
                globalState.logWarning("Invalid source information for compilation error");
                LOGGER.warn("Invalid source information in exception during compilation requested for section {} of {}: {}", sectionState.getSectionNumber(), documentState.getDocumentId(), (sourceInfo == null) ? null : sourceInfo.getMessage(), e);
            }
            return new CompileResult(e, pureModelContextData);
        }
        catch (Exception e)
        {
            LOGGER.error("Unexpected exception during compilation requested for section {} of {}", sectionState.getSectionNumber(), documentState.getDocumentId(), e);
            globalState.logWarning("Unexpected error during compilation");
            return new CompileResult(new EngineException(e.getMessage(), getSourceInformation(sectionState), EngineErrorType.COMPILATION, e), pureModelContextData);
        }
    }

    protected PureModelContextData buildPureModelContextData(GlobalState globalState)
    {
        return pureModelContextDataBuilder(globalState).build();
    }

    protected PureModelContextData.Builder pureModelContextDataBuilder(GlobalState globalState)
    {
        PureModelContextData.Builder builder = PureModelContextData.newBuilder();

        globalState.forEachDocumentState(docState -> docState.forEachSectionState(secState ->
        {
            ParseResult parseResult = secState.getProperty(PARSE_RESULT);
            if ((parseResult == null) && (secState.getExtension() instanceof AbstractLSPGrammarExtension))
            {
                parseResult = ((AbstractLSPGrammarExtension) secState.getExtension()).getParseResult(secState);
            }
            if (parseResult != null)
            {
                builder.addElements(parseResult.getElements());
            }
        }));
        return builder;
    }

    protected PureModelContextData deserializePMCD(String json)
    {
        try
        {
            return getProtocolMapper().readValue(json, PureModelContextData.class);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private JsonMapper getProtocolMapper()
    {
        return this.protocolMapper;
    }

    protected boolean isEngineServerConfigured()
    {
        return this.engineServerClient.isServerConfigured();
    }

    protected String postEngineServer(String path, Object payload)
    {
        return postEngineServer(path, payload, stream ->
        {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            stream.transferTo(bytes);
            return bytes.toString(StandardCharsets.UTF_8);
        });
    }

    protected <T> T postEngineServer(String path, Object payload, Class<T> responseType)
    {
        return postEngineServer(path, payload, stream -> getProtocolMapper().readValue(stream, responseType));
    }

    protected <T> T postEngineServer(String path, Object payload, ThrowingFunction<InputStream, T> consumer)
    {
        if (!isEngineServerConfigured())
        {
            throw new IllegalStateException("Engine server is not configured");
        }

        try
        {
            JsonMapper mapper = getProtocolMapper();
            String payloadJson = mapper.writeValueAsString(payload);
            return this.engineServerClient.post(path, payloadJson, consumer);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    protected String toGrammar(PureModelContextData pmcd)
    {
        return getComposer().renderPureModelContextData(pmcd);
    }

    private PureGrammarComposer getComposer()
    {
        return this.composer;
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
                .withLocation(SourceInformationUtil.toLocation(element.sourceInformation));
        forEachChild(element, c -> addChildIfNonNull(builder, c));
        return builder.build();
    }

    protected String getClassifier(PackageableElement element)
    {
        return this.classToClassifier.get(element.getClass());
    }

    protected List<? extends TestSuite> getTestSuites(PackageableElement element)
    {
        return Lists.fixedSize.empty();
    }

    @Override
    public final List<LegendTest> testCases(SectionState section)
    {
        ParseResult result = getParseResult(section);
        if (result.hasException() || result.getElements().isEmpty())
        {
            return Collections.emptyList();
        }

        return result.getElements().stream().map(this.testableCommandsSupport::collectTests).flatMap(Optional::stream).collect(Collectors.toList());
    }

    @Override
    public final List<LegendTestExecutionResult> executeTests(SectionState section, TextLocation location, String testId, Set<String> excludedTestIds)
    {
        return this.getParseResult(section)
                .getElements()
                .stream()
                .filter(x -> SourceInformationUtil.toLocation(x.sourceInformation).subsumes(location))
                .findAny()
                .map(e -> this.testableCommandsSupport.executeTests(section, e, testId, excludedTestIds))
                .orElse(List.of());
    }

    protected void forEachChild(PackageableElement element, Consumer<LegendDeclaration> consumer)
    {
        // Do nothing by default
    }

    protected SectionSourceCode toSectionSourceCode(SectionState sectionState)
    {
        String sourceId = sectionState.getDocumentState().getDocumentId();
        GrammarSection section = sectionState.getSection();
        SourceInformation sectionSourceInfo = getSourceInformation(sectionState);
        ParseTreeWalkerSourceInformation walkerSourceInfo = new ParseTreeWalkerSourceInformation.Builder(sourceId, sectionSourceInfo.startLine, 0).build();
        return new SectionSourceCode(section.getText(true), section.getGrammar(), sectionSourceInfo, walkerSourceInfo);
    }

    protected static void addChildIfNonNull(LegendDeclaration.Builder builder, LegendDeclaration declaration)
    {
        if (declaration != null)
        {
            builder.withChild(declaration);
        }
    }

    protected Optional<PackageableElement> getElementAtPosition(SectionState sectionState, TextPosition position)
    {
        ParseResult parseResult = this.getParseResult(sectionState);
        return parseResult
                .getElements()
                .stream()
                .filter(x -> this.isPositionIncludedOnSourceInfo(position, x.sourceInformation))
                .findAny();
    }

    private boolean isPositionIncludedOnSourceInfo(TextPosition position, SourceInformation sourceInformation)
    {
        return isValidSourceInfo(sourceInformation) && SourceInformationUtil.toLocation(sourceInformation).getTextInterval().includes(position);
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
                (sourceInfo.startLine > 0) &&
                (sourceInfo.startColumn > 0) &&
                (sourceInfo.startLine <= sourceInfo.endLine) &&
                ((sourceInfo.startLine == sourceInfo.endLine) ? (sourceInfo.startColumn <= sourceInfo.endColumn) : (sourceInfo.endColumn > 0));
    }

    protected Iterable<LegendCompletion> computeCompletionsForSupportedTypes(SectionState section, TextPosition location, Set<String> supportedTypes)
    {
        String text = section.getSection().getLineUpTo(location);

        if (text.isEmpty())
        {
            return supportedTypes.stream().map(x -> new LegendCompletion("New " + x, x)).collect(Collectors.toList());
        }
        else if (this.getElementAtPosition(section, location).isEmpty())
        {
            return supportedTypes.stream().filter(x -> x.startsWith(text)).map(x -> new LegendCompletion("New " + x, x)).collect(Collectors.toList());
        }

        return List.of();
    }

    @Override
    public Optional<LegendReference> getLegendReference(SectionState section, TextPosition textPosition)
    {
        Optional<PackageableElement> elementAtPosition = this.getElementAtPosition(section, textPosition);

        return elementAtPosition.flatMap(pe ->
                        this.getReferenceResolversResult(section, pe)
                                .stream()
                                .filter(ref -> ref.getLocation().getTextInterval().includes(textPosition))
                                .findAny()
                )
                .flatMap(reference ->
                        Optional.of(this.getCompileResult(section))
                                .map(CompileResult::getPureModel)
                                .map(x -> x.getContext(elementAtPosition.get()))
                                .flatMap(reference::goToReferenced)
                                .map(coreInstance ->
                                {
                                    SourceInformation sourceInfo = SourceInformationHelper.fromM3SourceInformation(coreInstance.getSourceInformation());
                                    if (isValidSourceInfo(sourceInfo))
                                    {
                                        TextLocation declarationLocation = SourceInformationUtil.toLocation(sourceInfo);
                                        return LegendReference.builder()
                                                .withLocation(reference.getLocation())
                                                .withReferencedLocation(declarationLocation)
                                                .build();
                                    }

                                    LOGGER.warn("Reference points to an element without source information");

                                    return null;
                                }));
    }

    private Collection<LegendReferenceResolver> getReferenceResolversResult(SectionState section, PackageableElement packageableElement)
    {
        return section.getProperty(REFERENCE_RESULT + ":" + packageableElement.getPath(), () -> getReferenceResolvers(section, packageableElement));
    }

    protected Collection<LegendReferenceResolver> getReferenceResolvers(SectionState section, PackageableElement packageableElement)
    {
        return List.of();
    }

    private static LegendEngineServerClient newEngineServerClient()
    {
        for (LegendEngineServerClient client : ServiceLoader.load(LegendEngineServerClient.class))
        {
            if (client.isServerConfigured())
            {
                LOGGER.debug("Using Legend Engine server client: {}", client.getClass().getName());
                return client;
            }
        }
        LOGGER.debug("Using default Legend Engine server client");
        return new DefaultLegendEngineServerClient();
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

        public PackageableElement getElement(String path)
        {
            return this.result.detect(e -> path.equals(e.getPath()));
        }

        public void forEachElement(Consumer<? super PackageableElement> consumer)
        {
            this.result.forEach(consumer);
        }
    }

    public static class CompileResult extends Result<PureModel>
    {
        private final PureModelContextData pureModelContextData;

        private CompileResult(PureModel pureModel, PureModelContextData pureModelContextData)
        {
            super(pureModel, null);
            this.pureModelContextData = pureModelContextData;
        }

        private CompileResult(Exception e, PureModelContextData pureModelContextData)
        {
            super(null, e);
            this.pureModelContextData = pureModelContextData;
        }

        public PureModel getPureModel()
        {
            return getResult();
        }

        public PureModelContextData getPureModelContextData()
        {
            return this.pureModelContextData;
        }
    }

    private static class LegendCommandFactory
    {
        public static LegendCommand newCommand(LegendCommandType type, String path, String id, String title, TextLocation textLocation, Map<String, String> arguments, Map<String, LegendInputParamter> inputParameters)
        {
            if (type.equals(LegendCommandType.CLIENT))
            {
                return LegendClientCommand.newCommand(path, id, title, textLocation, arguments, inputParameters);
            }
            return LegendCommand.newCommand(path, id, title, textLocation, arguments, inputParameters);
        }
    }

    protected interface CommandConsumer
    {
        default void accept(String id, String title, SourceInformation sourceInfo)
        {
            accept(id, title, sourceInfo, Collections.emptyMap());
        }

        default void accept(String id, String title, SourceInformation sourceInfo, LegendCommandType type)
        {
            accept(id, title, sourceInfo, Collections.emptyMap(), Collections.emptyMap(), type);
        }

        default void accept(String id, String title, SourceInformation sourceInfo, Map<String, String> arguments)
        {
            accept(id, title, sourceInfo, arguments, Collections.emptyMap(), LegendCommandType.SERVER);
        }

        void accept(String id, String title, SourceInformation sourceInfo, Map<String, String> arguments, Map<String, LegendInputParamter> inputParameters, LegendCommandType type);
    }
}