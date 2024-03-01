// Copyright 2024 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.engine.ide.lsp.extension.agGrid;

import java.util.Objects;

public class Filter
{
    private final String column;
    private final ColumnType columnType;
    private final FilterOperation operation;
    private final Object value;

    public Filter(String column, ColumnType columnType, FilterOperation operation, Object value)
    {
        this.column = Objects.requireNonNull(column, "column is required");
        this.columnType = Objects.requireNonNull(columnType, "columnType is required");
        this.operation = Objects.requireNonNull(operation, "operation is required");
        this.value = Objects.requireNonNull(value, "value is required");
    }

    /**
     * Return the column name of query, the filter applies to.
     *
     * @return column
     */
    public String getColumn()
    {
        return this.column;
    }

    /**
     * Return the column type of filter condition.
     *
     * @return value
     */
    public ColumnType getColumnType()
    {
        return columnType;
    }

    /**
     * Return the operation of filter column.
     *
     * @return operation
     */
    public FilterOperation getOperation()
    {
        return operation;
    }

    /**
     * Return the value of filter condition.
     *
     * @return value
     */
    public Object getValue()
    {
        return value;
    }
}
