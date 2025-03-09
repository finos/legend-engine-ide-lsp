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

import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.CompileResult;
import org.finos.legend.engine.ide.lsp.extension.Constants;
import org.finos.legend.engine.ide.lsp.extension.LegendTDSRequestLambdaBuilder;
import org.finos.legend.engine.ide.lsp.extension.agGrid.ColumnType;
import org.finos.legend.engine.ide.lsp.extension.agGrid.Filter;
import org.finos.legend.engine.ide.lsp.extension.agGrid.FilterOperation;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSGroupBy;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSRequest;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.features.LegendTDSRequestHandler;
import org.finos.legend.engine.ide.lsp.extension.state.CancellationToken;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.protocol.pure.m3.PackageableElement;
import org.finos.legend.engine.protocol.pure.m3.function.LambdaFunction;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.SingleExecutionPlan;

public class LegendTDSRequestHandlerImpl implements LegendTDSRequestHandler
{
    @Override
    public String description()
    {
        return "Legend TDS Request Handler";
    }

    @Override
    public LegendExecutionResult executeLegendTDSRequest(SectionState section, String entityPath, TDSRequest request, Map<String, Object> inputParameters, CancellationToken requestId)
    {
        if (!(section.getExtension() instanceof FunctionExecutionSupport))
        {
            return LegendExecutionResult.errorResult(new IllegalStateException("Not supported extension"), "", entityPath, null);
        }

        FunctionExecutionSupport functionExecutionSupport = (FunctionExecutionSupport) section.getExtension();
        AbstractLSPGrammarExtension extension = functionExecutionSupport.getExtension();

        CompileResult compileResult = extension.getCompileResult(section);
        if (compileResult.hasEngineException())
        {
            return extension.errorResult(compileResult.getCompileErrorResult(), entityPath);
        }
        MutableList<LegendExecutionResult> results = Lists.mutable.empty();
        PureModel pureModel = compileResult.getPureModel();

        PackageableElement packageableElement = compileResult.getPureModelContextData().getElements().stream().filter(e -> e.getPath().equals(entityPath)).collect(Collectors.toList()).get(0);
        LambdaFunction lambda = functionExecutionSupport.getLambda(packageableElement);

        try
        {
            TDSGroupBy groupBy = request.getGroupBy();
            for (int index = 0; index < groupBy.getGroupKeys().size(); index++)
            {
                Filter groupFilter = new Filter(groupBy.getColumns().get(index), ColumnType.String, FilterOperation.EQUALS, groupBy.getGroupKeys().get(index));
                request.getFilter().add(groupFilter);
            }

            LambdaFunction newLambda = new LambdaFunction();
            newLambda.body = LegendTDSRequestLambdaBuilder.buildLambdaExpressions(lambda.body, request);
            newLambda.parameters = lambda.parameters;

            GlobalState globalState = section.getDocumentState().getGlobalState();
            SingleExecutionPlan executionPlan = functionExecutionSupport.getExecutionPlan(packageableElement, newLambda, pureModel, inputParameters, globalState.getSetting(Constants.LEGEND_PROTOCOL_VERSION));
            FunctionExecutionSupport.executePlan(globalState, functionExecutionSupport, section.getDocumentState().getDocumentId(), section.getSectionNumber(), executionPlan, null, entityPath, inputParameters, results, requestId);
        }
        catch (Exception e)
        {
            results.add(extension.errorResult(e, entityPath));
        }
        return results.get(0);
    }
}
