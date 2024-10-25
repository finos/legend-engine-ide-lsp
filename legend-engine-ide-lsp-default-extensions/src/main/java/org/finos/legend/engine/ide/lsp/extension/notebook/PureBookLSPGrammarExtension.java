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

package org.finos.legend.engine.ide.lsp.extension.notebook;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.finos.legend.engine.ide.lsp.extension.CompileResult;
import org.finos.legend.engine.ide.lsp.extension.Constants;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.engine.ide.lsp.extension.SourceInformationUtil;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.core.FunctionExecutionSupport;
import org.finos.legend.engine.ide.lsp.extension.core.FunctionExpressionNavigator;
import org.finos.legend.engine.ide.lsp.extension.core.PureLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.NotebookDocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperValueSpecificationBuilder;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParserContext;
import org.finos.legend.engine.language.pure.grammar.from.domain.DomainParser;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.plan.execution.PlanExecutionContext;
import org.finos.legend.engine.protocol.pure.v1.model.SourceInformation;
import org.finos.legend.engine.protocol.pure.v1.model.context.EngineErrorType;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.SingleExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Lambda;
import org.finos.legend.engine.repl.autocomplete.Completer;
import org.finos.legend.engine.repl.autocomplete.CompletionResult;
import org.finos.legend.engine.repl.relational.autocomplete.RelationalCompleterExtension;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.finos.legend.engine.shared.javaCompiler.JavaCompileException;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.LambdaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PureBookLSPGrammarExtension implements LegendLSPGrammarExtension
{
    private static final String COMPILE_RESULT_KEY = "_COMPILE_RESULT";
    private static final String PLAN_EXEC_CONTEXT_KEY = "_PLAN_EXEC_CONTEXT";
    private static final FunctionExpressionNavigator FUNCTION_EXPRESSION_NAVIGATOR = new FunctionExpressionNavigator();
    private static final Logger LOGGER = LoggerFactory.getLogger(PureBookLSPGrammarExtension.class);
    private DomainParser domainParser;
    private PureGrammarParserContext parserContext;
    private PureLSPGrammarExtension pureGrammarExtension;

    @Override
    public String getName()
    {
        return "purebook";
    }

    @Override
    public void startup(GlobalState globalState)
    {
        this.domainParser = new DomainParser();
        this.parserContext = new PureGrammarParserContext(PureGrammarParserExtensions.fromAvailableExtensions());
        this.pureGrammarExtension = globalState.findGrammarExtensionThatImplements(PureLSPGrammarExtension.class)
                .findAny()
                .orElseThrow(() -> new UnsupportedOperationException("Notebook requires pure grammar extension"));
    }

    @Override
    public void initialize(SectionState section)
    {
        this.parse(section);
    }

    @Override
    public void destroy(SectionState section)
    {
        DocumentState documentState = section.getDocumentState();
        GlobalState globalState = documentState.getGlobalState();
        globalState.removeProperty(documentState.getDocumentId() + COMPILE_RESULT_KEY);
        globalState.removeProperty(documentState.getDocumentId() + PLAN_EXEC_CONTEXT_KEY);
    }

    private CompletableFuture<Lambda> parse(SectionState sectionState)
    {
        return sectionState.getProperty("PARSE_RESULT", () -> tryParse(sectionState));
    }

    private CompletableFuture<Lambda> tryParse(SectionState sectionState)
    {
        try
        {
            Lambda lambda = this.domainParser.parseLambda(
                    sectionState.getSection().getText(true),
                    this.parserContext,
                    sectionState.getDocumentState().getDocumentId(),
                    0,
                    0,
                    true
            );
            return CompletableFuture.completedFuture(lambda);
        }
        catch (Exception e)
        {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<? extends LambdaFunction<?>> compile(SectionState sectionState)
    {
        DocumentState documentState = sectionState.getDocumentState();
        GlobalState globalState = documentState.getGlobalState();
        return globalState.getProperty(documentState.getDocumentId() + COMPILE_RESULT_KEY, () -> tryCompile(sectionState));
    }

    private CompletableFuture<? extends LambdaFunction<?>> tryCompile(SectionState sectionState)
    {
        return this.parse(sectionState).thenApply(x ->
                {
                    CompileResult compileResult = this.pureGrammarExtension.getCompileResult(sectionState);
                    PureModel pureModel = compileResult.getPureModel();
                    if (pureModel == null)
                    {
                        throw compileResult.getEngineException();
                    }
                    return HelperValueSpecificationBuilder.buildLambda(x, pureModel.getContext());
                }
                // when we complete compiling, trigger plan generation on the background to improve user experience...
        ).whenCompleteAsync((l, e) -> this.generatePlan(sectionState), sectionState.getDocumentState().getGlobalState().getForkJoinPool());
    }

    @Override
    public Iterable<? extends LegendDiagnostic> getDiagnostics(SectionState sectionState)
    {
        DocumentState documentState = sectionState.getDocumentState();
        TextLocation sectionLocation = TextLocation.newTextSource(documentState.getDocumentId(), sectionState.getSection().getTextInterval());
        if (!(documentState instanceof NotebookDocumentState))
        {
            return List.of(LegendDiagnostic.newDiagnostic(sectionLocation, "###purebook should not be use outside of Purebooks", LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Parser));
        }

        if (sectionState.getSection().getFullText().isEmpty())
        {
            return List.of();
        }

        try
        {
            try
            {
                this.compile(sectionState).get(30, TimeUnit.SECONDS);
            }
            catch (CompletionException | ExecutionException e)
            {
                throw e.getCause();
            }
        }
        catch (EngineException e)
        {
            SourceInformation sourceInfo = e.getSourceInformation();
            TextLocation location;
            if (SourceInformationUtil.isValidSourceInfo(sourceInfo))
            {
                location = SourceInformationUtil.toLocation(sourceInfo);
            }
            else
            {
                location = sectionLocation;
            }

            LegendDiagnostic.Source source = e.getErrorType() == EngineErrorType.PARSER ? LegendDiagnostic.Source.Parser : LegendDiagnostic.Source.Compiler;

            return List.of(LegendDiagnostic.newDiagnostic(location, e.getMessage(), LegendDiagnostic.Kind.Error, source));
        }
        catch (Throwable t)
        {
            return List.of(LegendDiagnostic.newDiagnostic(sectionLocation, t.getMessage(), LegendDiagnostic.Kind.Error, LegendDiagnostic.Source.Compiler));
        }

        return List.of();
    }

    @Override
    public Stream<LegendReference> getLegendReferences(SectionState sectionState)
    {
        CompletableFuture<? extends LambdaFunction<?>> compiled = this.compile(sectionState);
        try
        {
            LambdaFunction<?> lambdaFunction = compiled.get(30, TimeUnit.SECONDS);
            PureModel pureModel = this.pureGrammarExtension.getCompileResult(sectionState).getPureModel();
            return LegendReferenceResolver.toLegendReference(
                    sectionState,
                    FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(lambdaFunction)),
                    pureModel.getContext()
            );
        }
        catch (Exception ignore)
        {
            // ignore for now...
        }
        return Stream.empty();
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs, Map<String, Object> inputParameters)
    {
        switch (commandId)
        {
            case "executeCell":
                return this.executeCell(section, inputParameters);
            default:
                return List.of();
        }
    }

    private Iterable<? extends LegendExecutionResult> executeCell(SectionState section, Map<String, Object> inputParameters)
    {
        if (section.getSection().getFullText().isEmpty())
        {
            return List.of(FunctionExecutionSupport.FunctionLegendExecutionResult.newResult("notebook_cell", LegendExecutionResult.Type.SUCCESS, "[]", "Nothing to execute!", section.getDocumentState().getDocumentId(), section.getSectionNumber(), inputParameters));
        }

        Pair<SingleExecutionPlan, PlanExecutionContext> planExecutionContextPair;

        try
        {
            try
            {
                planExecutionContextPair = this.generatePlan(section).get(30, TimeUnit.SECONDS);
            }
            catch (CompletionException | ExecutionException e)
            {
                throw e.getCause();
            }
        }
        catch (EngineException e)
        {
            return Lists.mutable.with(this.pureGrammarExtension.getExtension().errorResult(e, "Cannot execute since cell does not parse or compile.  Check diagnostics for further details...", "notebook_cell", section.getDocumentState().getTextLocation()));
        }
        catch (Throwable e)
        {
            return Lists.mutable.with(this.pureGrammarExtension.getExtension().errorResult(e, "Cannot generate an execution plan for given expression.  Likely the expression is not supported yet...", "notebook_cell", section.getDocumentState().getTextLocation()));
        }

        return this.executePlan(section, planExecutionContextPair, inputParameters);
    }

    private MutableList<LegendExecutionResult> executePlan(SectionState section, Pair<SingleExecutionPlan, PlanExecutionContext> planContext, Map<String, Object> inputParameters)
    {
        MutableList<LegendExecutionResult> results = Lists.mutable.empty();
        FunctionExecutionSupport.executePlan(section.getDocumentState().getGlobalState(), this.pureGrammarExtension, section.getDocumentState().getDocumentId(), section.getSectionNumber(), planContext.getOne(), planContext.getTwo(), "notebook_cell", inputParameters, results);
        return results;
    }

    private CompletableFuture<Pair<SingleExecutionPlan, PlanExecutionContext>> generatePlan(SectionState sectionState)
    {
        DocumentState documentState = sectionState.getDocumentState();
        GlobalState globalState = documentState.getGlobalState();
        return globalState.getProperty(documentState.getDocumentId() + PLAN_EXEC_CONTEXT_KEY, () -> tryGeneratePlan(sectionState));
    }

    private CompletableFuture<Pair<SingleExecutionPlan, PlanExecutionContext>> tryGeneratePlan(SectionState sectionState)
    {
        return this.compile(sectionState).thenApplyAsync(lambdaFunction ->
        {
            try
            {
                PureModel pureModel = this.pureGrammarExtension.getCompileResult(sectionState).getPureModel();
                GlobalState globalState = sectionState.getDocumentState().getGlobalState();
                SingleExecutionPlan singleExecutionPlan = FunctionExecutionSupport.generateSingleExecutionPlan(pureModel, globalState.getSetting(Constants.LEGEND_PROTOCOL_VERSION), lambdaFunction);
                return Tuples.pair(singleExecutionPlan, new PlanExecutionContext(singleExecutionPlan, List.of()));
            }
            catch (JavaCompileException e)
            {
                throw new RuntimeException(e);
            }
        }, sectionState.getDocumentState().getGlobalState().getForkJoinPool());
    }

    @Override
    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        try
        {
            int column = StrictMath.max(0,  location.getColumn() - 1);
            String functionExpression = section.getSection().getInterval(section.getSection().getStartLine(), 0, location.getLine(), column).trim();
            functionExpression = functionExpression.replace("\n", "").replace("\r", "");
            PureModel pureModel = this.pureGrammarExtension.getCompileResult(section).getPureModel();
            CompletionResult completionResult = new Completer(pureModel, Lists.mutable.with(new RelationalCompleterExtension())).complete(functionExpression);
            return completionResult.getCompletion().collect(c -> new LegendCompletion(c.getDisplay(), c.getCompletion()));
        }
        catch (Exception e)
        {
            LOGGER.error("Error fetching autocompletion results", e);
            return List.of();
        }
    }
}
