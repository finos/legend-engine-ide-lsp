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
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult.Type;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionSource.SourceType;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.service.grammar.from.ServiceParserExtension;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.ServiceTestSuite;
import org.finos.legend.engine.protocol.pure.v1.model.test.TestSuite;
import org.finos.legend.engine.pure.code.core.PureCoreExtensionLoader;
import org.finos.legend.engine.test.runner.service.RichServiceTestResult;
import org.finos.legend.engine.test.runner.service.ServiceTestRunner;
import org.finos.legend.engine.test.runner.shared.TestResult;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Extension for the Service grammar.
 */
public class ServiceLSPGrammarExtension extends AbstractSectionParserLSPGrammarExtension
{
    private static final List<String> KEYWORDS = List.of("Service", "import");

    private static final String RUN_LEGACY_TESTS_COMMAND_ID = "legend.service.runLegacyTests";
    private static final String RUN_LEGACY_TESTS_COMMAND_TITLE = "Run legacy tests";

    private static final ImmutableList<String> FUNCTIONS_TRIGGERS = Lists.immutable.with("->");

    private static final ImmutableList<String> FUNCTIONS_SUGGESTIONS = Lists.immutable.with(
            "filter(x|",
            "project([ x| $x.attribute1 ],['attribute1'])",
            "groupBy([ x| $x.attribute1 ],[ agg(x|$x.attribute2, x|sum($x)) ])",
            "distinct()",
            "limit(10)",
            "abs()",
            "add(",
            "adjust(",
            "and(",
            "assert(",
            "at(",
            "average(",
            "cast(",
            "ceiling(",
            "chunk(",
            "compare(",
            "concatenate(",
            "contains(",
            "convert(",
            "cos(",
            "count(",
            "date(",
            "dateDiff(",
            "datePart(",
            "dayOfMonth(",
            "dayOfWeekNumber(",
            "distinct(",
            "divide(",
            "drop(",
            "endsWith(",
            "enumValues(",
            "eq(",
            "equal(",
            "exists(",
            "extractEnumValue(",
            "filter(",
            "first(",
            "firstDayOfMonth(",
            "firstDayOfQuarter(",
            "firstDayOfThisMonth(",
            "firstDayOfThisQuarter(",
            "firstDayOfThisYear(",
            "firstDayOfWeek(",
            "firstDayOfYear(",
            "floor(",
            "fold(",
            "forAll(",
            "format(",
            "generateGuid(",
            "greaterThan(",
            "greaterThanEqual(",
            "hasDay(",
            "hasHour(",
            "hasMinute(",
            "hasMonth(",
            "hasSecond(",
            "hasSubsecond(",
            "hasSubsecondWithAtLeastPrecision(",
            "hasYear(",
            "hour(",
            "id(",
            "if(",
            "in(",
            "indexOf(",
            "init(",
            "instanceOf(",
            "is(",
            "isDistinct(",
            "isEmpty(",
            "isEqual(",
            "isNotEmpty(",
            "joinStrings(",
            "last(",
            "length(",
            "lessThan(",
            "lessThanEqual(",
            "letFunction(",
            "limit(",
            "map(",
            "match(",
            "matches(",
            "max(",
            "min(",
            "minus(",
            "minute(",
            "monthNumber(",
            "mostRecentDayOfWeek(",
            "new(",
            "newUnit(",
            "not(",
            "now(",
            "or(",
            "orElse(",
            "parseBoolean(",
            "parseDate(",
            "parseDecimal(",
            "parseFloat(",
            "parseInteger(",
            "plus(",
            "previousDayOfWeek(",
            "quarterNumber(",
            "range(",
            "rem(",
            "removeDuplicates(",
            "removeDuplicatesBy(",
            "replace(",
            "reverse(",
            "round(",
            "second(",
            "sin(",
            "size(",
            "slice(",
            "sort(",
            "sortBy(",
            "split(",
            "startsWith(",
            "substring(",
            "tail(",
            "take(",
            "times(",
            "toDecimal(",
            "toFloat(",
            "toLower(",
            "toOne(",
            "toOneMany(",
            "toRepresentation(",
            "toString(",
            "toUpper(",
            "toUpperFirstCharacter(",
            "today(",
            "trim(",
            "union(",
            "unitType(",
            "unitValue(",
            "weekOfYear(",
            "whenSubType(",
            "year()"
    );

    private static final ImmutableList<String> BOILERPLATE_SUGGESTIONS = Lists.immutable.with(
            "Service package::path::serviceName\n" +
                    "{\n" +
                    "  pattern: 'uri/to/the/service/{parameter1}/{parameter2}';\n" +
                    "  owners: ['kerberos1', 'kerberos2']; // at least two active workers\n" +
                    "  documentation: 'This service returns data about foobar. Parameter1 represents ... and can take values ... . Parameter2 represents ... and can take values ... .';\n" +
                    "  execution: Single\n" +
                    "  {\n" +
                    "    query:" +
                    "    {\n" +
                    "      parameter1: Date[1], parameter2: String[1] | \n" +
                    "        package::path::className.all()\n" +
                    "        ->filter(x| $x.attribute1 > 12)\n" +
                    "        ->project([ x| $x.attribute1, x|$x.attribute3 ], ['id', 'multipliedValue'])\n" +
                    "        ->filter(x| $x.getFloat('multipliedValue') > 0)\n" +
                    "    };\n" +
                    "    package::path::mappingName;\n" +
                    "    runtime: package::path::runtimeName;\n" +
                    "  }\n" +
                    "}\n");

