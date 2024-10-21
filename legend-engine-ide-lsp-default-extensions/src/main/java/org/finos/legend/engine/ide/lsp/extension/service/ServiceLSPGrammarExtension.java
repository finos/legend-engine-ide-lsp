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

package org.finos.legend.engine.ide.lsp.extension.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.block.factory.Functions;
import org.eclipse.collections.impl.lazy.CompositeIterable;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.AbstractSectionParserLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.CommandConsumer;
import org.finos.legend.engine.ide.lsp.extension.CompileResult;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.engine.ide.lsp.extension.SourceInformationUtil;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.core.FunctionExecutionSupport;
import org.finos.legend.engine.ide.lsp.extension.core.PureLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult.Type;
import org.finos.legend.engine.ide.lsp.extension.runtime.RuntimeLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperValueSpecificationBuilder;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.dsl.service.generation.ServicePlanGenerator;
import org.finos.legend.engine.language.pure.dsl.service.grammar.from.ServiceParserExtension;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParserContext;
import org.finos.legend.engine.language.pure.grammar.from.SectionSourceCode;
import org.finos.legend.engine.plan.execution.planHelper.PrimitiveValueSpecificationToObjectVisitor;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.plan.platform.PlanPlatform;
import org.finos.legend.engine.protocol.Protocol;
import org.finos.legend.engine.protocol.pure.PureClientVersions;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.context.AlloySDLC;
import org.finos.legend.engine.protocol.pure.v1.model.context.EngineErrorType;
import org.finos.legend.engine.protocol.pure.v1.model.context.PackageableElementPointer;
import org.finos.legend.engine.protocol.pure.v1.model.context.PackageableElementType;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextPointer;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.SingleExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Multiplicity;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.Runtime;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Execution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.KeyedExecutionParameter;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureMultiExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.PureSingleExecution;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.Service;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.ServiceTest;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.service.ServiceTestSuite;
import org.finos.legend.engine.protocol.pure.v1.model.test.AtomicTest;
import org.finos.legend.engine.protocol.pure.v1.model.test.TestSuite;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.Variable;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Lambda;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.executionContext.ExecutionContext;
import org.finos.legend.engine.pure.code.core.PureCoreExtensionLoader;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.finos.legend.engine.test.runner.service.RichServiceTestResult;
import org.finos.legend.engine.test.runner.service.ServiceTestRunner;
import org.finos.legend.engine.test.runner.shared.TestResult;
import org.finos.legend.pure.generated.Root_meta_legend_service_metamodel_Execution;
import org.finos.legend.pure.generated.Root_meta_legend_service_metamodel_PostValidation;
import org.finos.legend.pure.generated.Root_meta_legend_service_metamodel_PostValidationAssertion;
import org.finos.legend.pure.generated.Root_meta_legend_service_metamodel_PureExecution;
import org.finos.legend.pure.generated.Root_meta_legend_service_metamodel_Service;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

/**
 * Extension for the Service grammar.
 */
public class ServiceLSPGrammarExtension extends AbstractSectionParserLSPGrammarExtension implements FunctionExecutionSupport
{
    private static final List<String> KEYWORDS = List.of("Service", "import");

    static final String RUN_LEGACY_TESTS_COMMAND_ID = "legend.service.runLegacyTests";
    private static final String RUN_LEGACY_TESTS_COMMAND_TITLE = "Run legacy tests";

    static final String REGISTER_SERVICE_COMMAND_ID = "legend.service.registerService";
    private static final String REGISTER_SERVICE_COMMAND_TITLE = "Register service";

    private static final ImmutableList<String> FUNCTIONS_TRIGGERS = Lists.immutable.with("->");

    private static final ImmutableList<String> FUNCTIONS_SUGGESTIONS = Lists.immutable.with(
            "filter(x|",
            "project([ x| $x.attribute1 ],['attribute1'])",
            "groupBy([ x| $x.attribute1 ],[ agg(x|$x.attribute2, x|sum($x)) ])",
            "distinct()",
            "limit(10)");

