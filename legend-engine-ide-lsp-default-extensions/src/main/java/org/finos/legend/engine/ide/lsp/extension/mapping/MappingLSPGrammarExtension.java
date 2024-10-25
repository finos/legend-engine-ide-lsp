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

package org.finos.legend.engine.ide.lsp.extension.mapping;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.block.factory.Functions;
import org.eclipse.collections.impl.lazy.CompositeIterable;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.ide.lsp.extension.AbstractLegacyParserLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.CommandConsumer;
import org.finos.legend.engine.ide.lsp.extension.CompileResult;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.engine.ide.lsp.extension.SourceInformationUtil;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult.Type;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperMappingBuilder;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensionLoader;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.language.pure.grammar.from.mapping.MappingParser;
import org.finos.legend.engine.plan.generation.extension.PlanGeneratorExtension;
import org.finos.legend.engine.plan.generation.transformers.PlanTransformer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.AssociationMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.ClassMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.ClassMappingVisitor;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.EnumerationMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.Mapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.MappingInclude;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.OperationClassMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.PropertyMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.PropertyMappingVisitor;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.aggregationAware.AggregationAwareClassMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.aggregationAware.AggregationAwarePropertyMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.mappingTest.MappingTest;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.mappingTest.MappingTestSuite;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.mappingTest.MappingTest_Legacy;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.mappingTest.StoreTestData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.xStore.XStoreAssociationMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.xStore.XStorePropertyMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.modelToModel.mapping.PureInstanceClassMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.modelToModel.mapping.PurePropertyMapping;
import org.finos.legend.engine.protocol.pure.v1.model.test.AtomicTest;
import org.finos.legend.engine.protocol.pure.v1.model.test.TestSuite;
import org.finos.legend.engine.pure.code.core.PureCoreExtensionLoader;
import org.finos.legend.engine.test.runner.mapping.MappingTestRunner;
import org.finos.legend.engine.test.runner.mapping.RichMappingTestResult;
import org.finos.legend.pure.generated.Root_meta_analytics_mapping_modelCoverage_MappingModelCoverageAnalysisResult;
import org.finos.legend.pure.generated.Root_meta_pure_extension_Extension;
import org.finos.legend.pure.generated.Root_meta_pure_mapping_metamodel_MappingTestSuite;
import org.finos.legend.pure.generated.core_analytics_mapping_modelCoverage_analytics;
import org.finos.legend.pure.generated.core_analytics_mapping_modelCoverage_serializer;
import org.finos.legend.pure.m3.coreinstance.meta.external.store.model.PureInstanceSetImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.AssociationImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.MergeOperationSetImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.SetImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.aggregationAware.AggregateSetImplementationContainer;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.aggregationAware.AggregateSpecification;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.aggregationAware.AggregationAwareSetImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.aggregationAware.AggregationFunctionSpecification;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.aggregationAware.GroupByFunctionSpecification;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

/**
 * Extension for the Mapping grammar.
 */
public class MappingLSPGrammarExtension extends AbstractLegacyParserLSPGrammarExtension
{
    private static final String RUN_LEGACY_TESTS_COMMAND_ID = "legend.mapping.runLegacyTests";
    private static final String RUN_LEGACY_TESTS_COMMAND_TITLE = "Run legacy tests";

    private static final String RUN_LEGACY_TEST_COMMAND_ID = "legend.mapping.runLegacyTest";
    private static final String RUN_LEGACY_TEST_COMMAND_TITLE = "Run legacy test";
    private static final String LEGACY_TEST_ID = "legend.mapping.legacyTestId";
    private static final String ANALYZE_MAPPING_MODEL_COVERAGE_COMMAND_ID = "legend.mapping.analyzeMappingModelCoverage";

    private static final ImmutableList<String> STORE_OBJECT_TRIGGERS = Lists.immutable.with("~");

    private static final ImmutableList<String> STORE_OBJECT_SUGGESTIONS = Lists.immutable.with("primaryKey", "mainTable");

