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

package org.finos.legend.engine.ide.lsp.extension.relational;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.block.factory.Functions;
import org.finos.legend.engine.ide.lsp.extension.AbstractSectionParserLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.CommandConsumer;
import org.finos.legend.engine.ide.lsp.extension.CompileResult;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.engine.ide.lsp.extension.SourceInformationUtil;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.connection.ConnectionLSPGrammarProvider;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.mapping.MappingLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.mapping.MappingLSPGrammarProvider;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperRelationalBuilder;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.PureModel;
import org.finos.legend.engine.language.pure.grammar.from.RelationalGrammarParserExtension;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.Connection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.AssociationMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.ClassMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.mapping.PropertyMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.connection.RelationalDatabaseConnection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.mapping.FilterMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.mapping.RelationalAssociationMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.mapping.RelationalClassMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.mapping.RelationalPropertyMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.mapping.RootRelationalClassMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.mapping.TablePtr;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Column;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.ColumnMapping;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Database;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Filter;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Join;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Schema;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.View;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.operation.DynaFunc;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.operation.ElementWithJoins;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.operation.JoinPointer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.operation.RelationalOperationElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.operation.TableAliasColumn;
import org.finos.legend.pure.generated.core_relational_relational_autogeneration_relationalToPure;
import org.finos.legend.pure.m2.relational.M2RelationalPaths;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.SetImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.relational.mapping.RelationalInstanceSetImplementation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension for the Relational grammar.
 */
