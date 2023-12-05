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
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommand;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult.Type;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.language.pure.compiler.Compiler;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.Warning;
import org.finos.legend.engine.language.pure.grammar.from.ParseTreeWalkerSourceInformation;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParserContext;
import org.finos.legend.engine.language.pure.grammar.from.SectionSourceCode;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.language.pure.modelManager.ModelManager;
import org.finos.legend.engine.protocol.pure.v1.ProtocolToClassifierPathLoader;
import org.finos.legend.engine.protocol.pure.v1.model.SourceInformation;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.test.TestSuite;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.AssertFail;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.AssertPass;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.EqualToJsonAssertFail;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestError;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestExecuted;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestResult;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.finos.legend.engine.testable.TestableRunner;
import org.finos.legend.engine.testable.extension.TestableRunnerExtension;
import org.finos.legend.engine.testable.extension.TestableRunnerExtensionLoader;
import org.finos.legend.engine.testable.model.RunTestsResult;
import org.finos.legend.engine.testable.model.RunTestsTestableInput;
import org.finos.legend.engine.testable.model.UniqueTestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

abstract class AbstractLSPGrammarExtension implements LegendLSPGrammarExtension
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLSPGrammarExtension.class);

    private static final String RUN_TESTS_COMMAND_ID = "legend.testable.runTests";
    private static final String RUN_TESTS_COMMAND_TITLE = "Run tests";

    private static final String RUN_TEST_SUITE_COMMAND_ID = "legend.testable.runTestSuite";
    private static final String RUN_TEST_SUITE_COMMAND_TITLE = "Run test suite";
    private static final String TEST_SUITE_ID = "legend.testable.testSuiteId";

    private static final String RUN_TEST_COMMAND_ID = "legend.testable.runTest";
    private static final String RUN_TEST_COMMAND_TITLE = "Run test";
    private static final String TEST_ID = "legend.testable.testId";

    private static final String PARSE_RESULT = "parse";
    private static final String COMPILE_RESULT = "compile";

    private final Map<String, ? extends TestableRunnerExtension> testableRunners = TestableRunnerExtensionLoader.getClassifierPathToTestableRunnerMap();
    private final Map<Class<? extends PackageableElement>, String> classToClassifier = ProtocolToClassifierPathLoader.getProtocolClassToClassifierMap();

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
                    consumer.accept(LegendDiagnostic.newDiagnostic(toLocation(sourceInfo), warning.message, LegendDiagnostic.Kind.Warning, LegendDiagnostic.Source.Compiler));
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
            collectCommands(section, element, (id, title, sourceInfo, args) ->
            {
                if (isValidSourceInfo(sourceInfo))
                {
                    commands.add(LegendCommand.newCommand(path, id, title, toLocation(sourceInfo), args));
                }
            });
        });
        return commands;
    }

    protected void collectCommands(SectionState sectionState, PackageableElement element, CommandConsumer consumer)
    {
        String classifier = getClassifier(element);
        if ((classifier != null) && this.testableRunners.containsKey(classifier) && !getTestSuites(element).isEmpty())
        {
            List<? extends TestSuite> testSuites = getTestSuites(element);
            if (!testSuites.isEmpty())
            {
                consumer.accept(RUN_TESTS_COMMAND_ID, RUN_TESTS_COMMAND_TITLE, element.sourceInformation);
                testSuites.forEach(testSuite ->
                {
                    consumer.accept(RUN_TEST_SUITE_COMMAND_ID, RUN_TEST_SUITE_COMMAND_TITLE, testSuite.sourceInformation, Collections.singletonMap(TEST_SUITE_ID, testSuite.id));
                    testSuite.tests.forEach(test -> consumer.accept(RUN_TEST_COMMAND_ID, RUN_TEST_COMMAND_TITLE, test.sourceInformation, Maps.fixedSize.with(TEST_SUITE_ID, testSuite.id, TEST_ID, test.id)));
                });
            }
        }
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs)
    {
        switch (commandId)
        {
            case RUN_TESTS_COMMAND_ID:
            {
                return runTests(section, entityPath, Collections.emptyList());
            }
            case RUN_TEST_SUITE_COMMAND_ID:
            {
                String testSuiteId = executableArgs.get(TEST_SUITE_ID);
                if (testSuiteId == null)
                {
                    return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find test suite id to run tests for " + entityPath));
                }

                ParseResult parseResult = getParseResult(section);
                PackageableElement element = parseResult.getElement(entityPath);
                if (element == null)
                {
                    return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find " + entityPath));
                }
                TestSuite testSuite = Iterate.detect(getTestSuites(element), ts -> testSuiteId.equals(ts.id));
                if (testSuite == null)
                {
                    return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find test suite " + testSuiteId + " for " + entityPath));
                }
                return runTests(section, entityPath, ListIterate.collect(testSuite.tests, test -> newTestId(testSuiteId, test.id)));
            }
            case RUN_TEST_COMMAND_ID:
            {
                String testSuiteId = executableArgs.get(TEST_SUITE_ID);
                String testId = executableArgs.get(TEST_ID);
                if (testSuiteId == null)
                {
                    return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find test suite id to run test for " + entityPath));
                }
                if (testId == null)
                {
                    return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find test id to run test for " + entityPath));
                }
                return runTests(section, entityPath, Collections.singletonList(newTestId(testSuiteId, testId)));
            }
            default:
            {
                LOGGER.warn("Unknown command id for {}: {}", entityPath, commandId);
                return Collections.emptyList();
            }
        }
    }

    protected LegendExecutionResult errorResult(Throwable t, String entityPath)
    {
        return errorResult(t, null, entityPath);
    }

    protected LegendExecutionResult errorResult(Throwable t, String message, String entityPath)
    {
        StringWriter writer = new StringWriter();
        try (PrintWriter pw = new PrintWriter(writer))
        {
            t.printStackTrace(pw);
        }
        String resultMessage;
        if (message != null)
        {
            resultMessage = message;
        }
        else
        {
            String tMessage = t.getMessage();
            resultMessage = (tMessage == null) ? "Error" : tMessage;
        }
        return LegendExecutionResult.newResult(entityPath, Type.ERROR, resultMessage, writer.toString());
    }

    private UniqueTestId newTestId(String testSuiteId, String atomicTestId)
    {
        UniqueTestId testId = new UniqueTestId();
        testId.testSuiteId = testSuiteId;
        testId.atomicTestId = atomicTestId;
        return testId;
    }

    private Iterable<? extends LegendExecutionResult> runTests(SectionState section, String entityPath, List<UniqueTestId> unitTestIds)
    {
        CompileResult compileResult = getCompileResult(section);
        if (compileResult.hasException())
        {
            return Collections.singletonList(errorResult(compileResult.getException(), entityPath));
        }

        try
        {
            TestableRunner runner = new TestableRunner(new ModelManager(DeploymentMode.PROD));
            RunTestsTestableInput runTestsTestableInput = new RunTestsTestableInput();
            runTestsTestableInput.testable = entityPath;
            runTestsTestableInput.unitTestIds = unitTestIds;
            RunTestsResult testsResult = runner.doTests(Collections.singletonList(runTestsTestableInput), compileResult.getPureModel(), compileResult.getPureModelContextData());
            MutableList<LegendExecutionResult> results = Lists.mutable.empty();
            testsResult.results.forEach(res ->
            {
                if (res instanceof TestError)
                {
                    TestError testError = (TestError) res;
                    StringBuilder builder = appendTestId(testError).append(": ERROR\n").append(testError.error);
                    results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, res.testSuiteId, res.atomicTestId), Type.ERROR, builder.toString()));
                }
                else if (res instanceof TestExecuted)
                {
                    TestExecuted testExecuted = (TestExecuted) res;
                    String messagePrefix = appendTestId(testExecuted).toString();
                    testExecuted.assertStatuses.forEach(assertStatus ->
                    {
                        if (assertStatus instanceof AssertPass)
                        {
                            results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, res.testSuiteId, res.atomicTestId, assertStatus.id), Type.SUCCESS, messagePrefix + "." + assertStatus.id + ": PASS"));
                        }
                        else if (assertStatus instanceof AssertFail)
                        {
                            StringBuilder builder = new StringBuilder(messagePrefix).append('.').append(assertStatus.id).append(": FAILURE\n").append(((AssertFail) assertStatus).message);
                            if (assertStatus instanceof EqualToJsonAssertFail)
                            {
                                EqualToJsonAssertFail fail = (EqualToJsonAssertFail) assertStatus;
                                builder.append("\nexpected: ").append(fail.expected);
                                builder.append("\nactual:   ").append(fail.actual);
                            }
                            results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, res.testSuiteId, res.atomicTestId, assertStatus.id), Type.FAILURE, builder.toString()));
                        }
                        else
                        {
                            String message = appendTestId(res).append(": WARNING\nUnknown assert status: ").append(assertStatus.getClass().getName()).toString();
                            LOGGER.warn(message);
                            results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, res.testSuiteId, res.atomicTestId, assertStatus.id), Type.WARNING, message));
                        }
                    });
                }
                else
                {
                    String message = "Unknown test result type: " + res.getClass().getName();
                    LOGGER.warn(message);
                    results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, res.testSuiteId, res.atomicTestId), Type.WARNING, message));
                }
            });
            return results;
        }
        catch (Exception e)
        {
            return Collections.singletonList(errorResult(e, entityPath));
        }
    }

    private StringBuilder appendTestId(TestResult testResult)
    {
        StringBuilder builder = new StringBuilder(testResult.testable);
        if (testResult.testSuiteId != null)
        {
            builder.append('.').append(testResult.testSuiteId);
        }
        return builder.append('.').append(testResult.atomicTestId);
    }

    protected ParseResult getParseResult(SectionState sectionState)
    {
        return sectionState.getProperty(PARSE_RESULT, () -> tryParse(sectionState));
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
        globalState.logInfo("Starting compilation");
        try
        {
            PureModelContextData pureModelContextData = buildPureModelContextData(globalState);
            PureModel pureModel = Compiler.compile(pureModelContextData, DeploymentMode.PROD, Collections.emptyList());
            globalState.logInfo("Compilation completed successfully");
            return new CompileResult(pureModel, pureModelContextData);
        }
        catch (EngineException e)
        {
            SourceInformation sourceInfo = e.getSourceInformation();
            if (isValidSourceInfo(sourceInfo))
            {
                globalState.logInfo("Compilation completed with error " + "(" + sourceInfo.sourceId + " " + toLocation(sourceInfo).toCompactString() + "): " + e.getMessage());
            }
            else
            {
                globalState.logInfo("Compilation completed with error: " + e.getMessage());
                globalState.logWarning("Invalid source information for compilation error");
                LOGGER.warn("Invalid source information in exception during compilation requested for section {} of {}: {}", sectionState.getSectionNumber(), documentState.getDocumentId(), (sourceInfo == null) ? null : sourceInfo.getMessage(), e);
            }
            return new CompileResult(e);
        }
        catch (Exception e)
        {
            LOGGER.error("Unexpected exception during compilation requested for section {} of {}", sectionState.getSectionNumber(), documentState.getDocumentId(), e);
            globalState.logWarning("Unexpected error during compilation");
            return new CompileResult(e);
        }
    }

    protected PureModelContextData buildPureModelContextData(GlobalState globalState)
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
        return builder.build();
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

    protected String getClassifier(PackageableElement element)
    {
        return this.classToClassifier.get(element.getClass());
    }

    protected List<? extends TestSuite> getTestSuites(PackageableElement element)
    {
        return Lists.fixedSize.empty();
    }

    protected void forEachChild(PackageableElement element, Consumer<LegendDeclaration> consumer)
    {
        // Do nothing by default
    }

    private SectionSourceCode toSectionSourceCode(SectionState sectionState)
    {
        String sourceId = sectionState.getDocumentState().getDocumentId();
        GrammarSection section = sectionState.getSection();
        int startLine = section.hasGrammarDeclaration() ? (section.getStartLine() + 1) : section.getStartLine();
        SourceInformation sectionSourceInfo = new SourceInformation(sourceId, startLine, 0, section.getEndLine(), section.getLineLength(section.getEndLine()));
        ParseTreeWalkerSourceInformation walkerSourceInfo = new ParseTreeWalkerSourceInformation.Builder(sourceId, startLine, 0).build();
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
                (sourceInfo.startLine > 0) &&
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
        return TextInterval.newInterval(sourceInfo.startLine - 1, sourceInfo.startColumn - 1, sourceInfo.endLine - 1, sourceInfo.endColumn - 1);
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

    protected static class CompileResult extends Result<PureModel>
    {
        private PureModelContextData pureModelContextData;

        private CompileResult(PureModel pureModel, PureModelContextData pureModelContextData)
        {
            super(pureModel, null);
            this.pureModelContextData = pureModelContextData;
        }

        private CompileResult(Exception e)
        {
            super(null, e);
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

    protected interface CommandConsumer
    {
        default void accept(String id, String title, SourceInformation sourceInfo)
        {
            accept(id, title, sourceInfo, Collections.emptyMap());
        }

        void accept(String id, String title, SourceInformation sourceInfo, Map<String, String> arguments);
    }
}