    private static final ImmutableList<String> BOILERPLATE_SUGGESTIONS = Lists.immutable.with(
            "Mapping package::path::mappingName\n" +
                    "( /*Mapping contains the business logic relating your (exposed) Class to your underlying store objects (tables/views).*/\n" +
                    "  *package::path::className: Relational\n" +
                    "  {\n" +
                    "    ~primaryKey\n" +
                    "  (\n" +
                    "    [package::path::storeName]schemaName.TableName1.column1\n" +
                    "  )\n" +
                    "    ~mainTable [package::path::storeName]schemaName.TableName1\n" +
                    "    attribute1: [package::path::storeName]schemaName.TableName1.column1,\n" +
                    "    attribute2: [package::path::storeName]schemaName.TableName1.column2,\n" +
                    "    attribute3: multiply([package::path::storeName]schemaName.TableName1.column1, [package::path::storeName]schema.TableName1.column1)\n" +
                    "  }\n" +
                    ")\n");

    private final ListIterable<String> keywords;

    public MappingLSPGrammarExtension()
    {
        super(MappingParser.newInstance(PureGrammarParserExtensions.fromAvailableExtensions()));
        this.keywords = findKeywords();
    }

    @Override
    public Iterable<? extends String> getKeywords()
    {
        return this.keywords;
    }

    @Override
    protected List<? extends TestSuite> getTestSuites(PackageableElement element)
    {
        if (element instanceof Mapping)
        {
            List<MappingTestSuite> testSuites = ((Mapping) element).testSuites;
            return (testSuites == null) ? Lists.fixedSize.empty() : testSuites;
        }
        return super.getTestSuites(element);
    }

    @Override
    protected void collectCommands(SectionState sectionState, PackageableElement element, CommandConsumer consumer)
    {
        super.collectCommands(sectionState, element, consumer);
        if (element instanceof Mapping)
        {
            Mapping mapping = (Mapping) element;
            if ((mapping.tests != null) && !mapping.tests.isEmpty())
            {
                consumer.accept(RUN_LEGACY_TESTS_COMMAND_ID, RUN_LEGACY_TESTS_COMMAND_TITLE, mapping.sourceInformation);
                mapping.tests.forEach(t -> consumer.accept(RUN_LEGACY_TEST_COMMAND_ID, RUN_LEGACY_TEST_COMMAND_TITLE, t.sourceInformation, Collections.singletonMap(LEGACY_TEST_ID, t.name)));
            }
        }
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs)
    {
        switch (commandId)
        {
            case RUN_LEGACY_TESTS_COMMAND_ID:
            {
                return runLegacyMappingTests(section, entityPath, null);
            }
            case RUN_LEGACY_TEST_COMMAND_ID:
            {
                return runLegacyMappingTests(section, entityPath, executableArgs.get(LEGACY_TEST_ID));
            }
            case ANALYZE_MAPPING_MODEL_COVERAGE_COMMAND_ID:
            {
                return analyzeMappingModelCoverage(section, entityPath);
            }
            default:
            {
                return super.execute(section, entityPath, commandId, executableArgs);
            }
        }
    }