public class RelationalLSPGrammarExtension extends AbstractSectionParserLSPGrammarExtension implements MappingLSPGrammarProvider, ConnectionLSPGrammarProvider
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RelationalLSPGrammarExtension.class);

    static final String GENERATE_MODEL_MAPPING_COMMAND_ID = "legend.service.generateModel";
    private static final String GENERATE_MODEL_MAPPING_COMMAND_TITLE = "Generate sample models";

    private static final List<String> KEYWORDS = List.of("Database", "Schema", "Table", "View", "include", "Join", "Filter");

    private static final ImmutableList<String> SCHEMA_TRIGGERS = Lists.immutable.with("Schema ");

    private static final ImmutableList<String> SCHEMA_SUGGESTIONS = Lists.immutable.with(
            "schemaName\n" +
                    "(\n" +
                    " Table TableName1(column1 INT PRIMARY KEY, column2 DATE)\n" +
                    " Table TableName2(column3 VARCHAR(10) PRIMARY KEY)\n" +
                    ")\n"
    );

    private static final ImmutableList<String> TABLE_TRIGGERS = Lists.immutable.with("Table ");

    private static final ImmutableList<String> TABLE_SUGGESTIONS = Lists.immutable.with(
            "TableName1(column1 INT PRIMARY KEY, column2 DATE)\n"
    );

    private static final ImmutableList<String> VIEW_TRIGGERS = Lists.immutable.with("View ");

    private static final ImmutableList<String> VIEW_SUGGESTIONS = Lists.immutable.with(
            "viewName\n" +
                    "(\n" +
                    "  field1: table1.column1, \n" +
                    "  field2: table2.column2,\n" +
                    "  field3: table1.column1 + table2.column2\n" +
                    ")\n"
    );

    private static final ImmutableList<String> JOIN_TRIGGERS = Lists.immutable.with("Join ");

    private static final ImmutableList<String> JOIN_SUGGESTIONS = Lists.immutable.with(
            "joinName(table1.column1 = table2.column2)"
    );

    private static final ImmutableList<String> FILTER_TRIGGERS = Lists.immutable.with("Filter ");

    private static final ImmutableList<String> FILTER_SUGGESTIONS = Lists.immutable.with(
            "filterName(table1.column1 > 12)"
    );

    private static final ImmutableList<String> BOILERPLATE_SUGGESTIONS = Lists.immutable.with(
            "Database package::path::storeName\n" +
                    "(\n" +
                    "  Schema schemaName\n" +
                    "  (\n" +
                    "   Table TableName1(column1 INT PRIMARY KEY, column2 DATE)\n" +
                    "   Table TableName2(column3 VARCHAR(10) PRIMARY KEY)\n" +
                    "  )\n" +
                    ")\n"
    );

    @Override
    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        String codeLine = section.getSection().getLine(location.getLine()).substring(0, location.getColumn());
        List<LegendCompletion> legendCompletions = Lists.mutable.empty();

        if (codeLine.isEmpty())
        {
            return BOILERPLATE_SUGGESTIONS.collect(s -> new LegendCompletion("Relational boilerplate", s.replaceAll("\n", System.lineSeparator())));
        }

        if (SCHEMA_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            SCHEMA_SUGGESTIONS.collect(s -> new LegendCompletion("Schema definition", s.replaceAll("\n", System.lineSeparator())), legendCompletions);
        }
        if (TABLE_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            TABLE_SUGGESTIONS.collect(s -> new LegendCompletion("Table definition", s.replaceAll("\n", System.lineSeparator())), legendCompletions);
        }
        if (VIEW_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            VIEW_SUGGESTIONS.collect(s -> new LegendCompletion("View definition", s.replaceAll("\n", System.lineSeparator())), legendCompletions);
        }
        if (JOIN_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            JOIN_SUGGESTIONS.collect(s -> new LegendCompletion("Join definition", s.replaceAll("\n", System.lineSeparator())), legendCompletions);
        }
        if (FILTER_TRIGGERS.anySatisfy(codeLine::endsWith))
        {
            FILTER_SUGGESTIONS.collect(s -> new LegendCompletion("Filter definition", s.replaceAll("\n", System.lineSeparator())), legendCompletions);
        }
        return legendCompletions;
    }

    public RelationalLSPGrammarExtension()
    {
        super(RelationalGrammarParserExtension.NAME, new RelationalGrammarParserExtension());
    }

    @Override
    public String getName()
    {
        return "Relational";
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
        if (element instanceof Database)
        {
            Database database = (Database) element;
            consumer.accept(GENERATE_MODEL_MAPPING_COMMAND_ID, GENERATE_MODEL_MAPPING_COMMAND_TITLE, database.sourceInformation);
        }
    }

    @Override
    public Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs)
    {
        return GENERATE_MODEL_MAPPING_COMMAND_ID.equals(commandId) ?
                generateModelsFromDatabaseSpecification(section, entityPath) :
                super.execute(section, entityPath, commandId, executableArgs);
    }

    private Iterable<? extends LegendExecutionResult> generateModelsFromDatabaseSpecification(SectionState section, String entityPath)
    {
        PackageableElement element = getParseResult(section).getElement(entityPath);
        TextLocation location = SourceInformationUtil.toLocation(element.sourceInformation);
        if (!(element instanceof Database))
        {
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.ERROR, "Unable to find database " + entityPath, location));
        }

        CompileResult compileResult = getCompileResult(section);
        if (compileResult.hasException())
        {
            return Collections.singletonList(errorResult(compileResult.getException(), entityPath));
        }

        try
        {
            PureModel pureModel = compileResult.getPureModel();
            org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Database database = (org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Database) pureModel.getStore(entityPath);
            String targetPackage = element._package;
            String result = core_relational_relational_autogeneration_relationalToPure.Root_meta_relational_transform_autogen_classesAssociationsAndMappingFromDatabase_Database_1__String_1__String_1_(database, targetPackage, pureModel.getExecutionSupport());
            PureModelContextData pmcd = deserializePMCD(result);
            String code = toGrammar(pmcd);
            String warning = "***WARNING***\n" +
                    "These models and mappings are intended only as examples.\n" +
                    "They should not be considered a replacement for thoughtful modeling.\n" +
                    "Please review carefully before making any use of them.\n" +
                    "***WARNING***\n\n\n";
            return Collections.singletonList(LegendExecutionResult.newResult(entityPath, LegendExecutionResult.Type.SUCCESS, warning + code, location));
        }
        catch (Exception e)
        {
            return Collections.singletonList(errorResult(e, entityPath));
        }
    }

    @Override
    protected void forEachChild(PackageableElement element, Consumer<LegendDeclaration> consumer)
    {
        if (element instanceof Database)
        {
            Database db = (Database) element;
            db.schemas.forEach(s -> consumer.accept(getDeclaration(s)));
            db.joins.forEach(j -> consumer.accept(getDeclaration(j)));
            db.filters.forEach(f -> consumer.accept(getDeclaration(f)));
        }
    }

    private LegendDeclaration getDeclaration(Schema schema)
    {
        if (!isValidSourceInfo(schema.sourceInformation))
        {
            LOGGER.warn("Invalid source information for schema {}", schema.name);
            return null;
        }

        LegendDeclaration.Builder builder = LegendDeclaration.builder()
                .withIdentifier(schema.name)
                .withClassifier(M2RelationalPaths.Schema)
                .withLocation(SourceInformationUtil.toLocation(schema.sourceInformation));
        schema.tables.forEach(t -> addChildIfNonNull(builder, getDeclaration(t)));
        schema.views.forEach(v -> addChildIfNonNull(builder, getDeclaration(v)));
        return builder.build();
    }

    private LegendDeclaration getDeclaration(Table table)
    {
        if (!isValidSourceInfo(table.sourceInformation))
        {
            LOGGER.warn("Invalid source information for table {}", table.name);
            return null;
        }

        LegendDeclaration.Builder builder = LegendDeclaration.builder()
                .withIdentifier(table.name)
                .withClassifier(M2RelationalPaths.Table)
                .withLocation(SourceInformationUtil.toLocation(table.sourceInformation));
        table.columns.forEach(c -> addChildIfNonNull(builder, getDeclaration(c)));
        return builder.build();
    }

    private LegendDeclaration getDeclaration(Column column)
    {
        if (!isValidSourceInfo(column.sourceInformation))
        {
            LOGGER.warn("Invalid source information for column {}", column.name);
            return null;
        }
        return LegendDeclaration.builder()
                .withIdentifier(column.name)
                .withClassifier(M2RelationalPaths.Column)
                .withLocation(SourceInformationUtil.toLocation(column.sourceInformation))
                .build();
    }

    private LegendDeclaration getDeclaration(View view)
    {
        if (!isValidSourceInfo(view.sourceInformation))
        {
            LOGGER.warn("Invalid source information for view {}", view.name);
            return null;
        }

        LegendDeclaration.Builder builder = LegendDeclaration.builder()
                .withIdentifier(view.name)
                .withClassifier(M2RelationalPaths.Table)
                .withLocation(SourceInformationUtil.toLocation(view.sourceInformation));
        view.columnMappings.forEach(c -> addChildIfNonNull(builder, getDeclaration(c)));
        return builder.build();
    }

    private LegendDeclaration getDeclaration(ColumnMapping columnMapping)
    {
        if (!isValidSourceInfo(columnMapping.sourceInformation))
        {
            LOGGER.warn("Invalid source information for column mapping {}", columnMapping.name);
            return null;
        }
        return LegendDeclaration.builder()
                .withIdentifier(columnMapping.name)
                .withClassifier("meta::relational::mapping::ColumnMapping")
                .withLocation(SourceInformationUtil.toLocation(columnMapping.sourceInformation))
                .build();
    }

    private LegendDeclaration getDeclaration(Join join)
    {
        if (!isValidSourceInfo(join.sourceInformation))
        {
            LOGGER.warn("Invalid source information for join {}", join.name);
            return null;
        }
        return LegendDeclaration.builder()
                .withIdentifier(join.name)
                .withClassifier(M2RelationalPaths.Join)
                .withLocation(SourceInformationUtil.toLocation(join.sourceInformation))
                .build();
    }

    private LegendDeclaration getDeclaration(Filter filter)
    {
        if (!isValidSourceInfo(filter.sourceInformation))
        {
            LOGGER.warn("Invalid source information for filter {}", filter.name);
            return null;
        }
        return LegendDeclaration.builder()
                .withIdentifier(filter.name)
                .withClassifier(M2RelationalPaths.Filter)
                .withLocation(SourceInformationUtil.toLocation(filter.sourceInformation))
                .build();
    }

    @Override
    public Stream<LegendReferenceResolver> getClassMappingReferences(ClassMapping mapping, GlobalState state)
    {
        if (mapping instanceof RelationalClassMapping)
        {
            return toReferences((RelationalClassMapping) mapping, state);
        }
        return Stream.empty();
    }

    @Override
    public Stream<LegendReferenceResolver> getAssociationMappingReferences(AssociationMapping associationMapping, GlobalState state)
    {
        if (associationMapping instanceof RelationalAssociationMapping)
        {
            return toReferences((RelationalAssociationMapping) associationMapping, state);
        }
        return Stream.empty();
    }

    @Override
    public Stream<LegendReferenceResolver> getSetImplementationReferences(SetImplementation setImplementation)
    {
        if (setImplementation instanceof RelationalInstanceSetImplementation)
        {
            RelationalInstanceSetImplementation relationalInstanceSetImplementation = (RelationalInstanceSetImplementation) setImplementation;
            return StreamSupport.stream(relationalInstanceSetImplementation._propertyMappings().spliterator(), false)
                    .flatMap(MappingLSPGrammarExtension::toReferences);
        }
        return Stream.empty();
    }

    @Override
    public Stream<LegendReferenceResolver> getPropertyMappingReferences(PropertyMapping propertyMapping)
    {
        if (propertyMapping instanceof RelationalPropertyMapping)
        {
            RelationalPropertyMapping relationalPropertyMapping = (RelationalPropertyMapping) propertyMapping;
            return toReferences(relationalPropertyMapping.relationalOperation);
        }
        return Stream.empty();
    }

    private Stream<LegendReferenceResolver> toReferences(RelationalClassMapping relationalClassMapping, GlobalState state)
    {
        Stream<LegendReferenceResolver> properties = relationalClassMapping.propertyMappings.stream()
                .flatMap(Functions.bind(MappingLSPGrammarExtension::propertyMappingToReferences, state));
        Stream<LegendReferenceResolver> pkRef = relationalClassMapping.primaryKey.stream().flatMap(this::toReferences);
        Stream<LegendReferenceResolver> rootRelationalClassMappingReferences = Stream.empty();
        if (relationalClassMapping instanceof RootRelationalClassMapping)
        {
            RootRelationalClassMapping rootRelationalClassMapping = (RootRelationalClassMapping) relationalClassMapping;
            Stream<LegendReferenceResolver> filterReferences = Stream.empty();
            FilterMapping filter = rootRelationalClassMapping.filter;
            if (filter != null)
            {
                LegendReferenceResolver filterReference = LegendReferenceResolver.newReferenceResolver(
                        filter.sourceInformation,
                        x ->
                        {
                            org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Database database = HelperRelationalBuilder.getDatabase(filter.filter.db, filter.sourceInformation, x);
                            return HelperRelationalBuilder.getFilter(database, filter.filter.name, filter.sourceInformation);
                        }
                );

                filterReferences = Stream.concat(
                        Stream.of(filterReference),
                        filter.joins.stream().map(this::toReferences)
                );
            }

            Stream<LegendReferenceResolver> mainTableRef = Stream.of(this.toReference(rootRelationalClassMapping.mainTable))
                    .filter(Objects::nonNull);

            Stream<LegendReferenceResolver> groupByReferences = rootRelationalClassMapping.groupBy
                    .stream()
                    .flatMap(this::toReferences);
            rootRelationalClassMappingReferences = Stream.concat(filterReferences, Stream.concat(mainTableRef, groupByReferences));
        }

        return Stream.concat(properties, Stream.concat(pkRef, rootRelationalClassMappingReferences));
    }

    private Stream<LegendReferenceResolver> toReferences(RelationalAssociationMapping relationalAssociationMapping, GlobalState state)
    {
        return relationalAssociationMapping.propertyMappings
                .stream()
                .flatMap(Functions.bind(MappingLSPGrammarExtension::propertyMappingToReferences, state));
    }

    private LegendReferenceResolver toReference(TablePtr tablePtr)
    {
        if (tablePtr != null)
        {
            return LegendReferenceResolver.newReferenceResolver(
                    tablePtr.sourceInformation,
                    x -> HelperRelationalBuilder.getRelation(tablePtr, x)
            );
        }
        return null;
    }

    private LegendReferenceResolver toReferences(JoinPointer joinPointer)
    {
        return LegendReferenceResolver.newReferenceResolver(
                joinPointer.sourceInformation,
                x -> HelperRelationalBuilder.getJoin(joinPointer, x)
        );
    }

    private Stream<LegendReferenceResolver> toReferences(RelationalOperationElement element)
    {
        // todo ideally we should have a visitor
        if (element instanceof TableAliasColumn)
        {
            TableAliasColumn tableAliasColumn = (TableAliasColumn) element;
            if (tableAliasColumn.table != null)
            {
                LegendReferenceResolver tableRef = toReference(tableAliasColumn.table);

                LegendReferenceResolver colRef = LegendReferenceResolver.newReferenceResolver(tableAliasColumn.sourceInformation,
                        x -> HelperRelationalBuilder.getColumn(
                                HelperRelationalBuilder.getRelation(tableAliasColumn.table, x),
                                tableAliasColumn.column,
                                tableAliasColumn.sourceInformation
                        )
                );

                return Stream.of(tableRef, colRef);
            }
        }
        else if (element instanceof ElementWithJoins)
        {
            ElementWithJoins joins = (ElementWithJoins) element;
            Stream<LegendReferenceResolver> joinReferences = joins.joins.stream().map(this::toReferences);
            return Stream.concat(this.toReferences(joins.relationalElement), joinReferences);
        }
        else if (element instanceof DynaFunc)
        {
            DynaFunc dynaFunc = (DynaFunc) element;
            return dynaFunc.parameters.stream().flatMap(this::toReferences);
        }

        return Stream.empty();
    }

    @Override
    public Stream<LegendReferenceResolver> getConnectionReferences(Connection connection, GlobalState state)
    {
        if (connection instanceof RelationalDatabaseConnection)
        {
            return Stream.of(toReference((RelationalDatabaseConnection) connection));
        }

        return Stream.empty();
    }

    private LegendReferenceResolver toReference(RelationalDatabaseConnection relationalDatabaseConnection)
    {
        return LegendReferenceResolver.newReferenceResolver(relationalDatabaseConnection.elementSourceInformation, s -> s.resolveStore(relationalDatabaseConnection.element, relationalDatabaseConnection.elementSourceInformation));
    }
}