    private static final ImmutableList<String> BOILERPLATE_SUGGESTIONS = Lists.immutable.with(
            "Service package::path::serviceName\n" +
                    "{\n" +
                    "  pattern: 'uri/to/the/service/{parameter1}/{parameter2}';\n" +
                    "  ownership: DID { identifier: '' }; // old/deprecated grammar: owners: [ 'kerberos1', 'kerberos2' ] \n" +
                    "  documentation: 'This service returns data about foobar. Parameter1 represents ... and can take values ... . Parameter2 represents ... and can take values ... .';\n" +
                    "  execution: Single\n" +
                    "  {\n" +
                    "    query:\n" +
                    "    {\n" +
                    "      parameter1: Date[1], parameter2: String[1] | \n" +
                    "        package::path::className.all()\n" +
                    "        ->filter(x| $x.attribute1 > 12)\n" +
                    "        ->project([ x| $x.attribute1, x|$x.attribute3 ], ['id', 'multipliedValue'])\n" +
                    "        ->filter(x| $x.getFloat('multipliedValue') > 0)\n" +
                    "    mapping: package::path::mappingName;\n" +
                    "    runtime: package::path::runtimeName;\n" +
                    "    };\n" +
                    "  }\n" +
                    "}\n");

    private final JsonMapper resultMapper = PureProtocolObjectMapperFactory.withPureProtocolExtensions(JsonMapper.builder()
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build());

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

    /**
     * This is to compensate for how multi-execution handle test cases, so that every test is split by the execution key
     * This will allow us to better handle and display these parameterized tests in VS Code
     * </p>
     * Ideally, we want to have a sub-suite for the actual "test" to allow users to execute all exec parameters at once,
     * but Engine platform does not support this suite within suite at the moment (suite -> suite (for the test) -> test (per exec param)
     * @param service Service to fix test cases
     * @return list of service test cases, fixed for multi-execution services
     */
    private List<ServiceTestSuite> fixTestSuitesForMultiExecutions(Service service)
    {
        List<ServiceTestSuite> testSuites = service.testSuites;

        if (testSuites != null && service.execution instanceof PureMultiExecution)
        {
            PureMultiExecution multiExecution = (PureMultiExecution) service.execution;
            Optional<List<String>> keys = Optional.ofNullable(multiExecution.executionParameters)
                    .map(ep -> ep.stream().map(x -> x.key).collect(Collectors.toList()));

            List<ServiceTestSuite> newSuites = new ArrayList<>();

            for (ServiceTestSuite suite : testSuites)
            {
                List<AtomicTest> newTests = new ArrayList<>();

                for (AtomicTest atomicTest : suite.tests)
                {
                    ServiceTest test = (ServiceTest) atomicTest;

                    List<String> keysOnTest = Optional.ofNullable(test.parameters)
                            .orElse(List.of())
                            .stream()
                            // find exec keys on the test parameters
                            .filter(parameterValue -> parameterValue.name.equals(multiExecution.executionKey))
                            .findFirst()
                            .map(x -> x.value.accept(new PrimitiveValueSpecificationToObjectVisitor()))
                            .map(x -> x instanceof List ? (List<String>) x : List.of(x.toString()))
                            // or try to use the exec keys directly on the test
                            .or(() -> Optional.ofNullable(test.keys))
                            .filter(Predicate.not(List::isEmpty))
                            // or use the keys on the service definition
                            .or(() -> keys)
                            .orElseThrow(() -> new EngineException("Test '" + suite.id + '.' + test.id + "' on multi-execution service does not have any execution key", test.sourceInformation, EngineErrorType.PARSER));

                    for (String key : keysOnTest)
                    {
                        ServiceTest newTest = this.cloneProtocolObject(test);
                        newTest.id = newTest.id + "[" + key + "]";
                        newTest.keys = List.of(key);
                        newTests.add(newTest);
                    }
                }

                ServiceTestSuite newSuite = this.cloneProtocolObject(suite);
                newSuite.tests = newTests;
                newSuites.add(newSuite);
            }

            testSuites = newSuites;
        }

        return (testSuites == null) ? Lists.fixedSize.empty() : testSuites;
    }

