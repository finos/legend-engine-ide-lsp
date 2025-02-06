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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommandType;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.CancellationToken;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.protocol.functionActivator.metamodel.FunctionActivator;
import org.finos.legend.engine.protocol.pure.m3.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.PureProtocolObjectMapperFactory;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FunctionActivatorCommandsSupport implements CommandsSupport
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FunctionActivatorCommandsSupport.class);

    private static final String LEGEND_FUNCTION_ACTIVATOR_VALIDATE = "legend.functionActivator.validate";
    private static final String LEGEND_FUNCTION_ACTIVATOR_PUBLISH_SANDBOX = "legend.functionActivator.publishSandbox";

    private final JsonMapper resultMapper = PureProtocolObjectMapperFactory.withPureProtocolExtensions(JsonMapper.builder()
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build());

    private final AbstractLSPGrammarExtension extension;

    FunctionActivatorCommandsSupport(AbstractLSPGrammarExtension extension)
    {
        this.extension = extension;
    }

    @Override
    public Set<String> getSupportedCommands()
    {
        return Set.of(LEGEND_FUNCTION_ACTIVATOR_VALIDATE, LEGEND_FUNCTION_ACTIVATOR_PUBLISH_SANDBOX);
    }

    @Override
    public void collectCommands(SectionState sectionState, PackageableElement element, CommandConsumer consumer)
    {
        if (element instanceof FunctionActivator && this.extension.isEngineServerConfigured())
        {
            consumer.accept(LEGEND_FUNCTION_ACTIVATOR_VALIDATE, "Validate", element.sourceInformation, LegendCommandType.CODELENS);
            consumer.accept(LEGEND_FUNCTION_ACTIVATOR_PUBLISH_SANDBOX, "Publish to Sandbox", element.sourceInformation, LegendCommandType.CODELENS);
        }
    }

    @Override
    public Iterable<? extends LegendExecutionResult> executeCommand(SectionState section, PackageableElement element, String commandId, Map<String, String> executableArgs, Map<String, Object> inputParameters, CancellationToken requestId)
    {
        TextLocation location = SourceInformationUtil.toLocation(element.sourceInformation);
        String entityPath = element.getPath();

        CompileResult compileResult = this.extension.getCompileResult(section);
        if (compileResult.hasEngineException())
        {
            return Collections.singletonList(this.extension.errorResult(compileResult.getCompileErrorResult(), entityPath));
        }

        try
        {
            String api;
            switch (commandId)
            {
                case LEGEND_FUNCTION_ACTIVATOR_VALIDATE:
                {
                    api = "/functionActivator/validate";
                    String result = this.extension.postEngineServer(
                            api,
                            new FunctionActivatorInput(entityPath, compileResult.getPureModelContextData()),
                            x -> this.resultMapper.writeValueAsString(this.resultMapper.readTree(x))
                    );
                    List<Object> functionActivatorErrors = this.resultMapper.readValue(result, new TypeReference<>(){});
                    return (functionActivatorErrors.isEmpty()) ?
                            Collections.singletonList(LegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.SUCCESS, "", location)) :
                            Collections.singletonList(this.extension.errorResult(new Throwable(result), entityPath, location));
                }
                case LEGEND_FUNCTION_ACTIVATOR_PUBLISH_SANDBOX:
                {
                    api = "/functionActivator/publishToSandbox";
                    String result = this.extension.postEngineServer(
                            api,
                            new FunctionActivatorInput(entityPath, compileResult.getPureModelContextData()),
                            x -> this.resultMapper.writeValueAsString(this.resultMapper.readTree(x))
                    );
                    HashMap<Object, Object> deploymentResult = this.resultMapper.readValue(result, new TypeReference<>(){});
                    Boolean deploymentSuccessful = (Boolean) deploymentResult.getOrDefault("successful", false);
                    return (deploymentSuccessful) ?
                            Collections.singletonList(LegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.SUCCESS, result, location)) :
                            Collections.singletonList(this.extension.errorResult(new Throwable(result), entityPath, location));
                }
                default:
                {
                    LOGGER.warn("Unknown command id for {}: {}", entityPath, commandId);
                    return Collections.emptyList();
                }
            }
        }
        catch (Exception e)
        {
            return Collections.singletonList(this.extension.errorResult(e, entityPath, location));
        }
    }

    private static final class FunctionActivatorInput
    {
        public String functionActivator;
        public PureModelContext model;

        public FunctionActivatorInput(String functionActivator, PureModelContext model)
        {
            this.functionActivator = functionActivator;
            this.model = model;
        }
    }
}
