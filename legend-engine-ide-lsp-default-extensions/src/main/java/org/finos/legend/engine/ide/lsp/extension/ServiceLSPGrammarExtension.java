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
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult.Type;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private static final String REGISTER_SERVICE_COMMAND_ID = "legend.service.registerService";
    private static final String REGISTER_SERVICE_COMMAND_TITLE = "Register service";

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
            consumer.accept(REGISTER_SERVICE_COMMAND_ID, REGISTER_SERVICE_COMMAND_TITLE, service.sourceInformation);
            if (service.test != null)
            {
                consumer.accept(RUN_LEGACY_TESTS_COMMAND_ID, RUN_LEGACY_TESTS_COMMAND_TITLE, service.sourceInformation);
            }
        }
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs)
    {
        switch (commandId)
        {
            case RUN_LEGACY_TESTS_COMMAND_ID:
                return runLegacyServiceTest(section, entityPath);
            case REGISTER_SERVICE_COMMAND_ID:
                return List.of(registerService(section));
            default:
                return super.execute(section, entityPath, commandId, executableArgs);

        }
    }


    private HttpResponse post(String uri, String data) throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .POST(HttpRequest.BodyPublishers.ofString(data))
                .build();
        HttpResponse<?> response = HttpClient.newBuilder().build().send(request, HttpResponse.BodyHandlers.discarding());
        return response;
    }

    private LegendExecutionResult registerService(SectionState section)
    {
        String registerServiceUrl = "https://exec.alloy.site.gs.com/api/service/v1/register_fullInteractive?storeModel=false&generateLineage=true";
        String payload = getCompileResult(section).getPureModelContextData().toString();

        try
        {
            HttpResponse response = post(registerServiceUrl, payload);
            return (LegendExecutionResult.newResult(Type.WARNING,response.toString()));
        }
        catch (Exception e)
        {
            return (LegendExecutionResult.newResult(Type.ERROR,"Service not registered, payload: " + payload));
        }
    }

    private Iterable<? extends LegendExecutionResult> runLegacyServiceTest(SectionState section, String entityPath)
    {
        PackageableElement element = getParseResult(section).getElement(entityPath);
        if (!(element instanceof Service))
        {
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find service " + entityPath));
        }
        Service service = (Service) element;
        if (service.test == null)
        {
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find legacy test for service " + entityPath));
        }

        CompileResult compileResult = getCompileResult(section);
        if (compileResult.hasException())
        {
            return Collections.singletonList(errorResult(compileResult.getException(), entityPath));
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
            return Collections.singletonList(errorResult(compileResult.getException(), entityPath));
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
                    results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, result.name()), toResultType(result), writer.toString()));
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
}