    @Override
    protected void parse(SectionSourceCode section, Consumer<PackageableElement> elementConsumer, PureGrammarParserContext parserContext)
    {
        Consumer<PackageableElement> wrapConsumer = pe ->
        {
            if (pe instanceof Service)
            {
                Service service = (Service) pe;
                service.testSuites = fixTestSuitesForMultiExecutions(service);
            }

            elementConsumer.accept(pe);
        };
        this.parser.parse(section, wrapConsumer, parserContext);
    }

    @Override
    protected void collectCommands(SectionState sectionState, PackageableElement element, CommandConsumer consumer)
    {
        super.collectCommands(sectionState, element, consumer);
        if (element instanceof Service)
        {
            Service service = (Service) element;
            this.collectExecCommand(service, this.getCompileResult(sectionState), consumer);
            if (isEngineServerConfigured())
            {
                consumer.accept(REGISTER_SERVICE_COMMAND_ID, REGISTER_SERVICE_COMMAND_TITLE, service.sourceInformation);
            }
            if (service.test != null)
            {
                consumer.accept(RUN_LEGACY_TESTS_COMMAND_ID, RUN_LEGACY_TESTS_COMMAND_TITLE, service.sourceInformation);
            }
        }
    }

    private void collectExecCommand(Service service, CompileResult compileResult, CommandConsumer consumer)
    {
        FunctionExecutionSupport.collectFunctionExecutionCommand(
                this,
                service,
                compileResult,
                consumer
        );
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs, Map<String, Object> inputParams)
    {
        switch (commandId)
        {
            case RUN_LEGACY_TESTS_COMMAND_ID:
            {
                return runLegacyServiceTest(section, entityPath);
            }
            case REGISTER_SERVICE_COMMAND_ID:
            {
                return registerService(section, entityPath);
            }
            case FunctionExecutionSupport.EXECUTE_COMMAND_ID:
            {
                return FunctionExecutionSupport.executeFunction(this, section, entityPath, inputParams);
            }
            case FunctionExecutionSupport.EXECUTE_QUERY_ID:
            {
                return FunctionExecutionSupport.executeQuery(this, section, entityPath, executableArgs, inputParams);
            }
            case FunctionExecutionSupport.GENERATE_EXECUTION_PLAN_ID:
            {
                return FunctionExecutionSupport.generateExecutionPlan(this, section, entityPath, executableArgs, inputParams);
            }
            case FunctionExecutionSupport.GRAMMAR_TO_JSON_LAMBDA_ID:
            {
                return FunctionExecutionSupport.convertGrammarToJSON_lambda(this, section, entityPath, executableArgs, inputParams);
            }
            case FunctionExecutionSupport.JSON_TO_GRAMMAR_LAMBDA_ID:
            {
                return FunctionExecutionSupport.convertJSONToGrammar_lambda(this, section, entityPath, executableArgs, inputParams);
            }
            case FunctionExecutionSupport.JSON_TO_GRAMMAR_LAMBDA_BATCH_ID:
            {
                return FunctionExecutionSupport.convertJSONToGrammar_lambda_batch(this, section, entityPath, executableArgs, inputParams);
            }
            case FunctionExecutionSupport.GET_LAMBDA_RETURN_TYPE_ID:
            {
                return FunctionExecutionSupport.getLambdaReturnType(this, section, entityPath, executableArgs, inputParams);
            }
            default:
            {
                return super.execute(section, entityPath, commandId, executableArgs);
            }
        }
    }

    @Override
    public AbstractLSPGrammarExtension getExtension()
    {
        return this;
    }

    @Override
    public String getExecutionKey(PackageableElement element, Map<String, Object> args)
    {
        Service service = (Service) element;
        if (service.execution instanceof PureMultiExecution)
        {
            PureMultiExecution multiExecution = (PureMultiExecution) service.execution;
            return Objects.toString(args.get(multiExecution.executionKey));
        }
        else
        {
            return "";
        }
    }

