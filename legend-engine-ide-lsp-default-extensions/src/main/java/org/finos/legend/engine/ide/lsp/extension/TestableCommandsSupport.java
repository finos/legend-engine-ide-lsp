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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTest;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestAssertionResult;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.test.TestSuite;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.TestAssertion;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.AssertFail;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.AssertPass;
import org.finos.legend.engine.protocol.pure.v1.model.test.assertion.status.EqualToJsonAssertFail;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestError;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestExecuted;
import org.finos.legend.engine.protocol.pure.v1.model.test.result.TestExecutionStatus;
import org.finos.legend.engine.testable.TestableRunner;
import org.finos.legend.engine.testable.extension.TestableRunnerExtension;
import org.finos.legend.engine.testable.extension.TestableRunnerExtensionLoader;
import org.finos.legend.engine.testable.model.RunTestsResult;
import org.finos.legend.engine.testable.model.RunTestsTestableInput;
import org.finos.legend.engine.testable.model.UniqueTestId;
import org.finos.legend.engine.testable.service.result.MultiExecutionServiceTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestableCommandsSupport
{
    private static final Logger LOGGER = LoggerFactory.getLogger(TestableCommandsSupport.class);

    private final Map<String, ? extends TestableRunnerExtension> testableRunners = TestableRunnerExtensionLoader.getClassifierPathToTestableRunnerMap();

    private final AbstractLSPGrammarExtension extension;

    TestableCommandsSupport(AbstractLSPGrammarExtension extension)
    {
        this.extension = extension;
    }

    public Optional<LegendTest> collectTests(PackageableElement element)
    {
        if (this.isTestable(element))
        {
            // todo enhance engine to provide test suites per testables
            List<? extends TestSuite> testSuites = this.extension.getTestSuites(element);
            if (!testSuites.isEmpty())
            {
                List<LegendTest> testSuitesLegendTest = testSuites.stream().map(testSuite ->
                {
                    List<LegendTest> testLegendTest = testSuite.tests.stream().map(test -> LegendTest.newLegendTest(
                            List.of(),
                            SourceInformationUtil.toLocation(test.sourceInformation),
                            element.getPath(),
                            testSuite.id,
                            test.id
                    )).collect(Collectors.toList());

                    return LegendTest.newLegendTest(
                            testLegendTest,
                            SourceInformationUtil.toLocation(testSuite.sourceInformation),
                            element.getPath(),
                            testSuite.id
                    );
                }).collect(Collectors.toList());

                return Optional.of(
                        LegendTest.newLegendTest(
                                testSuitesLegendTest,
                                SourceInformationUtil.toLocation(element.sourceInformation),
                                element.getPath()
                        )
                );
            }
        }

        return Optional.empty();
    }

    public List<LegendTestExecutionResult> executeTests(SectionState sectionState, PackageableElement element, String testId, Set<String> excludedTestIds)
    {
        return this.collectTests(element)
                .map(test ->
                {
                    ArrayDeque<LegendTest> q = new ArrayDeque<>();
                    q.add(test);

                    while (!q.isEmpty())
                    {
                        LegendTest fromQ = q.pop();
                        if (fromQ.getId().equals(testId))
                        {
                            return this.executeTests(sectionState, element, fromQ, excludedTestIds);
                        }
                        else
                        {
                            q.addAll(fromQ.getChildren());
                        }
                    }

                    return List.<LegendTestExecutionResult>of();
                })
                .orElse(List.of());
    }

    public List<LegendTestExecutionResult> executeTests(SectionState sectionState, PackageableElement element, LegendTest test, Set<String> excludedTestIds)
    {
        List<UniqueTestId> uniqueTestIds;

        // top level (ie testable element) and no exclusions, then short-circuit to execute all test cases...
        if (excludedTestIds.isEmpty() && test.getIdComponents().size() == 1)
        {
            uniqueTestIds = List.of();
        }
        // else collect the unique test id base on current legend test and exclusion rules
        else
        {
            uniqueTestIds = this.collectUniqueTestId(test, excludedTestIds);

            if (uniqueTestIds.isEmpty())
            {
                return List.of();
            }
        }

        return this.executeTests(sectionState, element, uniqueTestIds);
    }

    private List<UniqueTestId> collectUniqueTestId(LegendTest test, Set<String> excludedTestIds)
    {
        List<UniqueTestId> uniqueTestIds = new ArrayList<>();

        ArrayDeque<LegendTest> q = new ArrayDeque<>();
        q.add(test);

        while (!q.isEmpty())
        {
            LegendTest fromQ = q.pop();

            if (!excludedTestIds.contains(fromQ.getId()))
            {
                // if is childless, (and is an atomic test; 3 component ID)
                // only atomic test should be childless, but maybe some test suite might be incorrectly constructed?
                if (fromQ.getChildren().isEmpty() && fromQ.getIdComponents().size() == 3)
                {
                    uniqueTestIds.add(this.newTestId(fromQ.getIdComponents().get(1), fromQ.getIdComponents().get(2)));
                }
                else
                {
                    q.addAll(fromQ.getChildren());
                }
            }
        }

        return uniqueTestIds;
    }

    private UniqueTestId newTestId(String testSuiteId, String atomicTestId)
    {
        UniqueTestId testId = new UniqueTestId();
        testId.testSuiteId = testSuiteId;
        testId.atomicTestId = atomicTestId;
        return testId;
    }

    private List<LegendTestExecutionResult> executeTests(SectionState section, PackageableElement element, List<UniqueTestId> unitTestIds)
    {
        String entityPath = element.getPath();
        CompileResult compileResult = this.extension.getCompileResult(section);

        if (compileResult.hasEngineException())
        {
            return List.of(LegendTestExecutionResult.error(compileResult.getCompileErrorResult(), entityPath));
        }

        try
        {
            // suite ->    test ->     assertion
            Map<String, Map<String, Map<String, TestAssertion>>> suiteToAssertionMap = this.extension.getTestSuites(element).stream()
                    .collect(Collectors.toMap(
                                    suite -> suite.id,
                                    suite -> suite.tests.stream()
                                            .collect(Collectors.toMap(
                                                            at -> at.id,
                                                            at -> at.assertions.stream()
                                                                    .collect(Collectors.toMap(
                                                                                    assertion -> assertion.id,
                                                                                    Function.identity()
                                                                            )
                                                                    )
                                                    )
                                            )
                            )
                    );

            TestableRunner runner = new TestableRunner();
            RunTestsTestableInput runTestsTestableInput = new RunTestsTestableInput();
            runTestsTestableInput.testable = entityPath;
            runTestsTestableInput.unitTestIds = unitTestIds;
            RunTestsResult testsResult = runner.doTests(Collections.singletonList(runTestsTestableInput), compileResult.getPureModel(), compileResult.getPureModelContextData());
            return testsResult.results.stream().map(res ->
            {
                // todo - is there a way to eliminate this instanceof and normalize test results in Engine platform?
                // for multi, all test are for one key, so we extract for that key, and use that result instead
                if (res instanceof MultiExecutionServiceTestResult)
                {
                    MultiExecutionServiceTestResult multiExecutionServiceTestResult = (MultiExecutionServiceTestResult) res;
                    if (multiExecutionServiceTestResult.getKeyIndexedTestResults().size() == 1)
                    {
                        res = multiExecutionServiceTestResult.getKeyIndexedTestResults().values().iterator().next();
                    }
                }

                if (res instanceof TestError)
                {
                    TestError testError = (TestError) res;
                    return LegendTestExecutionResult.error(testError.error, entityPath, res.testSuiteId, res.atomicTestId);
                }
                else if (res instanceof TestExecuted)
                {
                    TestExecuted testExecuted = (TestExecuted) res;

                    if (TestExecutionStatus.PASS.equals(testExecuted.testExecutionStatus))
                    {
                        return LegendTestExecutionResult.success(entityPath, res.testSuiteId, res.atomicTestId);
                    }
                    else
                    {
                        Map<String, TestAssertion> assertionMap = suiteToAssertionMap.get(res.testSuiteId).get(res.atomicTestId);

                        List<LegendTestAssertionResult> assertionResults = testExecuted.assertStatuses.stream()
                                .filter(Predicate.not(AssertPass.class::isInstance))
                                .map(assertStatus ->
                                {
                                    TestAssertion testAssertion = assertionMap.get(assertStatus.id);
                                    TextLocation location = testAssertion.sourceInformation != null ? SourceInformationUtil.toLocation(testAssertion.sourceInformation) : null;
                                    if (assertStatus instanceof AssertFail)
                                    {
                                        String message = ((AssertFail) assertStatus).message;
                                        if (assertStatus instanceof EqualToJsonAssertFail)
                                        {
                                            EqualToJsonAssertFail fail = (EqualToJsonAssertFail) assertStatus;
                                            return LegendTestAssertionResult.failure(assertStatus.id, location, message, fail.expected, fail.actual);
                                        }
                                        else
                                        {
                                            return LegendTestAssertionResult.failure(assertStatus.id, location, message, null, null);
                                        }
                                    }
                                    else
                                    {
                                        String message = "Unknown assert status: " + assertStatus.getClass().getName();
                                        LOGGER.warn(message);
                                        return LegendTestAssertionResult.unknown(assertStatus.id, message, location);
                                    }
                                }).collect(Collectors.toList());

                        return LegendTestExecutionResult.failures(assertionResults, entityPath, res.testSuiteId, res.atomicTestId);
                    }
                }
                else
                {
                    String resAsJson = this.extension.toProtocolJson(res);
                    String message = "Unknown test result type: " + res.getClass().getName() + ".  Value: " + resAsJson;
                    LOGGER.warn(message);
                    return LegendTestExecutionResult.unknown(message, entityPath, res.testSuiteId, res.atomicTestId);
                }
            }).collect(Collectors.toList());
        }
        catch (Exception e)
        {
            return List.of(LegendTestExecutionResult.error(e, entityPath));
        }
    }

    private boolean isTestable(PackageableElement element)
    {
        String classifier = this.extension.getClassifier(element);
        return classifier != null && this.testableRunners.containsKey(classifier);
    }
}
