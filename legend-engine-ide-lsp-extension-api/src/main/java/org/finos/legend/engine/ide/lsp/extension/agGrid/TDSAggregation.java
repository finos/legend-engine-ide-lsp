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

public class TDSAggregation
{
    private String column;
    private final ColumnType columnType;
    private TDSAggregationFunction function;

    public TDSAggregation(String column, ColumnType columnType, TDSAggregationFunction function)
    {
        this.column = Objects.requireNonNull(column, "column is required");
        this.columnType = Objects.requireNonNull(columnType, "columnType is required");
        this.function = Objects.requireNonNull(function, "function is required");
    }

    /**
     * Return the column name of aggregation operation.
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
     * Return the aggregation function.
     *
     * @return function
     */
    public TDSAggregationFunction getFunction()
    {
        return function;
    }
}
