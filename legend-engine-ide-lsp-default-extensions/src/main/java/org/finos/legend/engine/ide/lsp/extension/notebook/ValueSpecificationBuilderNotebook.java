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
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.BigInt;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Binary;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Bit;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Char;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.DataType;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Date;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Decimal;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Double;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Float;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Integer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Json;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Numeric;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Other;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Real;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.SemiStructured;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.SmallInt;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Timestamp;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.TinyInt;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.VarChar;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.relational.model.datatype.Varbinary;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.ValueSpecificationVisitor;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.application.AppliedFunction;
import org.finos.legend.engine.protocol.pure.v1.model.valueSpecification.raw.ClassInstance;
import org.finos.legend.engine.shared.core.operational.errorManagement.EngineException;
import org.finos.legend.pure.generated.core_relational_relational_transform_fromPure_pureToRelational;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relation.RelationType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Type;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.ValueSpecification;
import org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.relation.Table;

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
        if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Varchar)
        {
            org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Varchar varChar = (org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Varchar) dataType;
            VarChar transformedVarChar = new VarChar();
            transformedVarChar.size = varChar._size();
            return transformedVarChar;
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Integer)
        {
            return new Integer();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Decimal)
        {
            org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Decimal decimal = (org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Decimal) dataType;
            Decimal transformedDecimal = new Decimal();
            transformedDecimal.precision = decimal._precision();
            transformedDecimal.scale = decimal._scale();
            return transformedDecimal;
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Numeric)
        {
            org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Numeric numeric = (org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Numeric) dataType;
            Numeric transformedNumeric = new Numeric();
            transformedNumeric.precision = numeric._precision();
            transformedNumeric.scale = numeric._scale();
            return transformedNumeric;
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.BigInt)
        {
            return new BigInt();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Bit)
        {
            return new Bit();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Char)
        {
            org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Char _char = (org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Char) dataType;
            Char transformedChar = new Char();
            transformedChar.size = _char._size();
            return transformedChar;
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Date)
        {
            return new Date();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Double)
        {
            return new Double();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Float)
        {
            return new Float();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Real)
        {
            return new Real();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.SmallInt)
        {
            return new SmallInt();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Timestamp)
        {
            return new Timestamp();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.TinyInt)
        {
            return new TinyInt();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Varbinary)
        {
            org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Varbinary varbinary = (org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Varbinary) dataType;
            Varbinary transformedVarbinary = new Varbinary();
            transformedVarbinary.size = varbinary._size();
            return transformedVarbinary;
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Binary)
        {
            org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Binary binary = (org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Binary) dataType;
            Binary transformedBinary = new Binary();
            transformedBinary.size = binary._size();
            return transformedBinary;
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Other)
        {
            return new Other();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.SemiStructured)
        {
            return new SemiStructured();
        }
        else if (dataType instanceof org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.datatype.Json)
        {
            return new Json();
        }
        throw new UnsupportedOperationException();
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
            if (paths.size() < 2)
            {
                throw new EngineException("Target database and table must be provided!", EngineErrorType.COMPILATION);
            }
            String targetDuckDBDatabasePath = paths.get(0);
            String targetSchemaName = (paths.size() == 3) ? paths.get(1) : "default";
            String targetTableName = paths.get(paths.size() - 1);

            // Process database (Add new parsed/compiled schema/table/columns if not present)
            org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Database compiledTargetDuckDBDatabase = (org.finos.legend.pure.m3.coreinstance.meta.relational.metamodel.Database) (getContext().pureModel.getStore(targetDuckDBDatabasePath));
            processDatabase(this.parsedTargetDuckDBDatabase, compiledTargetDuckDBDatabase, columnNameDataTypePairs, targetSchemaName, targetTableName);
        }

        return super.visit(appliedFunction);
    }
}