    @Override
    public SingleExecutionPlan getExecutionPlan(PackageableElement element, Lambda function, PureModel pureModel, Map<String, Object> args, String clientVersion)
    {
        PureSingleExecution singleExecution = new PureSingleExecution();
        Service service = (Service) element;
        if (service.execution instanceof PureMultiExecution)
        {
            PureMultiExecution multiExecution = (PureMultiExecution) service.execution;
            Object multiVal = args.get(multiExecution.executionKey);
            KeyedExecutionParameter keyedExecutionParameter = multiExecution.executionParameters.stream().filter(x -> x.key.equals(multiVal)).findFirst().orElseThrow(() -> new IllegalArgumentException("Missing multi execution entry for value: " + multiVal));
            singleExecution.mapping = keyedExecutionParameter.mapping;
            singleExecution.runtime = keyedExecutionParameter.runtime;
            singleExecution.executionOptions = keyedExecutionParameter.executionOptions;
            singleExecution.func = function;
        }
        else
        {
            PureSingleExecution execution = (PureSingleExecution) service.execution;
            singleExecution.mapping = execution.mapping;
            singleExecution.runtime = execution.runtime;
            singleExecution.executionOptions = execution.executionOptions;
            singleExecution.func = function;
        }

        MutableList<? extends Root_meta_pure_extension_Extension> routerExtensions = PureCoreExtensionLoader.extensions().flatCollect(e -> e.extraPureCoreExtensions(pureModel.getExecutionSupport()));
        MutableList<PlanTransformer> planTransformers = Iterate.flatCollect(ServiceLoader.load(PlanGeneratorExtension.class), PlanGeneratorExtension::getExtraPlanTransformers, Lists.mutable.empty());
        return ServicePlanGenerator.generateSingleExecutionPlan(
                singleExecution,
                null,
                pureModel,
                "vX_X_X",
                PlanPlatform.JAVA,
                routerExtensions,
                planTransformers
        );
    }

    @Override
    public SingleExecutionPlan getExecutionPlan(Lambda function, String mappingPath, Runtime runtime, ExecutionContext context, PureModel pureModel, String clientVersion)
    {
        PureSingleExecution singleExecution = new PureSingleExecution();
        singleExecution.mapping = mappingPath;
        singleExecution.runtime = runtime;
        singleExecution.func = function;

        MutableList<? extends Root_meta_pure_extension_Extension> routerExtensions = PureCoreExtensionLoader.extensions().flatCollect(e -> e.extraPureCoreExtensions(pureModel.getExecutionSupport()));
        MutableList<PlanTransformer> planTransformers = Iterate.flatCollect(ServiceLoader.load(PlanGeneratorExtension.class), PlanGeneratorExtension::getExtraPlanTransformers, Lists.mutable.empty());
        return ServicePlanGenerator.generateSingleExecutionPlan(
                singleExecution,
                HelperValueSpecificationBuilder.processExecutionContext(context, pureModel.getContext()),
                pureModel,
                clientVersion,
                PlanPlatform.JAVA,
                routerExtensions,
                planTransformers
        );
    }

    @Override
    public Lambda getLambda(PackageableElement element)
    {
        Service service = (Service) element;
        PureExecution execution = (PureExecution) service.execution;
        return execution.func;
    }