    public ServiceLSPGrammarExtension()
    {
        super(ServiceParserExtension.NAME, new ServiceParserExtension());
    }

    @Override
    public Iterable<? extends String> getKeywords()
    {
        return KEYWORDS;
    }

    @Override
    protected List<? extends TestSuite> getTestSuites(PackageableElement element)
    {
        if (element instanceof Service)
        {
            List<ServiceTestSuite> testSuites = ((Service) element).testSuites;
            return (testSuites == null) ? Lists.fixedSize.empty() : testSuites;
        }
        return super.getTestSuites(element);
    }

    @Override
    protected void collectCommands(SectionState sectionState, PackageableElement element, CommandConsumer consumer)
    {
        super.collectCommands(sectionState, element, consumer);
        if (element instanceof Service)
        {
            Service service = (Service) element;
            if (service.test != null)
            {
                consumer.accept(RUN_LEGACY_TESTS_COMMAND_ID, RUN_LEGACY_TESTS_COMMAND_TITLE, service.sourceInformation);
            }
        }
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs)
    {
        return RUN_LEGACY_TESTS_COMMAND_ID.equals(commandId) ?
                runLegacyServiceTest(section, entityPath) :
                super.execute(section, entityPath, commandId, executableArgs);
    }

    private Iterable<? extends LegendExecutionResult> runLegacyServiceTest(SectionState section, String entityPath)
    {
        PackageableElement element = getParseResult(section).getElement(entityPath);
        if (!(element instanceof Service))
        {
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, SourceType.TEST, Type.ERROR, "Unable to find service " + entityPath));
        }
        Service service = (Service) element;
        if (service.test == null)
        {
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, SourceType.TEST, Type.ERROR, "Unable to find legacy test for service " + entityPath));
        }

        CompileResult compileResult = getCompileResult(section);
        if (compileResult.hasException())
        {
            return Collections.singletonList(errorResult(compileResult.getException(), entityPath, SourceType.TEST));
        }

        PureModel pureModel = compileResult.getPureModel();
        MutableList<? extends Root_meta_pure_extension_Extension> routerExtensions = PureCoreExtensionLoader.extensions().flatCollect(e -> e.extraPureCoreExtensions(pureModel.getExecutionSupport()));
        MutableList<PlanTransformer> planTransformers = Iterate.flatCollect(ServiceLoader.load(PlanGeneratorExtension.class), PlanGeneratorExtension::getExtraPlanTransformers, Lists.mutable.empty());
        ServiceTestRunner testRunner = new ServiceTestRunner(service, null, compileResult.getPureModelContextData(), pureModel, null, PlanExecutor.newPlanExecutorBuilder().withAvailableStoreExecutors().build(), routerExtensions, planTransformers, null);

        List<RichServiceTestResult> richServiceTestResults;
        try
        {
            richServiceTestResults = testRunner.executeTests();
        }
        catch (Exception e)
        {
            return Collections.singletonList(errorResult(compileResult.getException(), entityPath, SourceType.TEST));
        }

        MutableList<LegendExecutionResult> results = Lists.mutable.empty();
        richServiceTestResults.forEach(run ->
        {
            Map<String, TestResult> runResults = run.getResults();
            Map<String, Exception> runExceptions = run.getAssertExceptions();
            if (runResults != null)
            {
                runResults.forEach((key, result) ->
                {
                    StringWriter writer = new StringWriter().append(entityPath).append('.').append(key).append(": ").append(result.name());
                    Exception e = runExceptions.get(key);
                    if (e != null)
                    {
                        try (PrintWriter pw = new PrintWriter(writer.append("\n")))
                        {
                            e.printStackTrace(pw);
                        }
                    }
                    results.add(LegendExecutionResult.newResult(entityPath, SourceType.TEST, toResultType(result), writer.toString()));
                });
            }
        });
        return results;
    }

    private Type toResultType(TestResult testResult)
    {
        switch (testResult)
        {
            case SUCCESS:
            {
                return Type.SUCCESS;
            }
            case FAILURE:
            {
                return Type.FAILURE;
            }
            case ERROR:
            {
                return Type.ERROR;
            }
            default:
            {
                return Type.WARNING;
            }
        }
    }

    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        String codeLine = section.getSection().getLine(location.getLine()).substring(0, location.getColumn());
        List<LegendCompletion> legendCompletions = new ArrayList<>();

        if (codeLine.isEmpty())
        {
            return BOILERPLATE_SUGGESTIONS.collect(s -> new LegendCompletion("Service boilerplate", s));
        }

        if (FUNCTIONS_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            FUNCTIONS_SUGGESTIONS.collect(s -> new LegendCompletion("Join definition", s), legendCompletions);
        }

        return legendCompletions;
    }
}
