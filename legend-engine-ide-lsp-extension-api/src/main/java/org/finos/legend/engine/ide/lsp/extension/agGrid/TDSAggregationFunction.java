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

public enum TDSAggregationFunction
{
    /**
     * Sum aggregation operation on a query column.
     */
    SUM("sum"),

    /**
     * Minimum aggregation operation on a query column.
     */
    MIN("min"),

    /**
     * Maximum aggregation operation on a query column.
     */
    MAX("max"),

    /**
     * Count aggregation operation on a query column.
     */
    COUNT("count"),

    /**
     * Average aggregation operation on a query column.
     */
    AVG("avg"),

    /**
     * First aggregation operation on a query column.
     */
    FIRST("first"),

    /**
     * Last aggregation operation on a query column.
     */
    LAST("last");

    private final String value;

    TDSAggregationFunction(String value)
    {
        this.value = value;
    }

    public String getValue()
    {
        return value;
    }
}
