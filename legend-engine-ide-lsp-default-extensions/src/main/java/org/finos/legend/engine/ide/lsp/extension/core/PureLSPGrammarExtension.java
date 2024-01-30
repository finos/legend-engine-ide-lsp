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
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.lazy.CompositeIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.ide.lsp.extension.AbstractLegacyParserLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.SourceInformationUtil;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult.Type;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendInputParamter;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.grammar.from.domain.DomainParser;
import org.finos.legend.engine.plan.execution.PlanExecutor;
import org.finos.legend.engine.plan.execution.nodes.helpers.ExecuteNodeParameterTransformationHelper;
import org.finos.legend.engine.plan.execution.result.ConstantResult;
import org.finos.legend.engine.plan.execution.result.ErrorResult;
import org.finos.legend.engine.plan.execution.result.StreamingResult;
import org.finos.legend.engine.plan.execution.result.serialization.SerializationFormat;
import org.finos.legend.engine.plan.generation.PlanGenerator;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.plan.platform.PlanPlatform;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.SingleExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Association;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Enumeration;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Function;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Multiplicity;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Property;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.QualifiedProperty;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.Variable;
import org.finos.legend.engine.pure.code.core.PureCoreExtensionLoader;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.ConcreteFunctionDefinition;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m4.coreinstance.primitive.date.PureDate;
import org.finos.legend.pure.m4.coreinstance.primitive.strictTime.PureStrictTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension for the Pure grammar.
 */
