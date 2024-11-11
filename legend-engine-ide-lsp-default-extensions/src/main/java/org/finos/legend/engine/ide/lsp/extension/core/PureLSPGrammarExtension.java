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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.lazy.CompositeIterable;
import org.finos.legend.engine.ide.lsp.extension.AbstractLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.AbstractLegacyParserLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.CommandConsumer;
import org.finos.legend.engine.ide.lsp.extension.CompileResult;
import org.finos.legend.engine.ide.lsp.extension.Constants;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.engine.ide.lsp.extension.SourceInformationUtil;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommandType;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.functionActivator.FunctionActivatorLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.compiler.Compiler;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperValueSpecificationBuilder;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.grammar.from.ParseTreeWalkerSourceInformation;
import org.finos.legend.engine.language.pure.grammar.from.PureGrammarParserContext;
import org.finos.legend.engine.language.pure.grammar.from.SectionSourceCode;
import org.finos.legend.engine.language.pure.grammar.from.antlr4.domain.DomainLexerGrammar;
import org.finos.legend.engine.language.pure.grammar.from.antlr4.domain.DomainParserGrammar;
import org.finos.legend.engine.language.pure.grammar.from.domain.DomainParser;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.protocol.pure.v1.model.SourceInformation;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.executionPlan.SingleExecutionPlan;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Association;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Class;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Enumeration;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Function;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Multiplicity;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.Property;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.QualifiedProperty;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.StereotypePtr;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.domain.TaggedValue;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.function.FunctionTestSuite;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.function.StoreTestData;
import org.finos.legend.engine.protocol.pure.v1.model.test.TestSuite;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.Variable;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.Lambda;
import org.finos.legend.engine.repl.autocomplete.Completer;
import org.finos.legend.engine.repl.autocomplete.CompletionResult;
import org.finos.legend.engine.repl.relational.autocomplete.RelationalCompleterExtension;
import org.finos.legend.engine.shared.core.deployment.DeploymentMode;
import org.finos.legend.engine.shared.core.identity.Identity;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.constraint.Constraint;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.FunctionDefinition;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension for the Pure grammar.
 */
