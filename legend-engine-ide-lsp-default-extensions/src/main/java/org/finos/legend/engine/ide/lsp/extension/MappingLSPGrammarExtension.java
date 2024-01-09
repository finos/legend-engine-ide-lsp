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
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult.Type;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensionLoader;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.language.pure.grammar.from.mapping.MappingParser;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.mappingTest.MappingTestSuite;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.mappingTest.MappingTest_Legacy;
import org.finos.legend.engine.protocol.pure.v1.model.test.TestSuite;
import org.finos.legend.engine.pure.code.core.PureCoreExtensionLoader;
import org.finos.legend.engine.test.runner.mapping.MappingTestRunner;
import org.finos.legend.engine.test.runner.mapping.RichMappingTestResult;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Extension for the Mapping grammar.
 */
public class MappingLSPGrammarExtension extends AbstractLegacyParserLSPGrammarExtension
{
    private static final String RUN_LEGACY_TESTS_COMMAND_ID = "legend.mapping.runLegacyTests";
    private static final String RUN_LEGACY_TESTS_COMMAND_TITLE = "Run legacy tests";

    private static final String RUN_LEGACY_TEST_COMMAND_ID = "legend.mapping.runLegacyTest";
    private static final String RUN_LEGACY_TEST_COMMAND_TITLE = "Run legacy test";
    private static final String LEGACY_TEST_ID = "legend.mapping.legacyTestId";

    private static final ImmutableList<String> STORE_OBJECT_TRIGGERS = Lists.immutable.with("~");

    private static final ImmutableList<String> STORE_OBJECT_SUGGESTIONS = Lists.immutable.with("primaryKey", "mainTable");

    private static final ImmutableList<String> BOILERPLATE_SUGGESTIONS = Lists.immutable.with(
            "Mapping package::path::mappingName\n" +
                    "( /*Mapping contains the business logic relating your (exposed) Class to your underlying store objects (tables/views).*/\n" +
                    "  *package::path::className: Relational\n" +
                    "  {\n" +
                    "    ~primaryKey\n" +
                    "  (\n" +
                    "    [package::path::storeName]schemaName.TableName1.column1\n" +
                    "  )\n" +
                    "    ~mainTable [package::path::storeName]schemaName.TableName1\n" +
                    "    attribute1: [package::path::storeName]schemaName.TableName1.column1,\n" +
                    "    attribute2: [package::path::storeName]schemaName.TableName1.column2,\n" +
                    "    attribute3: multiply([package::path::storeName]schemaName.TableName1.column1, [package::path::storeName]schema.TableName1.column1)\n" +
                    "  }\n" +
                    ")\n");

    private final ListIterable<String> keywords;

    public MappingLSPGrammarExtension()
    {
        super(MappingParser.newInstance(PureGrammarParserExtensions.fromAvailableExtensions()));
        this.keywords = findKeywords();
    }

    @Override
    public Iterable<? extends String> getKeywords()
    {
        return this.keywords;
    }

    @Override
    protected List<? extends TestSuite> getTestSuites(PackageableElement element)
    {
        if (element instanceof Mapping)
        {
            List<MappingTestSuite> testSuites = ((Mapping) element).testSuites;
            return (testSuites == null) ? Lists.fixedSize.empty() : testSuites;
        }
        return super.getTestSuites(element);
    }


    @Override
    protected void collectCommands(SectionState sectionState, PackageableElement element, CommandConsumer consumer)
    {
        super.collectCommands(sectionState, element, consumer);
        if (element instanceof Mapping)
        {
            Mapping mapping = (Mapping) element;
            if ((mapping.tests != null) && !mapping.tests.isEmpty())
            {
                consumer.accept(RUN_LEGACY_TESTS_COMMAND_ID, RUN_LEGACY_TESTS_COMMAND_TITLE, mapping.sourceInformation);
                mapping.tests.forEach(t -> consumer.accept(RUN_LEGACY_TEST_COMMAND_ID, RUN_LEGACY_TEST_COMMAND_TITLE, t.sourceInformation, Collections.singletonMap(LEGACY_TEST_ID, t.name)));
            }
        }
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs)
    {
        switch (commandId)
        {
            case RUN_LEGACY_TESTS_COMMAND_ID:
            {
                return runLegacyMappingTests(section, entityPath, null);
            }
            case RUN_LEGACY_TEST_COMMAND_ID:
            {
                return runLegacyMappingTests(section, entityPath, executableArgs.get(LEGACY_TEST_ID));
            }
            default:
            {
                return super.execute(section, entityPath, commandId, executableArgs);
            }
        }
    }

