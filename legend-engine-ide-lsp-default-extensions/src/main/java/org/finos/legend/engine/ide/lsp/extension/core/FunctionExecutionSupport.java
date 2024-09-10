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

package org.finos.legend.engine.ide.lsp.extension.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.security.auth.Subject;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.CommandConsumer;
import org.finos.legend.engine.ide.lsp.extension.CompileResult;
import org.finos.legend.engine.ide.lsp.extension.Constants;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommandType;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendInputParameter;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.execution.PlanExecutionContext;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.plan.execution.nodes.helpers.ExecuteNodeParameterTransformationHelper;
import org.finos.legend.engine.plan.execution.result.ConstantResult;
import org.finos.legend.engine.plan.execution.result.ErrorResult;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.plan.execution.result.StreamingResult;
import org.finos.legend.engine.plan.execution.result.serialization.SerializationFormat;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.ExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.SingleExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Enumeration;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Multiplicity;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.Variable;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Lambda;
import org.finos.legend.engine.shared.core.identity.Identity;
import org.finos.legend.engine.shared.core.kerberos.SubjectTools;
import org.finos.legend.engine.shared.javaCompiler.JavaCompileException;
import org.finos.legend.pure.m4.coreinstance.primitive.date.PureDate;
import org.finos.legend.pure.m4.coreinstance.primitive.strictTime.PureStrictTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface FunctionExecutionSupport
{
    Logger LOGGER = LoggerFactory.getLogger(FunctionExecutionSupport.class);

    String EXECUTE_COMMAND_ID = "legend.function.execute";
    String EXECUTE_COMMAND_TITLE = "Execute";

    AbstractLSPGrammarExtension getExtension();

    Lambda getLambda(PackageableElement element);

    @Deprecated
    default String getExecutionKey(PackageableElement element, Map<String, Object> args)
    {
        return "";
    }

    SingleExecutionPlan getExecutionPlan(PackageableElement element, Lambda lambda, PureModel pureModel, Map<String, Object> args, String version);

    static void collectFunctionExecutionCommand(FunctionExecutionSupport executionSupport, PackageableElement element, CompileResult compileResult, CommandConsumer consumer)
    {
        List<PackageableElement> elements = compileResult.getPureModelContextData().getElements();
        Map<String, LegendInputParameter> parameters = Maps.mutable.empty();
        List<Variable> funcParameters = executionSupport.getParameters(element);
        if (funcParameters != null && !funcParameters.isEmpty())
        {
            funcParameters.forEach(p ->
            {
                PackageableElement paramElement = elements.stream().filter(e -> e.getPath().equals(p._class)).findFirst().orElse(null);
                if (paramElement instanceof Enumeration)
                {
                    parameters.put(p.name, LegendFunctionInputParameter.newFunctionParameter(p, paramElement));
                }
                else
                {
                    parameters.put(p.name, LegendFunctionInputParameter.newFunctionParameter(p));
                }
            });
        }
        consumer.accept(EXECUTE_COMMAND_ID, EXECUTE_COMMAND_TITLE, element.sourceInformation, Collections.emptyMap(), parameters, LegendCommandType.CLIENT);
    }

    List<Variable> getParameters(PackageableElement element);

    static Iterable<? extends LegendExecutionResult> executeFunction(FunctionExecutionSupport executionSupport, SectionState section, String entityPath, Map<String, Object> inputParameters)
    {
        AbstractLSPGrammarExtension extension = executionSupport.getExtension();

        CompileResult compileResult = extension.getCompileResult(section);
        if (compileResult.hasException())
        {
            return Collections.singletonList(extension.errorResult(compileResult.getCompileErrorResult(), entityPath));
        }

        MutableList<LegendExecutionResult> results = Lists.mutable.empty();
        try
        {
            PackageableElement element = compileResult.getPureModelContextData().getElements().stream().filter(x -> x.getPath().equals(entityPath)).findFirst().orElseThrow(() -> new IllegalArgumentException("Element " + entityPath + " not found"));
            GlobalState globalState = section.getDocumentState().getGlobalState();
            String executionKey = executionSupport.getExecutionKey(element, inputParameters);

            Pair<SingleExecutionPlan, PlanExecutionContext> executionPlanAndContext = globalState.getProperty(EXECUTE_COMMAND_ID + ":" + entityPath + ":" + executionKey, () ->
            {
                Lambda lambda = executionSupport.getLambda(element);
                PureModel pureModel = compileResult.getPureModel();
                SingleExecutionPlan executionPlan = executionSupport.getExecutionPlan(element, lambda, pureModel, inputParameters, globalState.getSetting(Constants.LEGEND_PROTOCOL_VERSION));
                PlanExecutionContext planExecutionContext = null;
                try
                {
                    planExecutionContext = new PlanExecutionContext(executionPlan, List.of());
                }
                catch (JavaCompileException e)
                {
                    LOGGER.warn("Failed to compile plan");
                }

                return Tuples.pair(executionPlan, planExecutionContext);
            });
            executePlan(globalState, executionSupport, section.getDocumentState().getDocumentId(), section.getSectionNumber(), executionPlanAndContext.getOne(), executionPlanAndContext.getTwo(), entityPath, inputParameters, results);
        }
        catch (Exception e)
        {
            results.add(extension.errorResult(e, entityPath));
        }
        return results;
    }

    static void executePlan(GlobalState globalState, FunctionExecutionSupport executionSupport, String docId, int sectionNum, SingleExecutionPlan executionPlan, PlanExecutionContext context, String entityPath, Map<String, Object> inputParameters, MutableList<LegendExecutionResult> results)
    {
        AbstractLSPGrammarExtension extension = executionSupport.getExtension();

        try
        {
            if (extension.isEngineServerConfigured()
                    && Boolean.parseBoolean(globalState.getSetting(Constants.LEGEND_ENGINE_SERVER_REMOTE_EXECUTION)))
            {
                ExecutionRequest executionRequest = new ExecutionRequest(executionPlan, inputParameters);
                LegendExecutionResult legendExecutionResult = extension.postEngineServer("/executionPlan/v1/execution/executeRequest?serializationFormat=DEFAULT", executionRequest, is ->
                {
                    ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
                    is.transferTo(os);
                    return FunctionLegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.SUCCESS, os.toString(StandardCharsets.UTF_8), "Executed using remote engine server", docId, sectionNum, inputParameters);
                });
                results.add(legendExecutionResult);
            }
            else
            {
                PlanExecutor planExecutor = extension.getPlanExecutor();
                MutableMap<String, Result> parametersToConstantResult = Maps.mutable.empty();
                ExecuteNodeParameterTransformationHelper.buildParameterToConstantResult(executionPlan, inputParameters, parametersToConstantResult);
                Identity identity = getIdentity();
                Result result = planExecutor.execute(executionPlan, parametersToConstantResult, identity.getName(), identity, context);
                collectResults(executionSupport, entityPath, result, docId, sectionNum, inputParameters, results::add);
            }
        }
        catch (Exception e)
        {
            results.add(extension.errorResult(e, entityPath));
        }
    }

    private static Identity getIdentity()
    {
        Subject subject = null;
        try
        {
            subject = SubjectTools.getLocalSubject();
        }
        catch (Exception e)
        {
            LOGGER.warn("Unable to get local subject", e);
        }
        return Optional.ofNullable(subject).map(Identity::makeIdentity).orElseGet(Identity::getAnonymousIdentity);
    }

    private static void collectResults(FunctionExecutionSupport executionSupport, String entityPath, org.finos.legend.engine.plan.execution.result.Result result, String docId, int secNum, Map<String, Object> inputParameters, Consumer<? super LegendExecutionResult> consumer)
    {
        AbstractLSPGrammarExtension extension = executionSupport.getExtension();

        // TODO also collect results from activities
        if (result instanceof ErrorResult)
        {
            ErrorResult errorResult = (ErrorResult) result;
            consumer.accept(FunctionLegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.ERROR, errorResult.getMessage(), errorResult.getTrace(), docId, secNum, inputParameters));
            return;
        }
        if (result instanceof ConstantResult)
        {
            consumer.accept(FunctionLegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.SUCCESS, getConstantResult((ConstantResult) result), null, docId, secNum, inputParameters));
            return;
        }
        if (result instanceof StreamingResult)
        {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
            try
            {
                ((StreamingResult) result).getSerializer(SerializationFormat.DEFAULT).stream(byteStream);
            }
            catch (IOException e)
            {
                consumer.accept(extension.errorResult(e, entityPath));
                return;
            }
            consumer.accept(FunctionLegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.SUCCESS, byteStream.toString(StandardCharsets.UTF_8), null, docId, secNum, inputParameters));
            return;
        }
        consumer.accept(FunctionLegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.WARNING, "Unhandled result type: " + result.getClass().getName(), null, docId, secNum, inputParameters));
    }

    private static String getConstantResult(ConstantResult constantResult)
    {
        return getConstantValueResult(constantResult.getValue());
    }

    JsonMapper functionResultMapper = PureProtocolObjectMapperFactory.withPureProtocolExtensions(JsonMapper.builder()
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build());

    private static String getConstantValueResult(Object value)
    {
        if (value == null)
        {
            return "[]";
        }
        if (value instanceof Iterable)
        {
            StringBuilder builder = new StringBuilder();
            ((Iterable<?>) value).forEach(v -> builder.append((builder.length() == 0) ? "[" : ", ").append(getConstantValueResult(v)));
            return builder.append("]").toString();
        }
        if ((value instanceof String) || (value instanceof Boolean) || (value instanceof Number) || (value instanceof PureDate) || (value instanceof PureStrictTime) || (value instanceof TemporalAccessor))
        {
            return value.toString();
        }
        try
        {
            return functionResultMapper.writeValueAsString(value);
        }
        catch (Exception e)
        {
            LOGGER.error("Error converting value to JSON", e);
        }
        return value.toString();
    }

    class FunctionLegendExecutionResult extends LegendExecutionResult
    {
        private final String uri;
        private final int sectionNum;
        private final Map<String, Object> inputParameters;

        public FunctionLegendExecutionResult(List<String> ids, Type type, String message, String logMessage, String uri, int sectionNum, Map<String, Object> inputParameters)
        {
            super(ids, type, message, logMessage, null);
            this.uri = uri;
            this.sectionNum = sectionNum;
            this.inputParameters = inputParameters;
        }

        public String getUri()
        {
            return uri;
        }

        public int getSectionNum()
        {
            return sectionNum;
        }

        public Map<String, Object> getInputParameters()
        {
            return inputParameters;
        }

        public static FunctionLegendExecutionResult newResult(String id, Type type, String message, String logMessage, String uri, int sectionNum, Map<String, Object> inputParameters)
        {
            return new FunctionLegendExecutionResult(Collections.singletonList(id), type, message, logMessage, uri, sectionNum, inputParameters);
        }
    }

    class LegendFunctionInputParameter extends LegendInputParameter
    {
        private final LegendVariable variable;
        private final PackageableElement element;

        private LegendFunctionInputParameter(Variable variable, PackageableElement element)
        {
            this.variable = LegendVariable.create(variable);
            this.element = element;
        }

        public LegendVariable getVariable()
        {
            return this.variable;
        }

        public PackageableElement getElement()
        {
            return this.element;
        }

        public static LegendFunctionInputParameter newFunctionParameter(Variable variable)
        {
            return newFunctionParameter(variable, null);
        }

        public static LegendFunctionInputParameter newFunctionParameter(Variable variable, PackageableElement element)
        {
            return new LegendFunctionInputParameter(variable, element);
        }
    }

    class LegendVariable
    {
        private final String name;
        private final Multiplicity multiplicity;
        private final String _class;

        private LegendVariable(String name, Multiplicity multiplicity, String _class)
        {
            this.name = name;
            this.multiplicity = multiplicity;
            this._class = _class;
        }

        public static LegendVariable create(Variable variable)
        {
            return new LegendVariable(variable.name, variable.multiplicity, variable._class.path);
        }

        public String getName()
        {
            return name;
        }

        public Multiplicity getMultiplicity()
        {
            return multiplicity;
        }

        public String get_class()
        {
            return _class;
        }
    }

    class ExecutionRequest
    {
        private final ExecutionPlan executionPlan;
        private final Map<String, Object> executionParameters;

        public ExecutionRequest(ExecutionPlan executionPlan, Map<String, Object> executionParameters)
        {
            this.executionPlan = executionPlan;
            this.executionParameters = executionParameters == null ? Collections.emptyMap() : executionParameters;
        }

        public ExecutionPlan getExecutionPlan()
        {
            return this.executionPlan;
        }

        public Map<String, Object> getExecutionParameters()
        {
            return this.executionParameters;
        }
    }
}
