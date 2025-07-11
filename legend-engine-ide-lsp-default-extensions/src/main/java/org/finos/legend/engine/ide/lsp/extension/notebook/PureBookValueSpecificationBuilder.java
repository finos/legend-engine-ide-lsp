/*
 * Copyright 2025 Goldman Sachs
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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.CompileContext;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperRelationalBuilder;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.ProcessingContext;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.SourceInformationHelper;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.ValueSpecificationBuilder;
import org.finos.legend.engine.protocol.pure.dsl.store.valuespecification.constant.classInstance.RelationStoreAccessor;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.AppliedFunction;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.ValueSpecificationVisitor;
import org.finos.legend.engine.protocol.pure.m3.valuespecification.constant.classInstance.ClassInstance;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Column;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Database;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Schema;
import org.finos.legend.engine.shared.core.ObjectMapperFactory;
import org.finos.legend.pure.generated.Root_meta_protocols_pure_vX_X_X_metamodel_store_relational_Column;
import org.finos.legend.pure.generated.Root_meta_relational_metamodel_Column_Impl;
import org.finos.legend.pure.generated.core_pure_protocol_protocol;
import org.finos.legend.pure.generated.core_relational_relational_protocols_pure_vX_X_X_transfers_metamodel_relational;
import org.finos.legend.pure.generated.core_relational_relational_transform_fromPure_pureToRelational;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.RelationType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Type;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.ValueSpecification;
import org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.DataType;
import org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.relation.Table;
import org.finos.legend.pure.m3.execution.ExecutionSupport;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PureBookValueSpecificationBuilder extends ValueSpecificationBuilder implements ValueSpecificationVisitor<ValueSpecification>
{
    private final Database parsedTargetDuckDBDatabase;
    private final Connection connection;

    public PureBookValueSpecificationBuilder(CompileContext context, MutableList<String> openVariables, ProcessingContext processingContext, Database database, Connection connection)
    {
        super(context, openVariables, processingContext);
        this.parsedTargetDuckDBDatabase = database;
        this.connection = connection;
    }

    private String getConcatenatedSchemaAndTableName(String schemaName, String tableName)
    {
        return (schemaName.equals("default")) ? tableName : schemaName + "." + tableName;
    }

    private String safeCreateSchema(String schemaName)
    {
        return "DROP SCHEMA IF EXISTS " + schemaName + "; CREATE SCHEMA " + schemaName + ";";
    }

    private String safeCreateTableWithColumns(String schemaName, String tableName, MutableList<org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Column> columns)
    {
        String columnNamesAndTypes = columns.stream()
                .map(c -> String.format("%s %s",
                        c._name(),
                        org.finos.legend.pure.generated.core_relational_duckdb_relational_typeConversion.Root_meta_relational_functions_typeConversion_duckDB_dataTypeToSqlTextDuckDB_DataType_1__String_1_(c._type(), getContext().getExecutionSupport())))
                .collect(Collectors.joining(", ", "(", ")"));
        return "CREATE OR REPLACE TABLE " + getConcatenatedSchemaAndTableName(schemaName, tableName) + " " + columnNamesAndTypes + ";";
    }

    private String safeAlterTableWithColumn(String schemaName, String tableName, org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Column column)
    {
        String columnType = org.finos.legend.pure.generated.core_relational_duckdb_relational_typeConversion.Root_meta_relational_functions_typeConversion_duckDB_dataTypeToSqlTextDuckDB_DataType_1__String_1_(column._type(), getContext().getExecutionSupport());
        return "ALTER TABLE " + getConcatenatedSchemaAndTableName(schemaName, tableName) + " ADD COLUMN IF NOT EXISTS " + column._name() + " " + columnType + ";";
    }

    private org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table createTargetTable(String targetTableName, MutableList<org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Column> columns)
    {
        org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table targetTable = new org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table();
        targetTable.name = targetTableName;
        targetTable.columns = Lists.mutable.withAll(createTargetColumns(columns));
        return targetTable;
    }

    private List<Column> createTargetColumns(MutableList<org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Column> columns)
    {
        ExecutionSupport executionSupport = getContext().getExecutionSupport();
        return columns.collect(c ->
        {
            Root_meta_protocols_pure_vX_X_X_metamodel_store_relational_Column transformedColumn = core_relational_relational_protocols_pure_vX_X_X_transfers_metamodel_relational.Root_meta_protocols_pure_vX_X_X_transformation_fromPureGraph_store_relational_transformColumn_RelationalOperationElement_1__Column_1_(c, executionSupport);
            String json = core_pure_protocol_protocol.Root_meta_alloy_metadataServer_alloyToJSON_Any_1__String_1_(transformedColumn, executionSupport);
            try
            {
                return ObjectMapperFactory.getNewStandardObjectMapperWithPureProtocolExtensionSupports().readValue(json, Column.class);
            }
            catch (IOException e)
            {
                throw new UnsupportedOperationException(e);
            }
        });
    }

    private void processDatabase(org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Database compiledTargetDuckDBDatabase, String targetSchemaName, String targetTableName, MutableList<org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Column> incomingColumns)
    {
        try (Statement statement = this.connection.createStatement())
        {
            Optional<Schema> optionalSchema = ListIterate.select(this.parsedTargetDuckDBDatabase.schemas, s -> s.name.equals(targetSchemaName)).getFirstOptional();
            if (optionalSchema.isEmpty())
            {
                Schema targetSchema = new Schema();
                targetSchema.name = targetSchemaName;
                targetSchema.tables = Lists.mutable.with(createTargetTable(targetTableName, incomingColumns));
                this.parsedTargetDuckDBDatabase.schemas = Lists.mutable.withAll(this.parsedTargetDuckDBDatabase.schemas).with(targetSchema);
                compiledTargetDuckDBDatabase._schemasAdd(HelperRelationalBuilder.processDatabaseSchema(targetSchema, getContext(), compiledTargetDuckDBDatabase));
                if (!targetSchemaName.equals("default"))
                {
                    statement.executeUpdate(safeCreateSchema(targetSchemaName));
                }
                statement.executeUpdate(safeCreateTableWithColumns(targetSchemaName, targetTableName, incomingColumns));
            }
            else
            {
                Schema targetSchema = optionalSchema.get();
                org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Schema compiledTargetSchema = compiledTargetDuckDBDatabase._schemas().select(s -> s._name().equals(targetSchemaName)).getOnly();
                Optional<org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table> optionalTable = ListIterate.select(targetSchema.tables, t -> t.name.equals(targetTableName)).getFirstOptional();
                if (optionalTable.isPresent())
                {
                    org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table targetTable = optionalTable.get();
                    MutableSet<String> existingColumnNameSet = Sets.mutable.fromStream(targetTable.columns.stream().map(c -> c.name));
                    MutableList<org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Column> columnsToAdd = incomingColumns.select(c -> !existingColumnNameSet.contains(c._name()));
                    targetTable.columns = Lists.mutable.withAll(targetTable.columns).withAll(createTargetColumns(columnsToAdd));
                    Table compiledTargetTable = compiledTargetSchema._tables().select(t -> t._name().equals(targetTableName)).getOnly();
                    compiledTargetSchema._tablesRemove(compiledTargetTable)._tablesAdd(HelperRelationalBuilder.processDatabaseTable(targetTable, getContext(), compiledTargetSchema));
                    for (org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Column column : columnsToAdd)
                    {
                        statement.executeUpdate(safeAlterTableWithColumn(targetSchemaName, targetTableName, column));
                    }
                }
                else
                {
                    org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table targetTable = createTargetTable(targetTableName, incomingColumns);
                    targetSchema.tables = Lists.mutable.withAll(targetSchema.tables).with(targetTable);
                    compiledTargetSchema._tablesAdd(HelperRelationalBuilder.processDatabaseTable(targetTable, getContext(), compiledTargetSchema));
                    statement.executeUpdate(safeCreateTableWithColumns(targetSchemaName, targetTableName, incomingColumns));
                }
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ValueSpecification visit(AppliedFunction appliedFunction)
    {
        if (appliedFunction.function.equals("write"))
        {
            // Process second parameter of write()
            org.finos.legend.engine.protocol.pure.m3.valuespecification.ValueSpecification writeSecondParameter = appliedFunction.parameters.get(1);
            if (writeSecondParameter instanceof ClassInstance)
            {
                ClassInstance classInstance = (ClassInstance) writeSecondParameter;
                Object value = classInstance.value;
                if (value instanceof RelationStoreAccessor)
                {
                    RelationStoreAccessor relationStoreAccessor = (RelationStoreAccessor) value;
                    List<String> paths = relationStoreAccessor.path;
                    if (paths.size() >= 2)
                    {
                        String targetDatabasePath = paths.get(0);
                        if (targetDatabasePath.equals("local::DuckDuckDatabase"))
                        {
                            String targetSchemaName = (paths.size() == 3) ? paths.get(1) : "default";
                            String targetTableName = paths.get(paths.size() - 1);

                            // Process first parameter of write()
                            org.finos.legend.engine.protocol.pure.m3.valuespecification.ValueSpecification writeFirstParameter = appliedFunction.parameters.get(0);
                            ValueSpecification compiledParameter = writeFirstParameter.accept(this);
                            RelationType relationType = (RelationType) compiledParameter._genericType()._typeArguments().getFirst()._rawType();
                            MutableList<org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Column> incomingColumns = relationType._columns().collect(c ->
                            {
                                org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.Column pureColumn = (org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.Column) c;
                                Type type = pureColumn._classifierGenericType()._typeArguments().getLast()._rawType();
                                DataType dataType = core_relational_relational_transform_fromPure_pureToRelational.Root_meta_relational_transform_fromPure_pureTypeToDataType_Type_1__DataType_$0_1$_(type, getContext().getExecutionSupport());
                                org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Column relationalColumn = new Root_meta_relational_metamodel_Column_Impl(pureColumn._name(), SourceInformationHelper.toM3SourceInformation(null), getContext().pureModel.getClass("meta::relational::metamodel::Column"));
                                return relationalColumn._name(pureColumn._name())._type(dataType);
                            }).toList();

                            // Process database (Add new parsed/compiled schema/table/columns if not present)
                            org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Database compiledTargetDuckDBDatabase = (org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Database) (getContext().pureModel.getStore(targetDatabasePath));
                            processDatabase(compiledTargetDuckDBDatabase, targetSchemaName, targetTableName, incomingColumns);
                        }
                    }
                }
            }
        }

        return super.visit(appliedFunction);
    }
}
