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

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.features.LegendTDSRequestHandler;
import org.finos.legend.engine.ide.lsp.extension.LegendTDSRequestLambdaBuilder;
import org.finos.legend.engine.ide.lsp.extension.agGrid.ColumnType;
import org.finos.legend.engine.ide.lsp.extension.agGrid.Filter;
import org.finos.legend.engine.ide.lsp.extension.agGrid.FilterOperation;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSGroupBy;
import org.finos.legend.engine.ide.lsp.extension.agGrid.TDSRequest;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperValueSpecificationBuilder;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.plan.generation.PlanGenerator;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.plan.platform.PlanPlatform;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.SingleExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Function;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.ValueSpecification;
import org.finos.legend.engine.pure.code.core.PureCoreExtensionLoader;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.LambdaFunction;

public class LegendTDSRequestHandlerImpl implements LegendTDSRequestHandler
{
    @Override
    public String description()
    {
        return "Legend TDS Request Handler";
    }

    @Override
    public LegendExecutionResult executeLegendTDSRequest(SectionState section, String entityPath, TDSRequest request, Map<String, Object> inputParameters)
    {
        if (!(section.getExtension() instanceof PureLSPGrammarExtension))
        {
            return LegendExecutionResult.errorResult(new IllegalStateException("Expected pure extensions"), "", entityPath, null);
        }

        PureLSPGrammarExtension extension = (PureLSPGrammarExtension) section.getExtension();

        AbstractLSPGrammarExtension.CompileResult compileResult = extension.getCompileResult(section);
        if (compileResult.hasException())
        {
            return extension.errorResult(compileResult.getException(), entityPath);
        }
        MutableList<LegendExecutionResult> results = Lists.mutable.empty();
        PureModel pureModel = compileResult.getPureModel();
        Function function = (Function) compileResult.getPureModelContextData().getElements().stream().filter(e -> e.getPath().equals(entityPath)).collect(Collectors.toList()).get(0);
        try
        {
            TDSGroupBy groupBy = request.getGroupBy();
            for (int index = 0; index < groupBy.getGroupKeys().size(); index++)
            {
                Filter groupFilter = new Filter(groupBy.getColumns().get(index), ColumnType.String, FilterOperation.EQUALS, groupBy.getGroupKeys().get(index));
                request.getFilter().add(groupFilter);
            }
            List<ValueSpecification> expressions = LegendTDSRequestLambdaBuilder.buildLambdaExpressions(function.body.get(0), request);

            LambdaFunction<?> queryLambda = HelperValueSpecificationBuilder.buildLambda(expressions, function.parameters, pureModel.getContext());
            MutableList<? extends Root_meta_pure_extension_Extension> routerExtensions = PureCoreExtensionLoader.extensions().flatCollect(e -> e.extraPureCoreExtensions(pureModel.getExecutionSupport()));
            MutableList<PlanTransformer> planTransformers = Iterate.flatCollect(ServiceLoader.load(PlanGeneratorExtension.class), PlanGeneratorExtension::getExtraPlanTransformers, Lists.mutable.empty());
            SingleExecutionPlan executionPlan = PlanGenerator.generateExecutionPlan(queryLambda, null, null, null, pureModel, null, PlanPlatform.JAVA, null, routerExtensions, planTransformers);
            extension.executePlan(section, executionPlan, entityPath, inputParameters, results);
        }
        catch (Exception e)
        {
            results.add(extension.errorResult(e, entityPath));
        }
        return results.get(0);
    }
}
