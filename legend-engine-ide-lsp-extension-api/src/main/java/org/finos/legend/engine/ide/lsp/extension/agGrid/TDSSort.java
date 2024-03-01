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

public class TDSSort
{
    private final String column;

    private final TDSSortOrder order;

    public TDSSort(String column, TDSSortOrder order)
    {
        this.column = Objects.requireNonNull(column, "column is required");
        this.order = Objects.requireNonNull(order, "order is required");
    }

    /**
     * Return the column name on which sort operation is performed.
     *
     * @return column
     */
    public String getColumn()
    {
        return this.column;
    }

    /**
     * Return the sort order.
     *
     * @return order
     */
    public TDSSortOrder getOrder()
    {
        return this.order;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{column = " + this.column +
                ", order = " + this.order + "\"}";
    }
}