    private List<? extends LegendExecutionResult> runLegacyMappingTests(SectionState section, String entityPath, String testName)
    {
        PackageableElement element = getParseResult(section).getElement(entityPath);
        if (!(element instanceof Mapping))
        {
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, "Unable to find mapping " + entityPath, SourceInformationUtil.toLocation(element.sourceInformation)));
        }
        Mapping mapping = (Mapping) element;
        List<MappingTest_Legacy> tests = getLegacyMappingTests(mapping, testName);
        if (tests.isEmpty())
        {
            String message = (testName == null) ?
                    ("Unable to find legacy tests for mapping " + entityPath) :
                    ("Unable to find legacy test " + testName + " for mapping " + entityPath);
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, Type.ERROR, message, SourceInformationUtil.toLocation(element.sourceInformation)));
        }

        CompileResult compileResult = getCompileResult(section);
        if (compileResult.hasEngineException())
        {
            return Collections.singletonList(errorResult(compileResult.getCompileErrorResult(), entityPath));
        }

        PureModel pureModel = compileResult.getPureModel();
        MutableList<? extends Root_meta_pure_extension_Extension> routerExtensions = PureCoreExtensionLoader.extensions().flatCollect(e -> e.extraPureCoreExtensions(pureModel.getExecutionSupport()));
        MutableList<PlanTransformer> planTransformers = Iterate.flatCollect(ServiceLoader.load(PlanGeneratorExtension.class), PlanGeneratorExtension::getExtraPlanTransformers, Lists.mutable.empty());
        MutableList<LegendExecutionResult> results = Lists.mutable.empty();
        tests.forEach(test ->
        {
            try
            {
                MappingTestRunner testRunner = new MappingTestRunner(pureModel, entityPath, test, getPlanExecutor(), routerExtensions, planTransformers);
                RichMappingTestResult result = testRunner.setupAndRunTest();
                TextLocation location = SourceInformationUtil.toLocation(test.sourceInformation);
                switch (result.getResult())
                {
                    case SUCCESS:
                    {
                        results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, test.name), Type.SUCCESS, entityPath + "." + result.getTestName() + ": SUCCESS", location));
                        break;
                    }
                    case FAILURE:
                    {
                        StringBuilder builder = new StringBuilder(entityPath).append('.').append(result.getTestName()).append(": FAILURE");
                        if (result.getExpected().isPresent() && result.getActual().isPresent())
                        {
                            builder.append("\nexpected: ").append(result.getExpected().get());
                            builder.append("\nactual:   ").append(result.getActual().get());
                        }
                        results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, test.name), Type.FAILURE, builder.toString(), location));
                        break;
                    }
                    case ERROR:
                    {
                        StringWriter writer = new StringWriter().append(entityPath).append('.').append(result.getTestName()).append(": ERROR");
                        if (result.getException() != null)
                        {
                            try (PrintWriter pw = new PrintWriter(writer.append("\n")))
                            {
                                result.getException().printStackTrace(pw);
                            }
                        }
                        results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, test.name), Type.ERROR, writer.toString(), location));
                        break;
                    }
                    default:
                    {
                        results.add(LegendExecutionResult.newResult(Lists.mutable.of(entityPath, test.name), Type.SUCCESS, entityPath + "." + result.getTestName() + ": " + result.getResult().name() + " (unhandled result type)", location));
                    }
                }
            }
            catch (Exception e)
            {
                results.add(errorResult(e, entityPath));
            }
        });
        return results;
    }

    private List<? extends LegendExecutionResult> analyzeMappingModelCoverage(SectionState section, String entityPath)
    {
        PackageableElement element = getParseResult(section).getElement(entityPath);
        CompileResult compileResult = this.getCompileResult(section);

        try
        {
            final PureModel pureModel = compileResult.getPureModel();
            final org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.Mapping mapping = pureModel.getMapping(entityPath);
            Root_meta_analytics_mapping_modelCoverage_MappingModelCoverageAnalysisResult analysisResult = core_analytics_mapping_modelCoverage_analytics
                    .Root_meta_analytics_mapping_modelCoverage_analyze_Mapping_1__Boolean_1__Boolean_1__Boolean_1__MappingModelCoverageAnalysisResult_1_(
                            mapping,
                            false,
                            false,
                            false,
                            pureModel.getExecutionSupport()
                    );
            String result = core_analytics_mapping_modelCoverage_serializer
                    .Root_meta_analytics_mapping_modelCoverage_serialization_json_getSerializedMappingModelCoverageAnalysisResult_MappingModelCoverageAnalysisResult_1__String_1_(
                            analysisResult,
                            pureModel.getExecutionSupport()
                    );
            return Collections.singletonList(
                    LegendExecutionResult.newResult(
                            entityPath,
                            Type.SUCCESS,
                            result,
                            SourceInformationUtil.toLocation(element.sourceInformation)
                    )
            );
        }
        catch (Exception e)
        {
            return Collections.singletonList(errorResult(e, entityPath));
        }
    }

    private List<MappingTest_Legacy> getLegacyMappingTests(Mapping mapping, String testName)
    {
        List<MappingTest_Legacy> tests = mapping.tests;
        if (tests == null)
        {
            return Collections.emptyList();
        }
        if (testName == null)
        {
            return tests;
        }
        return ListIterate.select(tests, t -> testName.equals(t.name));
    }

    private static ListIterable<String> findKeywords()
    {
        MutableSet<String> keywords = Sets.mutable.with("Mapping", "MappingTests", "include");
        PureGrammarParserExtensionLoader.extensions().forEach(ext -> ext.getExtraMappingElementParsers().forEach(p -> keywords.add(p.getElementTypeName())));
        return Lists.immutable.withAll(keywords);
    }

    @Override
    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        String codeLine = section.getSection().getLineUpTo(location);
        List<LegendCompletion> legendCompletions = Lists.mutable.empty();

        if (codeLine.isEmpty())
        {
            BOILERPLATE_SUGGESTIONS.collect(s -> new LegendCompletion("Mapping boilerplate", s.replaceAll("\n", System.lineSeparator())), legendCompletions);
        }
        else if (STORE_OBJECT_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            STORE_OBJECT_SUGGESTIONS.collect(s -> new LegendCompletion("Store object type", s), legendCompletions);
        }

        return CompositeIterable.with(legendCompletions, this.computeCompletionsForSupportedTypes(section, location, Set.of("Mapping")));
    }

    @Override
    protected Stream<Optional<LegendReferenceResolver>> getReferenceResolvers(SectionState section, PackageableElement packageableElement, Optional<CoreInstance> coreInstance)
    {
        Mapping mapping = (Mapping) packageableElement;
        GlobalState state = section.getDocumentState().getGlobalState();
        Stream<Optional<LegendReferenceResolver>> classMappingReferences = mapping.classMappings
                .stream()
                .flatMap(Functions.bind(this::toReferences, state));

        Stream<Optional<LegendReferenceResolver>> includedMappingReferences = mapping.includedMappings
                .stream()
                .map(this::toReference);

        Stream<Optional<LegendReferenceResolver>> associationMappingReferences = mapping.associationMappings
                .stream()
                .flatMap(Functions.bind(this::toReferences, state));

        Stream<Optional<LegendReferenceResolver>> enumerationMappingReferences = mapping.enumerationMappings
                .stream()
                .flatMap(this::toReferences);

        Stream<Optional<LegendReferenceResolver>> testSuiteReferences = getTestSuites(mapping)
                .stream()
                .flatMap(this::toReferences);

        Stream<Optional<LegendReferenceResolver>> coreReferences = Stream.empty();
        if (coreInstance.isPresent())
        {
            coreReferences = toReferences((org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.Mapping) coreInstance.get(), state);
        }

        return Stream.of(classMappingReferences, includedMappingReferences, associationMappingReferences, enumerationMappingReferences, coreReferences, testSuiteReferences)
                .flatMap(Functions.identity());
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(ClassMapping classMapping, GlobalState state)
    {
        Optional<LegendReferenceResolver> legendReferenceResolver = LegendReferenceResolver.newReferenceResolver(classMapping.classSourceInformation, c -> c.resolveClass(classMapping._class, classMapping.classSourceInformation));

        Stream<Optional<LegendReferenceResolver>> otherReferences = classMapping.accept(new ClassMappingVisitor<>()
        {
            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(ClassMapping classMapping)
            {
                return state.findGrammarExtensionThatImplements(MappingLSPGrammarProvider.class)
                        .flatMap(x -> x.getClassMappingReferences(classMapping, state));
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(OperationClassMapping operationClassMapping)
            {
                return Stream.empty();
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(PureInstanceClassMapping pureInstanceClassMapping)
            {
                Optional<LegendReferenceResolver> srcReference = LegendReferenceResolver.newReferenceResolver(
                        pureInstanceClassMapping.sourceClassSourceInformation,
                        x -> x.resolveClass(pureInstanceClassMapping.srcClass, pureInstanceClassMapping.sourceClassSourceInformation)
                );

                Stream<Optional<LegendReferenceResolver>> propReferences = pureInstanceClassMapping.propertyMappings
                        .stream()
                        .flatMap(Functions.bind(MappingLSPGrammarExtension::propertyMappingToReferences, state));

                return Stream.concat(propReferences, Stream.of(srcReference));
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(AggregationAwareClassMapping aggregationAwareClassMapping)
            {
                return Stream.empty();
            }
        });

        return Stream.concat(Stream.of(legendReferenceResolver), otherReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(AssociationMapping associationMapping, GlobalState state)
    {
        Optional<LegendReferenceResolver> associationReference = LegendReferenceResolver.newReferenceResolver(
                associationMapping.association.sourceInformation,
                x -> x.resolveAssociation(associationMapping.association.path, associationMapping.association.sourceInformation)
        );

        Stream<Optional<LegendReferenceResolver>> associationMappingReferences;
        if (associationMapping instanceof XStoreAssociationMapping)
        {
            associationMappingReferences = ((XStoreAssociationMapping) associationMapping).propertyMappings
                    .stream()
                    .flatMap(Functions.bind(MappingLSPGrammarExtension::propertyMappingToReferences, state));
        }

        else
        {
            associationMappingReferences = state.findGrammarExtensionThatImplements(MappingLSPGrammarProvider.class)
                    .flatMap(x -> x.getAssociationMappingReferences(associationMapping, state));
        }

        return Stream.concat(Stream.of(associationReference), associationMappingReferences);
    }

    public static Stream<Optional<LegendReferenceResolver>> propertyMappingToReferences(PropertyMapping propertyMapping, GlobalState state)
    {
        Optional<LegendReferenceResolver> propReference = propertyMappingToReference(propertyMapping);
        Stream<Optional<LegendReferenceResolver>> otherReferences = propertyMapping.accept(new PropertyMappingVisitor<>()
        {
            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(PropertyMapping propertyMapping)
            {
                return state.findGrammarExtensionThatImplements(MappingLSPGrammarProvider.class)
                        .flatMap(x -> x.getPropertyMappingReferences(propertyMapping));
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(PurePropertyMapping purePropertyMapping)
            {
                return Stream.empty();
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(XStorePropertyMapping xStorePropertyMapping)
            {
                return Stream.empty();
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(AggregationAwarePropertyMapping aggregationAwarePropertyMapping)
            {
                return Stream.empty();
            }
        });
        return Stream.concat(Stream.of(propReference), otherReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(EnumerationMapping enumerationMapping)
    {
        Optional<LegendReferenceResolver> enumerationMappingReference = LegendReferenceResolver.newReferenceResolver(
                enumerationMapping.enumeration.sourceInformation,
                x -> x.resolveEnumeration(enumerationMapping.enumeration.path, enumerationMapping.enumeration.sourceInformation));

        Stream<Optional<LegendReferenceResolver>> enumValueReferences = enumerationMapping.enumValueMappings
                .stream()
                .map(enumValueMapping ->
                        LegendReferenceResolver.newReferenceResolver(
                                enumValueMapping.enumValueSourceInformation,
                                x -> x.resolveEnumValue(enumerationMapping.enumeration.path, enumValueMapping.enumValue, enumValueMapping.enumValueSourceInformation, enumValueMapping.sourceInformation)
                        ));

        return Stream.concat(
                Stream.of(enumerationMappingReference),
                enumValueReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(TestSuite testSuite)
    {
        return testSuite.tests.stream().flatMap(this::toReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(AtomicTest test)
    {
        if (!(test instanceof MappingTest))
        {
            return Stream.empty();
        }

        MappingTest mappingtest = (MappingTest) test;
        return mappingtest.storeTestData.stream().flatMap(this::toReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(StoreTestData storeTestData)
    {
        Optional<LegendReferenceResolver> storeReference = LegendReferenceResolver.newReferenceResolver(
                storeTestData.store.sourceInformation,
                x -> x.resolveStore(storeTestData.store.path));

        return Stream.of(storeReference);
    }

    private Optional<LegendReferenceResolver> toReference(MappingInclude mappingInclude)
    {
        return LegendReferenceResolver.newReferenceResolver(
            mappingInclude.sourceInformation,
            x -> x.resolvePackageableElement(mappingInclude.getFullName(), mappingInclude.sourceInformation)
        );
    }

    private static Optional<LegendReferenceResolver> propertyMappingToReference(PropertyMapping propertyMapping)
    {
        return LegendReferenceResolver.newReferenceResolver(
                propertyMapping.property.sourceInformation,
                x -> HelperMappingBuilder.getMappedProperty(propertyMapping, x)
        );
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.Mapping mapping, GlobalState state)
    {
        Stream<Optional<LegendReferenceResolver>> setImplementationReferences = StreamSupport.stream(mapping._classMappings().spliterator(), false)
                .flatMap(Functions.bind(this::toReferences, state));
        Stream<Optional<LegendReferenceResolver>> associationImplementationReferences = StreamSupport.stream(mapping._associationMappings().spliterator(), false)
                .flatMap(this::toReferences);
        Stream<Optional<LegendReferenceResolver>> testSuiteReferences = StreamSupport.stream(mapping._tests().spliterator(), false)
                .flatMap(testSuite -> toReferences((Root_meta_pure_mapping_metamodel_MappingTestSuite) testSuite));
        return Stream.concat(Stream.concat(setImplementationReferences, associationImplementationReferences), testSuiteReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(Root_meta_pure_mapping_metamodel_MappingTestSuite testSuite)
    {
        return FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(testSuite._query()));
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(SetImplementation setImplementation, GlobalState state)
    {
        if (setImplementation == null)
        {
            return Stream.empty();
        }
        if (setImplementation instanceof PureInstanceSetImplementation)
        {
            PureInstanceSetImplementation pureInstanceSetImplementation = (PureInstanceSetImplementation) setImplementation;
            Stream<Optional<LegendReferenceResolver>> filterReferences = FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(pureInstanceSetImplementation._filter()));
            Stream<Optional<LegendReferenceResolver>> propertyMappingReferences = StreamSupport.stream(pureInstanceSetImplementation._propertyMappings().spliterator(), false)
                    .flatMap(MappingLSPGrammarExtension::toReferences);
            return Stream.concat(filterReferences, propertyMappingReferences);
        }

        if (setImplementation instanceof AggregationAwareSetImplementation)
        {
            AggregationAwareSetImplementation aggregationAwareSetImplementation = (AggregationAwareSetImplementation) setImplementation;
            Stream<Optional<LegendReferenceResolver>> mainSetImplementationReferences = toReferences(aggregationAwareSetImplementation._mainSetImplementation(), state);
            Stream<Optional<LegendReferenceResolver>> propertyMappingReferences = StreamSupport.stream(aggregationAwareSetImplementation._propertyMappings().spliterator(), false)
                    .flatMap(MappingLSPGrammarExtension::toReferences);
            Stream<Optional<LegendReferenceResolver>> aggregateSetImplementationReferences = StreamSupport.stream(aggregationAwareSetImplementation._aggregateSetImplementations().spliterator(), false)
                    .flatMap(Functions.bind(this::toReferences, state));
            return Stream.of(mainSetImplementationReferences, propertyMappingReferences, aggregateSetImplementationReferences)
                    .flatMap(java.util.function.Function.identity());
        }

        if (setImplementation instanceof MergeOperationSetImplementation)
        {
            MergeOperationSetImplementation mergeOperationSetImplementation = (MergeOperationSetImplementation) setImplementation;
            return FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(mergeOperationSetImplementation._validationFunction()));
        }

        return state.findGrammarExtensionThatImplements(MappingLSPGrammarProvider.class)
                .flatMap(x -> x.getSetImplementationReferences(setImplementation));
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(AggregateSetImplementationContainer aggregateSetImplementationContainer, GlobalState state)
    {
        if (aggregateSetImplementationContainer == null)
        {
            return Stream.empty();
        }
        Stream<Optional<LegendReferenceResolver>> setImplementationReferences = toReferences(aggregateSetImplementationContainer._setImplementation(), state);
        Stream<Optional<LegendReferenceResolver>> aggregateSpecificationReferences = toReferences(aggregateSetImplementationContainer._aggregateSpecification());
        return Stream.concat(setImplementationReferences, aggregateSpecificationReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(AggregateSpecification aggregateSpecification)
    {
        if (aggregateSpecification == null)
        {
            return Stream.empty();
        }
        Stream<Optional<LegendReferenceResolver>> groupByFunctionReferences = StreamSupport.stream(aggregateSpecification._groupByFunctions().spliterator(), false)
                .flatMap(this::toReferences);
        Stream<Optional<LegendReferenceResolver>> aggregateValueReferences = StreamSupport.stream(aggregateSpecification._aggregateValues().spliterator(), false)
                .flatMap(this::toReferences);
        return Stream.concat(groupByFunctionReferences, aggregateValueReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(GroupByFunctionSpecification groupByFunctionSpecification)
    {
        if (groupByFunctionSpecification == null)
        {
            return Stream.empty();
        }
        return FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(groupByFunctionSpecification._groupByFn()));
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(AggregationFunctionSpecification aggregationFunctionSpecification)
    {
        if (aggregationFunctionSpecification == null)
        {
            return Stream.empty();
        }
        Stream<Optional<LegendReferenceResolver>> mapFnReferences = FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(aggregationFunctionSpecification._mapFn()));
        Stream<Optional<LegendReferenceResolver>> aggregateFnReferences = FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(aggregationFunctionSpecification._aggregateFn()));
        return Stream.concat(mapFnReferences, aggregateFnReferences);
    }

    private Stream<Optional<LegendReferenceResolver>> toReferences(AssociationImplementation associationImplementation)
    {
        if (associationImplementation == null)
        {
            return Stream.empty();
        }
        return StreamSupport.stream(associationImplementation._propertyMappings().spliterator(), false)
                .flatMap(MappingLSPGrammarExtension::toReferences);
    }

    public static Stream<Optional<LegendReferenceResolver>> toReferences(org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.PropertyMapping propertyMapping)
    {
        if (propertyMapping == null)
        {
            return Stream.empty();
        }
        if (propertyMapping instanceof org.finos.legend.pure.m3.coreinstance.meta.external.store.model.PurePropertyMapping)
        {
            org.finos.legend.pure.m3.coreinstance.meta.external.store.model.PurePropertyMapping purePropertyMapping = (org.finos.legend.pure.m3.coreinstance.meta.external.store.model.PurePropertyMapping) propertyMapping;
            return FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(purePropertyMapping._transform()));
        }

        if (propertyMapping instanceof org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.xStore.XStorePropertyMapping)
        {
            org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.xStore.XStorePropertyMapping xStorePropertyMapping = (org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.xStore.XStorePropertyMapping) propertyMapping;
            return FUNCTION_EXPRESSION_NAVIGATOR.findReferences(Optional.ofNullable(xStorePropertyMapping._crossExpression()));
        }

        return Stream.empty();
    }
}