    private List<? extends LegendExecutionResult> runLegacyServiceTest(SectionState section, String entityPath)
    {
        PackageableElement element = getParseResult(section).getElement(entityPath);
        if (!(element instanceof Service))
        {
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find service " + entityPath, null));
        }
        Service service = (Service) element;
        TextLocation location = SourceInformationUtil.toLocation(service.sourceInformation);
        if (service.test == null)
        {
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find legacy test for service " + entityPath, location));
        }

        CompileResult compileResult = getCompileResult(section);
        if (compileResult.hasException())
        {
            return Collections.singletonList(errorResult(compileResult.getCompileErrorResult(), entityPath));
        }

        PureModel pureModel = compileResult.getPureModel();
        MutableList<? extends Root_meta_pure_extension_Extension> routerExtensions = PureCoreExtensionLoader.extensions().flatCollect(e -> e.extraPureCoreExtensions(pureModel.getExecutionSupport()));
        MutableList<PlanTransformer> planTransformers = Iterate.flatCollect(ServiceLoader.load(PlanGeneratorExtension.class), PlanGeneratorExtension::getExtraPlanTransformers, Lists.mutable.empty());
        ServiceTestRunner testRunner = new ServiceTestRunner(service, null, compileResult.getPureModelContextData(), pureModel, null, getPlanExecutor(), routerExtensions, planTransformers, null);

        List<RichServiceTestResult> richServiceTestResults;
        try
        {
            richServiceTestResults = testRunner.executeTests();
        }
        catch (Exception e)
        {
            return Collections.singletonList(errorResult(compileResult.getCompileErrorResult(), entityPath));
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
                    results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, result.name()), toResultType(result), writer.toString(), location));
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

    private Iterable<? extends LegendExecutionResult> registerService(SectionState section, String entityPath)
    {
        try
        {
            Service service = (Service) getParseResult(section).getElement(entityPath);
            TextLocation location = SourceInformationUtil.toLocation(service.sourceInformation);
            Protocol serializer = new Protocol("pure", PureClientVersions.production);
            PureModelContextPointer origin = new PureModelContextPointer();
            origin.serializer = serializer;
            origin.sdlcInfo = new AlloySDLC();
            origin.sdlcInfo.baseVersion = "latest";
            origin.sdlcInfo.version = "none";
            origin.sdlcInfo.packageableElementPointers = Collections.singletonList(new PackageableElementPointer(PackageableElementType.SERVICE, entityPath));
            PureModelContextData pmcd = pureModelContextDataBuilder(section.getDocumentState().getGlobalState())
                    .withSerializer(serializer)
                    .withOrigin(origin)
                    .build();
            String response = postEngineServer("/service/v1/register_fullInteractive", pmcd);
            try
            {
                // Try to parse it as JSON and then format it prettily
                JsonMapper mapper = getResultMapper();
                JsonNode node = mapper.readTree(response);
                String formatted = mapper.writeValueAsString(node);
                return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.SUCCESS, formatted, location));
            }
            catch (Exception ignore)
            {
                // Couldn't format as JSON
                return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.SUCCESS, response, location));
            }
        }
        catch (Exception e)
        {
            return Collections.singletonList(errorResult(e, entityPath));
        }
    }

    private JsonMapper getResultMapper()
    {
        return this.resultMapper;
    }

    @Override
    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        String codeLine = section.getSection().getLineUpTo(location);
        List<LegendCompletion> legendCompletions = Lists.mutable.empty();

        if (codeLine.isEmpty())
        {
            BOILERPLATE_SUGGESTIONS.collect(s -> new LegendCompletion("Service boilerplate", s.replaceAll("\n", System.lineSeparator())), legendCompletions);
        }

        if (FUNCTIONS_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            FUNCTIONS_SUGGESTIONS.collect(s -> new LegendCompletion("Function evaluation", s), legendCompletions);
        }

        return CompositeIterable.with(legendCompletions, (Iterable<LegendCompletion>) super.getCompletions(section, location));
    }

    @Override
    protected Stream<Optional<LegendReferenceResolver>> getReferenceResolvers(SectionState section, PackageableElement packageableElement, Optional<CoreInstance> coreInstance)
    {
        if (!(packageableElement instanceof Service))
        {
            return Stream.empty();
        }

        Service service = (Service) packageableElement;
        Stream<Optional<LegendReferenceResolver>> executionReferences = this.getExecutionReferences(service.execution, section.getDocumentState().getGlobalState());
        Stream<Optional<LegendReferenceResolver>> stereoTypeReferences = PureLSPGrammarExtension.toStereotypeReferences(service.stereotypes);
        Stream<Optional<LegendReferenceResolver>> taggedValueReferences = PureLSPGrammarExtension.toTaggedValueReferences(service.taggedValues);
        Stream<Optional<LegendReferenceResolver>> coreReferences = Stream.empty();
        if (coreInstance.isPresent())
        {
            coreReferences = toReferences((Root_meta_legend_service_metamodel_Service) coreInstance.get());
        }
        return Stream.of(executionReferences, stereoTypeReferences, taggedValueReferences, coreReferences)
                .flatMap(Functions.identity());
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(Root_meta_legend_service_metamodel_Service service)
    {
        Stream<Optional<LegendReferenceResolver>> executionReferences = toReferences(service._execution());
        Stream<Optional<LegendReferenceResolver>> postValidationReferences = StreamSupport.stream(service._postValidations().spliterator(), false)
                .flatMap(this::toReferences);
        return Stream.concat(executionReferences, postValidationReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(Root_meta_legend_service_metamodel_Execution execution)
    {
        if (execution instanceof Root_meta_legend_service_metamodel_PureExecution)
        {
            Root_meta_legend_service_metamodel_PureExecution pureExecution = (Root_meta_legend_service_metamodel_PureExecution) execution;
            return FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(pureExecution._func()));
        }
        return Stream.empty();
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(Root_meta_legend_service_metamodel_PostValidation postValidation)
    {
        if (postValidation == null)
        {
            return Stream.empty();
        }
        RichIterable<? extends CoreInstance> parameters = postValidation._parameters();
        Stream<Optional<LegendReferenceResolver>> parameterReferences = StreamSupport.stream(parameters.spliterator(), false)
                .flatMap(parameter -> FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(parameter)));
        RichIterable<? extends Root_meta_legend_service_metamodel_PostValidationAssertion> assertions = postValidation._assertions();
        Stream<Optional<LegendReferenceResolver>> assertionReferences = StreamSupport.stream(assertions.spliterator(), false)
                .flatMap(this::toReferences);
        return Stream.concat(parameterReferences, assertionReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(Root_meta_legend_service_metamodel_PostValidationAssertion postValidationAssertion)
    {
        if (postValidationAssertion == null)
        {
            return Stream.empty();
        }
        return FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(postValidationAssertion._assertion()));
    }

    private Stream<Optional<LegendReferenceResolver>> getExecutionReferences(Execution execution, GlobalState state)
    {
        if (execution instanceof PureSingleExecution)
        {
            return this.toPureSingleExecutionReferences((PureSingleExecution) execution, state);
        }

        if (execution instanceof PureMultiExecution)
        {
            return this.toPureMultiExecutionReferences((PureMultiExecution) execution, state);
        }

        return Stream.empty();
    }

    private Stream<Optional<LegendReferenceResolver>> toPureSingleExecutionReferences(PureSingleExecution pureSingleExecution, GlobalState state)
    {
        Optional<LegendReferenceResolver> mappingReference = LegendReferenceResolver.newReferenceResolver(
                pureSingleExecution.mappingSourceInformation,
                x -> x.resolveMapping(pureSingleExecution.mapping, pureSingleExecution.mappingSourceInformation));
        Stream<Optional<LegendReferenceResolver>> runtimeReferences = this.toRuntimeReferences(pureSingleExecution.runtime, state);
        return Stream.concat(Stream.of(mappingReference), runtimeReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toPureMultiExecutionReferences(PureMultiExecution pureMultiExecution, GlobalState state)
    {
        return pureMultiExecution.executionParameters
                .stream()
                .flatMap(executionParameter ->
                {
                    Optional<LegendReferenceResolver> mappingReference = LegendReferenceResolver.newReferenceResolver(
                            executionParameter.mappingSourceInformation,
                            x -> x.resolveMapping(executionParameter.mapping, executionParameter.mappingSourceInformation)
                    );
                    Stream<Optional<LegendReferenceResolver>> runtimeReferences = this.toRuntimeReferences(executionParameter.runtime, state);
                    return Stream.concat(Stream.of(mappingReference), runtimeReferences);
                });
    }

    private Stream<Optional<LegendReferenceResolver>> toRuntimeReferences(Runtime runtime, GlobalState state)
    {
        return state.findGrammarExtensionThatImplements(RuntimeLSPGrammarExtension.class)
                .flatMap(x -> x.getRuntimeReferences(runtime, state));
    }

    @Override
    public List<Variable> getParameters(PackageableElement element)
    {
        Service service = (Service) element;
        PureExecution execution = (PureExecution) service.execution;

        if (execution instanceof PureMultiExecution)
        {
            PureMultiExecution multiExecution = (PureMultiExecution) execution;
            List<Variable> variables = new ArrayList<>(execution.func.parameters);
            variables.add(0, new Variable(multiExecution.executionKey, "String", Multiplicity.PURE_ONE));
            return variables;
        }
        else
        {
            return execution.func.parameters;
        }
    }
}