public class PureLSPGrammarExtension extends AbstractLegacyParserLSPGrammarExtension implements FunctionExecutionSupport
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

    protected static final String ACTIVATE_FUNCTION_ID = "legend.pure.activateFunction";
    private static final String ACTIVATE_FUNCTION_TITLE = "Activate";

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
            CompileResult compileResult = getCompileResult(sectionState);
            if (!compileResult.hasEngineException())
            {
                Function function = (Function) element;
                SourceInformation sourceInformation = function.sourceInformation;

                FunctionExecutionSupport.collectFunctionExecutionCommand(
                        this,
                        function,
                        compileResult,
                        consumer
                );

                Map<String, String> arguments = sectionState.getDocumentState().getGlobalState().findGrammarExtensionThatImplements(FunctionActivatorLSPGrammarExtension.class).collect(Collectors.toMap(LegendLSPExtension::getName, x -> x.getSnippet((Function) element, compileResult.getPureModelContextData().getElements())));
                consumer.accept(ACTIVATE_FUNCTION_ID, ACTIVATE_FUNCTION_TITLE, sourceInformation, arguments, Collections.emptyMap(), LegendCommandType.CLIENT);
            }
        }
    }

    @Override
    protected Stream<Optional<LegendReferenceResolver>> getReferenceResolvers(SectionState section, PackageableElement packageableElement, Optional<CoreInstance> coreInstance)
    {
        Stream<Optional<LegendReferenceResolver>> pureReferences = packageableElement.accept(new PackageableElementDefaultVisitor()
        {
            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(Function function)
            {
                Stream<Optional<LegendReferenceResolver>> stereotypeReferences = toStereotypeReferences(function.stereotypes);
                Stream<Optional<LegendReferenceResolver>> taggedValueReferences = toTaggedValueReferences(function.taggedValues);
                Stream<Optional<LegendReferenceResolver>> testSuiteReferences = toFunctionTestSuiteReferences(getTestSuites(function));
                Stream<Optional<LegendReferenceResolver>> coreReferences = FUNCTION_EXPRESSION_NAVIGATOR.findReferences(coreInstance);
                return Stream.of(stereotypeReferences, taggedValueReferences, coreReferences, testSuiteReferences)
                        .flatMap(java.util.function.Function.identity());
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(Class clazz)
            {
                Stream<Optional<LegendReferenceResolver>> milestonedPropertyReferences = toPropertyReferences(clazz.originalMilestonedProperties);
                Stream<Optional<LegendReferenceResolver>> propertyReferences = toPropertyReferences(clazz.properties);
                Stream<Optional<LegendReferenceResolver>> stereotypeReferences = toStereotypeReferences(clazz.stereotypes);
                Stream<Optional<LegendReferenceResolver>> taggedValueReferences = toTaggedValueReferences(clazz.taggedValues);
                Stream<Optional<LegendReferenceResolver>> qualifiedPropertyReferences = toQualifiedPropertyReferences(clazz.qualifiedProperties);
                Stream<Optional<LegendReferenceResolver>> coreReferences = Stream.empty();
                if (coreInstance.isPresent())
                {
                    coreReferences = toReferences((org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class) coreInstance.get());
                }
                return Stream.of(milestonedPropertyReferences, propertyReferences, stereotypeReferences, taggedValueReferences, qualifiedPropertyReferences, coreReferences)
                        .flatMap(java.util.function.Function.identity());
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(Association association)
            {
                Stream<Optional<LegendReferenceResolver>> milestonedPropertyReferences = toPropertyReferences(association.originalMilestonedProperties);
                Stream<Optional<LegendReferenceResolver>> propertyReferences = toPropertyReferences(association.properties);
                Stream<Optional<LegendReferenceResolver>> qualifiedPropertyReferences = toQualifiedPropertyReferences(association.qualifiedProperties);
                Stream<Optional<LegendReferenceResolver>> stereotypeReferences = toStereotypeReferences(association.stereotypes);
                Stream<Optional<LegendReferenceResolver>> taggedValueReferences = toTaggedValueReferences(association.taggedValues);
                Stream<Optional<LegendReferenceResolver>> coreReferences = Stream.empty();
                if (coreInstance.isPresent())
                {
                    coreReferences = toReferences((org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association) coreInstance.get());
                }
                return Stream.of(milestonedPropertyReferences, propertyReferences, qualifiedPropertyReferences, stereotypeReferences, taggedValueReferences, coreReferences)
                        .flatMap(java.util.function.Function.identity());
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(Enumeration enumeration)
            {
                Stream<Optional<LegendReferenceResolver>> stereotypeReferences = toStereotypeReferences(enumeration.stereotypes);
                Stream<Optional<LegendReferenceResolver>> taggedValueReferences = toTaggedValueReferences(enumeration.taggedValues);
                return Stream.concat(stereotypeReferences, taggedValueReferences);
            }
        });
        return pureReferences;
    }

    private Stream<Optional<LegendReferenceResolver>> toPropertyReferences(List<Property> properties)
    {
        return properties.stream().flatMap(this::toReferences);
    }

    public static Stream<Optional<LegendReferenceResolver>> toStereotypeReferences(List<StereotypePtr> stereotypePtrs)
    {
        return stereotypePtrs.stream().flatMap(PureLSPGrammarExtension::toReferences);
    }

    public static Stream<Optional<LegendReferenceResolver>> toTaggedValueReferences(List<TaggedValue> taggedValues)
    {
        return taggedValues.stream().flatMap(PureLSPGrammarExtension::toReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toQualifiedPropertyReferences(List<QualifiedProperty> qualifiedProperties)
    {
        return qualifiedProperties.stream().flatMap(this::toReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toFunctionTestSuiteReferences(List<? extends TestSuite> functionTestSuites)
    {
        return functionTestSuites.stream().flatMap(testSuite -> toReferences((FunctionTestSuite) testSuite));
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(Property property)
    {
        Optional<LegendReferenceResolver> propertyReference = LegendReferenceResolver.newReferenceResolver(property.propertyTypeSourceInformation, x -> x.resolvePackageableElement(property.type, property.propertyTypeSourceInformation));
        Stream<Optional<LegendReferenceResolver>> stereotypeReferences = toStereotypeReferences(property.stereotypes);
        Stream<Optional<LegendReferenceResolver>> taggedValueReferences = toTaggedValueReferences(property.taggedValues);
        return Stream.of(Stream.of(propertyReference), stereotypeReferences, taggedValueReferences)
                .flatMap(java.util.function.Function.identity());
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(QualifiedProperty qualifiedProperty)
    {
        Stream<Optional<LegendReferenceResolver>> stereotypeReferences = toStereotypeReferences(qualifiedProperty.stereotypes);
        Stream<Optional<LegendReferenceResolver>> taggedValueReferences = toTaggedValueReferences(qualifiedProperty.taggedValues);
        return Stream.concat(stereotypeReferences, taggedValueReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(FunctionTestSuite functionTestSuite)
    {
        return functionTestSuite.testData.stream().flatMap(this::toReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(StoreTestData storeTestData)
    {
        Optional<LegendReferenceResolver> storeReference = LegendReferenceResolver.newReferenceResolver(storeTestData.store.sourceInformation, x -> x.resolveStore(storeTestData.store.path, storeTestData.store.sourceInformation));
        return Stream.of(storeReference);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class clazz)
    {
        Stream<Optional<LegendReferenceResolver>> constraintReferences = StreamSupport.stream(clazz._constraints().spliterator(), false)
                .flatMap(this::toReferences);
        RichIterable<? extends CoreInstance> qualifiedProperties = clazz._qualifiedProperties();
        Stream<Optional<LegendReferenceResolver>> qualifiedPropertyReferences = StreamSupport.stream(qualifiedProperties.spliterator(), false)
                .flatMap(qualifiedProperty -> FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(qualifiedProperty)));

        Stream<Optional<LegendReferenceResolver>> superTypes = StreamSupport.stream(clazz._generalizations().spliterator(), false)
                .map(generalization -> LegendReferenceResolver.newReferenceResolver(generalization.getSourceInformation(), generalization._general()._rawType()));

        return Stream.concat(Stream.concat(constraintReferences, qualifiedPropertyReferences), superTypes);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association association)
    {
        RichIterable<? extends CoreInstance> qualifiedProperties = association._qualifiedProperties();
        return StreamSupport.stream(qualifiedProperties.spliterator(), false)
                .flatMap(qualifiedProperty -> FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(qualifiedProperty)));
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(Constraint constraint)
    {
        if (constraint == null)
        {
            return Stream.empty();
        }
        Stream<Optional<LegendReferenceResolver>> functionDefinitionReference = FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(constraint._functionDefinition()));
        Stream<Optional<LegendReferenceResolver>> messageFunctionReference = FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(constraint._messageFunction()));
        return Stream.concat(functionDefinitionReference, messageFunctionReference);
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs, Map<String, Object> inputParameters)
    {
        return FunctionExecutionSupport.EXECUTE_COMMAND_ID.equals(commandId)
               ? FunctionExecutionSupport.executeFunction(this, section, entityPath, inputParameters)
               : FunctionExecutionSupport.execute(this, section, entityPath, commandId, executableArgs,
                       inputParameters);
    }

    @Override
    public AbstractLSPGrammarExtension getExtension()
    {
        return this;
    }

    @Override
    public Lambda getLambda(PackageableElement element)
    {
        Function function = (Function) element;
        Lambda l = new Lambda();
        l.body = function.body;
        l.parameters = function.parameters;
        return l;
    }

    @Override
    public SingleExecutionPlan getExecutionPlan(PackageableElement element, Lambda lambda, PureModel pureModel, Map<String, Object> args, String clientVersion)
    {
        Function function = (Function) element;
        FunctionDefinition<?> functionDefinition;
        if (lambda.body == function.body)
        {
            functionDefinition = pureModel.getConcreteFunctionDefinition(element.getPath(), element.sourceInformation);
        }
        else
        {
            functionDefinition = HelperValueSpecificationBuilder.buildLambda(lambda.body, lambda.parameters, pureModel.getContext());
        }
        return FunctionExecutionSupport.generateSingleExecutionPlan(pureModel, clientVersion, functionDefinition);
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
                if (SourceInformationUtil.isValidSourceInfo(value.sourceInformation))
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

    @Override
    protected List<? extends TestSuite> getTestSuites(PackageableElement element)
    {
        if (element instanceof Function)
        {
            List<FunctionTestSuite> testSuites = ((Function) element).tests;
            return (testSuites == null) ? Lists.fixedSize.empty() : testSuites;
        }
        return super.getTestSuites(element);
    }

    private LegendDeclaration getDeclaration(Property property)
    {
        if (!SourceInformationUtil.isValidSourceInfo(property.sourceInformation))
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
        if (!SourceInformationUtil.isValidSourceInfo(property.sourceInformation))
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

        CompletionResult autocompletion = getFunctionAutocompletion(section, location);
        if (autocompletion != null)
        {
            autocompletion.getCompletion().collect(c -> new LegendCompletion(c.getDisplay(), c.getCompletion()), legendCompletions);
        }

        return CompositeIterable.with(legendCompletions, this.computeCompletionsForSupportedTypes(section, location, SUGGESTABLE_KEYWORDS));
    }

    private CompletionResult getFunctionAutocompletion(SectionState section, TextPosition location)
    {
        try
        {
            int autocompleteColumn = Math.max(location.getColumn() - 1, 0);
            TextPosition autocompleteLocation = TextPosition.newPosition(location.getLine(), autocompleteColumn);
            SectionSourceCode sectionSourceCode = toSectionSourceCode(section);
            CharStream input = CharStreams.fromString(sectionSourceCode.code);
            DomainLexerGrammar lexer = new DomainLexerGrammar(input);
            lexer.removeErrorListeners();
            DomainParserGrammar parser = new DomainParserGrammar(new CommonTokenStream(lexer));
            parser.removeErrorListeners();

            DomainParserGrammar.DefinitionContext definition = parser.definition();
            return definition.getRuleContexts(DomainParserGrammar.ElementDefinitionContext.class)
                    .stream()
                    .filter(elemDefCtx -> SourceInformationUtil.toLocation(sectionSourceCode.walkerSourceInformation.getSourceInformation(elemDefCtx)).getTextInterval().includes(autocompleteLocation))
                    .findAny()
                    .flatMap(elemDefCtx -> elemDefCtx.getRuleContexts(DomainParserGrammar.FunctionDefinitionContext.class)
                            .stream()
                            .filter(funcDefCtx -> SourceInformationUtil.toLocation(sectionSourceCode.walkerSourceInformation.getSourceInformation(funcDefCtx)).getTextInterval().includes(autocompleteLocation))
                            .findAny())
                    .map(funcCtx ->
                    {
                        TextLocation codeBlockLocation = SourceInformationUtil.toLocation(sectionSourceCode.walkerSourceInformation.getSourceInformation(funcCtx.codeBlock()));
                        String functionExpression = section.getSection().getInterval(codeBlockLocation.getTextInterval().getStart().getLine(), codeBlockLocation.getTextInterval().getStart().getColumn(), autocompleteLocation.getLine(), autocompleteLocation.getColumn());
                        PureModel pureModel = this.getCompileResult(section).getPureModel();
                        return new Completer(pureModel, Lists.mutable.with(new RelationalCompleterExtension())).complete(functionExpression);
                    }).orElse(null);
        }
        catch (Exception e)
        {
            LOGGER.error("Error fetching autocompletion results", e);
            return null;
        }
    }

    @Override
    public void startup(GlobalState globalState)
    {
        super.startup(globalState);
        // sample parse/compile/exec to load classes at startup and speed up future executions
        String startupGrammar = "function _startup_::_vscode_::function(): Any[1]\n" +
                "{\n" +
                "  1 + 1;\n" +
                "}";

        List<PackageableElement> elements = new ArrayList<>();

        this.parse(
                new SectionSourceCode(
                        startupGrammar,
                        "Pure",
                        SourceInformation.getUnknownSourceInformation(),
                        new ParseTreeWalkerSourceInformation.Builder("memory", 0, 0).build()
                ),
                elements::add,
                new PureGrammarParserContext(PureGrammarParserExtensions.fromAvailableExtensions())
        );

        PureModelContextData pmcd = PureModelContextData.newBuilder().withElements(elements).build();
        PureModel pureModel = Compiler.compile(pmcd, DeploymentMode.PROD, Identity.getAnonymousIdentity().getName());
        PackageableElement element = elements.get(0);
        SingleExecutionPlan executionPlan = this.getExecutionPlan(element, this.getLambda(element), pureModel, Map.of(), globalState.getSetting(Constants.LEGEND_PROTOCOL_VERSION));
        MutableList<LegendExecutionResult> results = Lists.mutable.empty();
        FunctionExecutionSupport.executePlan(globalState, this, "memory", -1, executionPlan, null, element.getPath(), Map.of(), results);
    }

    @Override
    public List<Variable> getParameters(PackageableElement element)
    {
        Function function = (Function) element;
        return function.parameters;
    }
}
