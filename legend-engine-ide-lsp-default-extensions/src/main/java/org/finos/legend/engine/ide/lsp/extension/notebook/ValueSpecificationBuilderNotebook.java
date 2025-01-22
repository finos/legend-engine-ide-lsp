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
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.CompileContext;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.HelperRelationalBuilder;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.ProcessingContext;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.ValueSpecificationBuilder;
import org.finos.legend.engine.protocol.pure.v1.model.context.EngineErrorType;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Column;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Database;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Schema;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.DataType;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.ValueSpecificationVisitor;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.application.AppliedFunction;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.ClassInstance;
import org.finos.legend.engine.shared.core.ObjectMapperFactory;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.finos.legend.pure.generated.Root_meta_protocols_pure_vX_X_X_metamodel_store_relational_DataType;
import org.finos.legend.pure.generated.core_pure_protocol_protocol;
import org.finos.legend.pure.generated.core_relational_relational_protocols_pure_vX_X_X_transfers_metamodel_relational;
import org.finos.legend.pure.generated.core_relational_relational_transform_fromPure_pureToRelational;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.RelationType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Type;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.ValueSpecification;
import org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.relation.Table;
import org.finos.legend.pure.m3.execution.ExecutionSupport;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class ValueSpecificationBuilderNotebook extends ValueSpecificationBuilder implements ValueSpecificationVisitor<ValueSpecification>
{
    private final Database parsedTargetDuckDBDatabase;

    public ValueSpecificationBuilderNotebook(CompileContext context, MutableList<String> openVariables, ProcessingContext processingContext, Database database)
    {
        super(context, openVariables, processingContext);
        this.parsedTargetDuckDBDatabase = database;
    }

    private DataType transformDatabaseDataType(org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.DataType dataType)
    {
        ExecutionSupport executionSupport = getContext().getExecutionSupport();
        Root_meta_protocols_pure_vX_X_X_metamodel_store_relational_DataType transformedDataType = core_relational_relational_protocols_pure_vX_X_X_transfers_metamodel_relational.Root_meta_protocols_pure_vX_X_X_transformation_fromPureGraph_store_relational_pureDataTypeToAlloyDataType_DataType_1__DataType_1_(dataType, executionSupport);
        String json = core_pure_protocol_protocol.Root_meta_alloy_metadataServer_alloyToJSON_Any_1__String_1_(transformedDataType, executionSupport);
        try
        {
            return ObjectMapperFactory.getNewStandardObjectMapperWithPureProtocolExtensionSupports().readValue(json, DataType.class);
        }
        catch (IOException e)
        {
            throw new UnsupportedOperationException(e);
        }
    }

    private void processDatabase(Database parsedTargetDuckDBDatabase, org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Database compiledTargetDuckDBDatabase, MutableList<Pair<String, org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.DataType>> columnNameDataTypePairs, String targetSchemaName, String targetTableName)
    {
        Optional<Schema> optionalSchema = ListIterate.select(parsedTargetDuckDBDatabase.schemas, s -> s.name.equals(targetSchemaName)).getFirstOptional();
        if (optionalSchema.isEmpty())
        {
            Schema targetSchema = new Schema();
            targetSchema.name = targetSchemaName;
            Optional<org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table> optionalTable = getTableIfModified(columnNameDataTypePairs, targetSchema, targetTableName);
            if (optionalTable.isEmpty())
            {
                throw new EngineException("Error: a new table should have been created!", EngineErrorType.COMPILATION);
            }
            targetSchema.tables = Lists.mutable.with(optionalTable.get());
            parsedTargetDuckDBDatabase.schemas = Lists.mutable.with(targetSchema);
            compiledTargetDuckDBDatabase._schemasAdd(HelperRelationalBuilder.processDatabaseSchema(targetSchema, getContext(), compiledTargetDuckDBDatabase));
        }
        else
        {
            Schema targetSchema = optionalSchema.get();
            Optional<org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table> optionalTable = getTableIfModified(columnNameDataTypePairs, targetSchema, targetTableName);
            if (optionalTable.isPresent())
            {
                org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table targetTable = optionalTable.get();
                targetSchema.tables.removeIf(t -> t.name.equals(targetTable.name));
                targetSchema.tables = Lists.mutable.withAll(targetSchema.tables).with(targetTable);
                org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Schema compiledTargetSchema = compiledTargetDuckDBDatabase._schemas().select(s -> s._name().equals(targetSchemaName)).getOnly();
                Table compiledTargetTable = compiledTargetSchema._tables().select(t -> t._name().equals(targetTableName)).getOnly();
                compiledTargetSchema._tablesRemove(compiledTargetTable)._tablesAdd(HelperRelationalBuilder.processDatabaseTable(targetTable, getContext(), compiledTargetSchema));
            }
        }
    }

    private Optional<org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table> getTableIfModified(MutableList<Pair<String, org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.DataType>> columnNameDataTypePairs, Schema targetSchema, String targetTableName)
    {
        Optional<org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table> optionalTable = ListIterate.select(targetSchema.tables, t -> t.name.equals(targetTableName)).getFirstOptional();
        org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table targetTable = optionalTable.orElseGet(org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table::new);
        List<Column> columnsToAdd = getColumnsToAdd(columnNameDataTypePairs, targetTable);
        if (columnsToAdd.isEmpty())
        {
            return Optional.empty();
        }
        targetTable.name = targetTableName;
        targetTable.columns = Lists.mutable.withAll(targetTable.columns).withAll(columnsToAdd);
        return Optional.of(targetTable);
    }

    private List<Column> getColumnsToAdd(MutableList<Pair<String, org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.DataType>> columnNameDataTypePairs, org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.Table targetTable)
    {
        MutableSet<String> existingColumnNameSet = Sets.mutable.fromStream(targetTable.columns.stream().map(c -> c.name));
        return columnNameDataTypePairs.select(pair -> !existingColumnNameSet.contains(pair.getOne()))
                .collect(pair ->
                {
                    Column targetColumn = new Column();
                    targetColumn.name = pair.getOne();
                    targetColumn.nullable = true;
                    targetColumn.type = transformDatabaseDataType(pair.getTwo());
                    return targetColumn;
                });
    }

    @Override
    public ValueSpecification visit(AppliedFunction appliedFunction)
    {
        if (appliedFunction.function.equals("write"))
        {
            // Process second parameter of write()
            org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.ValueSpecification writeSecondParameter = appliedFunction.parameters.get(1);
            if (!(writeSecondParameter instanceof ClassInstance))
            {
                throw new EngineException("Second parameter of write() should be ClassInstance, but found " + writeSecondParameter.getClass(), EngineErrorType.COMPILATION);
            }
            ClassInstance classInstance = (ClassInstance) writeSecondParameter;
            Object value = classInstance.value;
            if (!(value instanceof org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.classInstance.relation.RelationStoreAccessor))
            {
                throw new EngineException("Notebook currently only supports expressions with RelationStoreAccessor, but found " + value.getClass(), EngineErrorType.COMPILATION);
            }
            org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.classInstance.relation.RelationStoreAccessor targetRelationStoreAccessor = (org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.classInstance.relation.RelationStoreAccessor) value;
            List<String> paths = targetRelationStoreAccessor.path;
            String targetDatabasePath = paths.get(0);
            if (paths.size() > 1 && targetDatabasePath.equals("local::DuckDuckDatabase"))
            {
                String targetSchemaName = (paths.size() == 3) ? paths.get(1) : "default";
                String targetTableName = paths.get(paths.size() - 1);

                // Process first parameter of write()
                org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.ValueSpecification writeFirstParameter = appliedFunction.parameters.get(0);
                ValueSpecification compiledParameter = writeFirstParameter.accept(this);
                RelationType relationType = (RelationType) compiledParameter._genericType()._typeArguments().getFirst()._rawType();
                MutableList<Pair<String, org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.DataType>> columnNameDataTypePairs = relationType._columns().collect(c ->
                {
                    org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.Column column = (org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.Column) c;
                    Type type = column._classifierGenericType()._typeArguments().getLast()._rawType();
                    org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.DataType dataType = core_relational_relational_transform_fromPure_pureToRelational.Root_meta_relational_transform_fromPure_pureTypeToDataType_Type_1__DataType_$0_1$_(type, getContext().getExecutionSupport());
                    return Tuples.pair(column._name(), dataType);
                }).toList();

                // Process database (Add new parsed/compiled schema/table/columns if not present)
                org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Database compiledTargetDuckDBDatabase = (org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Database) (getContext().pureModel.getStore(targetDatabasePath));
                processDatabase(this.parsedTargetDuckDBDatabase, compiledTargetDuckDBDatabase, columnNameDataTypePairs, targetSchemaName, targetTableName);
            }
        }

        return super.visit(appliedFunction);
    }
}
