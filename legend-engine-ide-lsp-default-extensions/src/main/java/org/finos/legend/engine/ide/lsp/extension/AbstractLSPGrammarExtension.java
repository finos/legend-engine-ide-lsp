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
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommand;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
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
import org.finos.legend.engine.protocol.pure.v1.model.SourceInformation;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.test.AtomicTest;
import org.finos.legend.engine.protocol.pure.v1.model.test.Test;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
                        LOGGER.warn("Invalid source information in compiler warning in {}: cannot create diagnostic", docId, warning);
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
            SourceInformation sourceInfo = element.sourceInformation;
            if (isValidSourceInfo(sourceInfo))
            {
                String path = element.getPath();
                TextInterval location = toLocation(sourceInfo);
                collectCommands(section, element, (id, title) -> commands.add(LegendCommand.newCommand(path, id, title, location)));
            }
            List<? extends TestSuite> testSuites = getTestSuites(element);
            testSuites.forEach(testSuite ->
            {
                SourceInformation sourceInformation = testSuite.sourceInformation;
                if (isValidSourceInfo(sourceInfo))
                {
                    String path = element.getPath();
                    TextInterval location = toLocation(sourceInformation);
                    Map<String, String> executableArgs = new HashMap<>();
                    executableArgs.put(TEST_SUITE_ID, testSuite.id);
                    LegendCommand command = LegendCommand.newCommand(path, RUN_TEST_SUITE_COMMAND_ID, RUN_TEST_SUITE_COMMAND_TITLE, location, executableArgs);
                    commands.add(command);
                }
                List<AtomicTest> tests = testSuite.tests;
                tests.forEach(test ->
                {
                    SourceInformation testSourceInfo = test.sourceInformation;
                    if (isValidSourceInfo(testSourceInfo))
                    {
                        String path = element.getPath();
                        TextInterval location = toLocation(testSourceInfo);
                        Map<String, String> executableArgs = new HashMap<>();
                        executableArgs.put(TEST_SUITE_ID, testSuite.id);
                        executableArgs.put(TEST_ID, test.id);
                        LegendCommand command = LegendCommand.newCommand(path, RUN_TEST_COMMAND_ID, RUN_TEST_COMMAND_TITLE, location, executableArgs);
                        commands.add(command);
                    }
                });
            });
        });
        return commands;
    }

    protected void collectCommands(SectionState sectionState, PackageableElement element, BiConsumer<String, String> consumer)
    {
        String classifier = getClassifier(element);
        if (this.testableRunners.containsKey(classifier))
        {
            consumer.accept(RUN_TESTS_COMMAND_ID, RUN_TESTS_COMMAND_TITLE);
        }
    }

    private Iterable<? extends  LegendExecutionResult> runTests(SectionState section, String entityPath, List<UniqueTestId> unitTestIds)
    {
        CompileResult compileResult = getCompileResult(section);
        if (compileResult.hasException())
        {
            Exception e = compileResult.getException();
            String message = e.getMessage();
            StringWriter writer = new StringWriter();
            try (PrintWriter pw = new PrintWriter(writer))
            {
                e.printStackTrace(pw);
            }
            return Collections.singletonList(LegendExecutionResult.newResult(LegendExecutionResult.Type.ERROR, (message == null) ? "Error" : message, writer.toString()));
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
                    results.add(LegendExecutionResult.newResult(LegendExecutionResult.Type.ERROR, builder.toString()));
                }
                else if (res instanceof TestExecuted)
                {
                    TestExecuted testExecuted = (TestExecuted) res;
                    String messagePrefix = appendTestId(testExecuted).toString();
                    testExecuted.assertStatuses.forEach(assertStatus ->
                    {
                        if (assertStatus instanceof AssertPass)
                        {
                            results.add(LegendExecutionResult.newResult(LegendExecutionResult.Type.SUCCESS, messagePrefix + "." + assertStatus.id + ": PASS"));
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
                            results.add(LegendExecutionResult.newResult(LegendExecutionResult.Type.FAILURE, builder.toString()));
                        }
                        else
                        {
                            String message = appendTestId(res).append(": WARNING\nUnknown assert status: ").append(assertStatus.getClass().getName()).toString();
                            LOGGER.warn(message);
                            results.add(LegendExecutionResult.newResult(LegendExecutionResult.Type.WARNING, message));
                        }
                    });
                }
                else
                {
                    String message = "Unknown test result type: " + res.getClass().getName();
                    LOGGER.warn(message);
                    results.add(LegendExecutionResult.newResult(LegendExecutionResult.Type.WARNING, message));
                }
            });
            return results;
        }
        catch (Exception e)
        {
            String message = e.getMessage();
            StringWriter writer = new StringWriter();
            try (PrintWriter pw = new PrintWriter(writer))
            {
                e.printStackTrace(pw);
            }
            return Collections.singletonList(LegendExecutionResult.newResult(LegendExecutionResult.Type.ERROR, (message == null) ? "Error" : message, writer.toString()));
        }
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs)
    {
        switch (commandId)
        {
            case RUN_TESTS_COMMAND_ID:
            {
                return runTests(section, entityPath, Lists.mutable.empty());
            }
            case RUN_TEST_SUITE_COMMAND_ID:
            {
                MutableList<UniqueTestId> testIds = Lists.mutable.empty();
                ParseResult parseResult = section.getProperty(PARSE_RESULT);
                String testSuiteId = executableArgs.get(TEST_SUITE_ID);
                if (testSuiteId != null)
                {
                    PackageableElement element = parseResult.getElements().stream().filter(e -> e.getPath().equals(entityPath)).collect(Collectors.toList()).get(0);
                    TestSuite testSuite = getTestSuites(element).stream().filter(t -> t.id.equals(testSuiteId)).collect(Collectors.toList()).get(0);
                    testSuite.tests.forEach(test ->
                    {
                        UniqueTestId testId = new UniqueTestId();
                        testId.atomicTestId = test.id;
                        testId.testSuiteId = testSuiteId;
                        testIds.add(testId);
                    });
                    return runTests(section, entityPath, testIds);
                }
                else
                {
                    LOGGER.warn("Unable to find test suite id to run tests", testSuiteId, entityPath, commandId);
                    return Collections.emptyList();
                }
            }
            case RUN_TEST_COMMAND_ID:
            {
                MutableList<UniqueTestId> testIds = Lists.mutable.empty();
                ParseResult parseResult = section.getProperty(PARSE_RESULT);
                String testSuiteId = executableArgs.get(TEST_SUITE_ID);
                String testId = executableArgs.get(TEST_ID);
                if (testSuiteId == null)
                {
                    LOGGER.warn("Unable to find test suite id to run tests", testSuiteId, entityPath, commandId);
                    return Collections.emptyList();
                }
                if (testId == null)
                {
                    LOGGER.warn("Unable to find test id to run tests", testId, entityPath, commandId);
                    return Collections.emptyList();
                }
                PackageableElement element = parseResult.getElements().stream().filter(e -> e.getPath().equals(entityPath)).collect(Collectors.toList()).get(0);
                TestSuite testSuite = getTestSuites(element).stream().filter(t -> t.id.equals(testSuiteId)).collect(Collectors.toList()).get(0);
                Test test = testSuite.tests.stream().filter(t -> t.id.equals(testId)).collect(Collectors.toList()).get(0);
                UniqueTestId uniqueTestId = new UniqueTestId();
                uniqueTestId.atomicTestId = test.id;
                uniqueTestId.testSuiteId = testSuiteId;
                testIds.add(uniqueTestId);
                return runTests(section, entityPath, testIds);
            }
            default:
            {
                LOGGER.warn("Unknown command id for {}: {}", entityPath, commandId);
                return Collections.emptyList();
            }
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
            PureModelContextData.Builder builder = PureModelContextData.newBuilder();
            globalState.forEachDocumentState(docState -> docState.forEachSectionState(secState ->
            {
                ParseResult parseResult = secState.getProperty(PARSE_RESULT);
                if (parseResult != null)
                {
                    builder.addElements(parseResult.getElements());
                }
            }));
            PureModelContextData pureModelContextData = builder.build();
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

    protected List<? extends TestSuite> getTestSuites(PackageableElement element)
    {
        return Lists.mutable.empty();
    }

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
}