    private Iterable<? extends LegendExecutionResult> runLegacyMappingTests(SectionState section, String entityPath, String testName)
    {
        PackageableElement element = getParseResult(section).getElement(entityPath);
        if (!(element instanceof Mapping))
        {
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find mapping " + entityPath));
        }
        Mapping mapping = (Mapping) element;
        List<MappingTest_Legacy> tests = getLegacyMappingTests(mapping, testName);
        if (tests.isEmpty())
        {
            String message = (testName == null) ?
                    ("Unable to find legacy tests for mapping " + entityPath) :
                    ("Unable to find legacy test " + testName + " for mapping " + entityPath);
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, message));
        }

        CompileResult compileResult = getCompileResult(section);
        if (compileResult.hasException())
        {
            return Collections.singletonList(errorResult(compileResult.getException(), entityPath));
        }

        PureModel pureModel = compileResult.getPureModel();
        MutableList<? extends Root_meta_pure_extension_Extension> routerExtensions = PureCoreExtensionLoader.extensions().flatCollect(e -> e.extraPureCoreExtensions(pureModel.getExecutionSupport()));
        MutableList<PlanTransformer> planTransformers = Iterate.flatCollect(ServiceLoader.load(PlanGeneratorExtension.class), PlanGeneratorExtension::getExtraPlanTransformers, Lists.mutable.empty());
        PlanExecutor planExecutor = PlanExecutor.newPlanExecutorBuilder().withAvailableStoreExecutors().build();
        MutableList<LegendExecutionResult> results = Lists.mutable.empty();
        tests.forEach(test ->
        {
            try
            {
                MappingTestRunner testRunner = new MappingTestRunner(pureModel, entityPath, test, planExecutor, routerExtensions, planTransformers);
                RichMappingTestResult result = testRunner.setupAndRunTest();
                switch (result.getResult())
                {
                    case SUCCESS:
                    {
                        results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, test.name), Type.SUCCESS, entityPath + "." + result.getTestName() + ": SUCCESS"));
                        break;
                    }
                    case FAILURE:
                    {
                        StringBuilder builder = new StringBuilder(entityPath).append('.').append(result.getTestName()).append(": FAILURE");
                        if (result.getExpected().isPresent() && result.getActual().isPresent())
                        {
                            builder.append("\nexpected: ").append(result.getExpected().get());
                            builder.append("\nactual:   ").append(result.getActual().get());
                        }
                        results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, test.name), Type.FAILURE, builder.toString()));
                        break;
                    }
                    case ERROR:
                    {
                        StringWriter writer = new StringWriter().append(entityPath).append('.').append(result.getTestName()).append(": ERROR");
                        if (result.getException() != null)
                        {
                            try (PrintWriter pw = new PrintWriter(writer.append("\n")))
                            {
                                result.getException().printStackTrace(pw);
                            }
                        }
                        results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, test.name),Type.ERROR, writer.toString()));
                        break;
                    }
                    default:
                    {
                        results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, test.name), Type.SUCCESS, entityPath + "." + result.getTestName() + ": " + result.getResult().name() + " (unhandled result type)"));
                    }
                }
            }
            catch (Exception e)
            {
                results.add(errorResult(e, entityPath));
            }
        });
        return results;
    }

    private List<MappingTest_Legacy> getLegacyMappingTests(Mapping mapping, String testName)
    {
        List<MappingTest_Legacy> tests = mapping.tests;
        if (tests == null)
        {
            return Collections.emptyList();
        }
        if (testName == null)
        {
            return tests;
        }
        return ListIterate.select(tests, t -> testName.equals(t.name));
    }

    private static ListIterable<String> findKeywords()
    {
        MutableSet<String> keywords = Sets.mutable.with("Mapping", "MappingTests", "include");
        PureGrammarParserExtensionLoader.extensions().forEach(ext -> ext.getExtraMappingElementParsers().forEach(p -> keywords.add(p.getElementTypeName())));
        return Lists.immutable.withAll(keywords);
    }

    @Override
    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        String codeLine = section.getSection().getLine(location.getLine()).substring(0, location.getColumn());
        List<LegendCompletion> legendCompletions = Lists.mutable.empty();

        if (codeLine.isEmpty())
        {
            return BOILERPLATE_SUGGESTIONS.collect(s -> new LegendCompletion("Mapping boilerplate", s.replaceAll("\n",System.lineSeparator())));
        }

        if (STORE_OBJECT_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            STORE_OBJECT_SUGGESTIONS.collect(s -> new LegendCompletion("Store object type", s), legendCompletions);
        }

        return legendCompletions;
    }
}
