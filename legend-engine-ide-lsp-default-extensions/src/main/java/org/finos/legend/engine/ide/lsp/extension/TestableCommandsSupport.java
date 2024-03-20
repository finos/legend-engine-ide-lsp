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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.test.AtomicTest;
import org.finos.legend.engine.protocol.pure.v1.model.test.TestSuite;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.AssertFail;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.AssertPass;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.EqualToJsonAssertFail;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestError;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestExecuted;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestResult;
import org.finos.legend.engine.testable.TestableRunner;
import org.finos.legend.engine.testable.extension.TestableRunnerExtension;
import org.finos.legend.engine.testable.extension.TestableRunnerExtensionLoader;
import org.finos.legend.engine.testable.model.RunTestsResult;
import org.finos.legend.engine.testable.model.RunTestsTestableInput;
import org.finos.legend.engine.testable.model.UniqueTestId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestableCommandsSupport implements CommandsSupport
{
    private static final Logger LOGGER = LoggerFactory.getLogger(TestableCommandsSupport.class);

    public static final String RUN_TESTS_COMMAND_ID = "legend.testable.runTests";
    private static final String RUN_TESTS_COMMAND_TITLE = "Run tests";

    public static final String RUN_TEST_SUITE_COMMAND_ID = "legend.testable.runTestSuite";
    public static final String RUN_TEST_SUITE_COMMAND_TITLE = "Run test suite";
    private static final String TEST_SUITE_ID = "legend.testable.testSuiteId";

    public static final String RUN_TEST_COMMAND_ID = "legend.testable.runTest";
    private static final String RUN_TEST_COMMAND_TITLE = "Run test";
    private static final String TEST_ID = "legend.testable.testId";

    private final Map<String, ? extends TestableRunnerExtension> testableRunners = TestableRunnerExtensionLoader.getClassifierPathToTestableRunnerMap();

    private final AbstractLSPGrammarExtension extension;

    TestableCommandsSupport(AbstractLSPGrammarExtension extension)
    {
        this.extension = extension;
    }

    @Override
    public Set<String> getSupportedCommands()
    {
        return Set.of();
    }

    @Override
    public void collectCommands(SectionState sectionState, PackageableElement element, AbstractLSPGrammarExtension.CommandConsumer consumer)
    {
        if (this.isTestable(element))
        {
            // todo enhance engine to provide test suites per testables
            List<? extends TestSuite> testSuites = this.extension.getTestSuites(element);
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
    public Iterable<? extends LegendExecutionResult> executeCommand(SectionState section, PackageableElement element, String commandId, Map<String, String> executableArgs, Map<String, Object> inputParameters)
    {
        TextLocation location = SourceInformationUtil.toLocation(element.sourceInformation);
        String entityPath = element.getPath();
        switch (commandId)
        {
            case RUN_TESTS_COMMAND_ID:
            {
                return runTests(section, element, Collections.emptyList());
            }
            case RUN_TEST_SUITE_COMMAND_ID:
            {
                String testSuiteId = executableArgs.get(TEST_SUITE_ID);
                if (testSuiteId == null)
                {
                    return Collections.singletonList(LegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.ERROR, "Unable to find test suite id to run tests for " + entityPath, location));
                }

                TestSuite testSuite = Iterate.detect(this.extension.getTestSuites(element), ts -> testSuiteId.equals(ts.id));
                if (testSuite == null)
                {
                    return Collections.singletonList(LegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.ERROR, "Unable to find test suite " + testSuiteId + " for " + entityPath, location));
                }
                return runTests(section, element, ListIterate.collect(testSuite.tests, test -> newTestId(testSuiteId, test.id)));
            }
            case RUN_TEST_COMMAND_ID:
            {
                String testSuiteId = executableArgs.get(TEST_SUITE_ID);
                String testId = executableArgs.get(TEST_ID);
                if (testSuiteId == null)
                {
                    return Collections.singletonList(LegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.ERROR, "Unable to find test suite id to run test for " + entityPath, location));
                }
                if (testId == null)
                {
                    return Collections.singletonList(LegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.ERROR, "Unable to find test id to run test for " + entityPath, location));
                }
                return runTests(section, element, Collections.singletonList(newTestId(testSuiteId, testId)));
            }
            default:
            {
                LOGGER.warn("Unknown command id for {}: {}", entityPath, commandId);
                return Collections.emptyList();
            }
        }
    }


    private UniqueTestId newTestId(String testSuiteId, String atomicTestId)
    {
        UniqueTestId testId = new UniqueTestId();
        testId.testSuiteId = testSuiteId;
        testId.atomicTestId = atomicTestId;
        return testId;
    }

    private List<? extends LegendExecutionResult> runTests(SectionState section, PackageableElement element, List<UniqueTestId> unitTestIds)
    {
        String entityPath = element.getPath();
        AbstractLSPGrammarExtension.CompileResult compileResult = this.extension.getCompileResult(section);
        if (compileResult.hasException())
        {
            return Collections.singletonList(this.extension.errorResult(compileResult.getException(), entityPath, SourceInformationUtil.toLocation(element.sourceInformation)));
        }

        try
        {
            List<? extends TestSuite> suites = this.extension.getTestSuites(element);
            TestableRunner runner = new TestableRunner();
            RunTestsTestableInput runTestsTestableInput = new RunTestsTestableInput();
            runTestsTestableInput.testable = entityPath;
            runTestsTestableInput.unitTestIds = unitTestIds;
            RunTestsResult testsResult = runner.doTests(Collections.singletonList(runTestsTestableInput), compileResult.getPureModel(), compileResult.getPureModelContextData());
            MutableList<LegendExecutionResult> results = Lists.mutable.empty();
            testsResult.results.forEach(res ->
            {
                TestSuite testSuite = Iterate.detect(suites, ts -> res.testSuiteId.equals(ts.id));
                AtomicTest test = Iterate.detect(testSuite.tests, at -> res.atomicTestId.equals(at.id));
                TextLocation location = SourceInformationUtil.toLocation(test.sourceInformation);

                if (res instanceof TestError)
                {
                    TestError testError = (TestError) res;
                    StringBuilder builder = appendTestId(testError).append(": ERROR\n").append(testError.error);
                    results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, res.testSuiteId, res.atomicTestId), LegendExecutionResult.Type.ERROR, builder.toString(), location));
                }
                else if (res instanceof TestExecuted)
                {
                    TestExecuted testExecuted = (TestExecuted) res;
                    String messagePrefix = appendTestId(testExecuted).toString();
                    testExecuted.assertStatuses.forEach(assertStatus ->
                    {
                        if (assertStatus instanceof AssertPass)
                        {
                            results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, res.testSuiteId, res.atomicTestId, assertStatus.id), LegendExecutionResult.Type.SUCCESS, messagePrefix + "." + assertStatus.id + ": PASS", location));
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
                            results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, res.testSuiteId, res.atomicTestId, assertStatus.id), LegendExecutionResult.Type.FAILURE, builder.toString(), location));
                        }
                        else
                        {
                            String message = appendTestId(res).append(": WARNING\nUnknown assert status: ").append(assertStatus.getClass().getName()).toString();
                            LOGGER.warn(message);
                            results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, res.testSuiteId, res.atomicTestId, assertStatus.id), LegendExecutionResult.Type.WARNING, message, location));
                        }
                    });
                }
                else
                {
                    String message = "Unknown test result type: " + res.getClass().getName();
                    LOGGER.warn(message);
                    results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, res.testSuiteId, res.atomicTestId), LegendExecutionResult.Type.WARNING, message, location));
                }
            });
            return results;
        }
        catch (Exception e)
        {
            return Collections.singletonList(this.extension.errorResult(e, entityPath, SourceInformationUtil.toLocation(element.sourceInformation)));
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

    private boolean isTestable(PackageableElement element)
    {
        String classifier = this.extension.getClassifier(element);
        return classifier != null && this.testableRunners.containsKey(classifier);
    }

    Stream<? extends LegendExecutionResult> executeAllTestCases(SectionState section)
    {
        return this.extension.getParseResult(section)
                .getElements()
                .stream()
                .flatMap(x ->
                        {

                            if (this.isTestable(x) && !this.extension.getTestSuites(x).isEmpty())
                            {
                                return this.runTests(section, x, Collections.emptyList()).stream();
                            }
                            else
                            {
                                return Stream.empty();
                            }
                        }
                );
    }
}