public class PureLSPGrammarExtension extends AbstractLegacyParserLSPGrammarExtension
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PureLSPGrammarExtension.class);

    private static final Set<String> SUGGESTABLE_KEYWORDS = Set.of(
            "Association",
            "Class",
            "Enum",
            "function",
            "import",
            "Profile"
    );

    private static final List<String> KEYWORDS = List.copyOf(PrimitiveUtilities.getPrimitiveTypeNames().toSet()
            .withAll(SUGGESTABLE_KEYWORDS)
            .with("let")
            .with("native function")
    );

    private static final ImmutableList<String> ATTRIBUTE_TYPES = PrimitiveUtilities.getPrimitiveTypeNames().collect(n -> n + " ", Lists.mutable.empty()).toImmutable();
    private static final ImmutableList<String> ATTRIBUTE_TYPES_TRIGGERS = Lists.immutable.with(": ");
    private static final ImmutableList<String> ATTRIBUTE_TYPES_SUGGESTIONS = ATTRIBUTE_TYPES;
    private static final ImmutableList<String> ATTRIBUTE_MULTIPLICITIES_TRIGGERS = ATTRIBUTE_TYPES;
    private static final ImmutableList<String> ATTRIBUTE_MULTIPLICITIES_SUGGESTIONS = Lists.immutable.with("[0..1];\n", "[1];\n", "[1..*];\n", "[*];\n");
    private static final ImmutableList<String> BOILERPLATE_SUGGESTIONS = Lists.immutable.with(
            "function go() : Any[*]\n" +
                    "{\n" +
                    "   1+1;\n" +
                    "}\n",
            "Class package::path::className\n" +
                    "{\n" +
                    "   attributeName: attributeType [attributeMultiplicity];\n" +
                    "}\n");

    private static final String EXEC_FUNCTION_ID = "legend.pure.executeFunction";
    private static final String EXEC_FUNCTION_WITH_PARAMETERS_ID = "legend.pure.executeFunctionWithParameters";
    private static final String EXEC_FUNCTION_TITLE = "Execute function";

    private final JsonMapper functionResultMapper = PureProtocolObjectMapperFactory.withPureProtocolExtensions(JsonMapper.builder()
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build());

    public PureLSPGrammarExtension()
    {
        super(new DomainParser());
    }

    @Override
    public Iterable<? extends String> getKeywords()
    {
        return KEYWORDS;
    }

    @Override
    protected void collectCommands(SectionState sectionState, PackageableElement element, CommandConsumer consumer)
    {
        super.collectCommands(sectionState, element, consumer);
        if (element instanceof Function)
        {
            Function function = (Function) element;
            if ((function.parameters == null) || function.parameters.isEmpty())
            {
                consumer.accept(EXEC_FUNCTION_ID, EXEC_FUNCTION_TITLE, function.sourceInformation);
            }
            else
            {
                Map<String, LegendInputParamter> parameters = Maps.mutable.empty();
                function.parameters.forEach(p ->
                {
                    CompileResult compileResult = getCompileResult(sectionState);
                    PackageableElement paramElement = compileResult.getPureModelContextData().getElements().stream().filter(e -> e.getPath().equals(p._class)).findFirst().orElse(null);
                    if (paramElement != null && paramElement instanceof Enumeration)
                    {
                        parameters.put(p.name, LegendFunctionInputParameter.newFunctionParameter(p, paramElement));
                    }
                    else
                    {
                        parameters.put(p.name, LegendFunctionInputParameter.newFunctionParameter(p));
                    }
                });
                consumer.accept(EXEC_FUNCTION_WITH_PARAMETERS_ID, EXEC_FUNCTION_TITLE, function.sourceInformation, Collections.emptyMap(), parameters);
            }
        }
    }

    private static class LegendFunctionInputParameter extends LegendInputParamter
    {
        private Variable variable;
        private PackageableElement element;

        private LegendFunctionInputParameter(Variable variable, PackageableElement element)
        {
            this.variable = variable;
            this.element = element;
        }

        public Variable getVariable()
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

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs, Map<String, Object> inputParameters)
    {
        return EXEC_FUNCTION_ID.equals(commandId) || EXEC_FUNCTION_WITH_PARAMETERS_ID.equals(commandId) ?
               executeFunction(section, entityPath, inputParameters) :
               super.execute(section, entityPath, commandId, executableArgs, inputParameters);
    }

    @Override
    protected void forEachChild(PackageableElement element, Consumer<LegendDeclaration> consumer)
    {
        if (element instanceof Class)
        {
            Class _class = (Class) element;
            _class.properties.forEach(p -> consumer.accept(getDeclaration(p)));
            _class.qualifiedProperties.forEach(qp -> consumer.accept(getDeclaration(qp)));
        }
        else if (element instanceof Enumeration)
        {
            Enumeration _enum = (Enumeration) element;
            String path = _enum.getPath();
            _enum.values.forEach(value ->
            {
                if (isValidSourceInfo(value.sourceInformation))
                {
                    consumer.accept(LegendDeclaration.builder()
                            .withIdentifier(value.value)
                            .withClassifier(path)
                            .withLocation(SourceInformationUtil.toLocation(value.sourceInformation))
                            .build());
                }
            });
        }
        else if (element instanceof Association)
        {
            Association association = (Association) element;
            association.properties.forEach(p -> consumer.accept(getDeclaration(p)));
            association.qualifiedProperties.forEach(qp -> consumer.accept(getDeclaration(qp)));
        }
    }

    private LegendDeclaration getDeclaration(Property property)
    {
        if (!isValidSourceInfo(property.sourceInformation))
        {
            LOGGER.warn("Invalid source information for property {}", property.name);
            return null;
        }

        return LegendDeclaration.builder()
                .withIdentifier(property.name)
                .withClassifier(M3Paths.Property)
                .withLocation(SourceInformationUtil.toLocation(property.sourceInformation))
                .build();
    }

    private LegendDeclaration getDeclaration(QualifiedProperty property)
    {
        if (!isValidSourceInfo(property.sourceInformation))
        {
            LOGGER.warn("Invalid source information for qualified property {}", property.name);
            return null;
        }

        StringBuilder builder = new StringBuilder(property.name).append('(');
        int len = builder.length();
        property.parameters.forEach(p ->
        {
            if (builder.length() > len)
            {
                builder.append(',');
            }
            builder.append(p._class).append(":[");
            Multiplicity mult = p.multiplicity;
            int lower = mult.lowerBound;
            Integer upper = mult.getUpperBound();
            if ((upper == null) ? (lower != 0) : (lower != upper))
            {
                builder.append(lower).append("..");
            }
            if (upper == null)
            {
                builder.append('*');
            }
            else
            {
                builder.append(upper.intValue());
            }
            builder.append(']');
        });
        builder.append(')');
        return LegendDeclaration.builder()
                .withIdentifier(builder.toString())
                .withClassifier(M3Paths.QualifiedProperty)
                .withLocation(SourceInformationUtil.toLocation(property.sourceInformation))
                .build();
    }

    private Iterable<? extends LegendExecutionResult> executeFunction(SectionState section, String entityPath, Map<String, Object> inputParameters)
    {
        CompileResult compileResult = getCompileResult(section);
        if (compileResult.hasException())
        {
            return Collections.singletonList(errorResult(compileResult.getException(), entityPath));
        }

        MutableList<LegendExecutionResult> results = Lists.mutable.empty();
        try
        {
            PureModel pureModel = compileResult.getPureModel();
            ConcreteFunctionDefinition<?> function = pureModel.getConcreteFunctionDefinition(entityPath, null);
            MutableList<? extends Root_meta_pure_extension_Extension> routerExtensions = PureCoreExtensionLoader.extensions().flatCollect(e -> e.extraPureCoreExtensions(pureModel.getExecutionSupport()));
            MutableList<PlanTransformer> planTransformers = Iterate.flatCollect(ServiceLoader.load(PlanGeneratorExtension.class), PlanGeneratorExtension::getExtraPlanTransformers, Lists.mutable.empty());
            SingleExecutionPlan executionPlan = PlanGenerator.generateExecutionPlan(function, null, null, null, pureModel, null, PlanPlatform.JAVA, null, routerExtensions, planTransformers);

            if (this.isEngineServerConfigured())
            {
                LegendExecutionResult legendExecutionResult = this.postEngineServer("/executionPlan/v1/execution/executePlan?serializationFormat=DEFAULT", executionPlan, is ->
                {
                    ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
                    is.transferTo(os);
                    return LegendExecutionResult.newResult(entityPath, Type.SUCCESS, os.toString(StandardCharsets.UTF_8), "Executed using remote engine server");
                });
                results.add(legendExecutionResult);
            }
            else
            {
                PlanExecutor planExecutor = PlanExecutor.newPlanExecutorBuilder().withAvailableStoreExecutors().build();
                MutableMap<String, org.finos.legend.engine.plan.execution.result.Result> parametersToConstantResult = Maps.mutable.empty();
                ExecuteNodeParameterTransformationHelper.buildParameterToConstantResult(executionPlan, inputParameters, parametersToConstantResult);
                collectResults(entityPath, planExecutor.execute(executionPlan, parametersToConstantResult, "localUser", Lists.mutable.empty()), results::add);
            }
        }
        catch (Exception e)
        {
            results.add(errorResult(e, entityPath));
        }
        return results;
    }

    private void collectResults(String entityPath, org.finos.legend.engine.plan.execution.result.Result result, Consumer<? super LegendExecutionResult> consumer)
    {
        // TODO also collect results from activities
        if (result instanceof ErrorResult)
        {
            ErrorResult errorResult = (ErrorResult) result;
            consumer.accept(LegendExecutionResult.newResult(entityPath, Type.ERROR, errorResult.getMessage(), errorResult.getTrace()));
            return;
        }
        if (result instanceof ConstantResult)
        {
            consumer.accept(LegendExecutionResult.newResult(entityPath, Type.SUCCESS, getConstantResult((ConstantResult) result)));
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
                consumer.accept(errorResult(e, entityPath));
                return;
            }
            consumer.accept(LegendExecutionResult.newResult(entityPath, Type.SUCCESS, byteStream.toString(StandardCharsets.UTF_8)));
            return;
        }
        consumer.accept(LegendExecutionResult.newResult(entityPath, Type.WARNING, "Unhandled result type: " + result.getClass().getName()));
    }

    private String getConstantResult(ConstantResult constantResult)
    {
        return getConstantValueResult(constantResult.getValue());
    }

    private String getConstantValueResult(Object value)
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
            return getFunctionResultMapper().writeValueAsString(value);
        }
        catch (Exception e)
        {
            LOGGER.error("Error converting value to JSON", e);
        }
        return value.toString();
    }

    private JsonMapper getFunctionResultMapper()
    {
        return this.functionResultMapper;
    }

    @Override
    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        MutableList<LegendCompletion> legendCompletions = Lists.mutable.empty();
        String codeLine = section.getSection().getLineUpTo(location);

        if (codeLine.isEmpty())
        {
            BOILERPLATE_SUGGESTIONS.collect(s -> new LegendCompletion("Pure boilerplate", s.replaceAll("\n", System.lineSeparator())), legendCompletions);
        }
        else if (ATTRIBUTE_TYPES_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            ATTRIBUTE_TYPES_SUGGESTIONS.collect(s -> new LegendCompletion("Attribute type", s), legendCompletions);
        }
        else if (ATTRIBUTE_MULTIPLICITIES_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            ATTRIBUTE_MULTIPLICITIES_SUGGESTIONS.collect(s -> new LegendCompletion("Attribute multiplicity", s), legendCompletions);
        }

        return CompositeIterable.with(legendCompletions, this.computeCompletionsForSupportedTypes(section, location, SUGGESTABLE_KEYWORDS));
    }

}
