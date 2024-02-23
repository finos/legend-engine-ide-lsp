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

import java.util.List;

public class TDSGroupBy
{
    private final List<String> columns;
    private final List<String> groupKeys;
    private final List<TDSAggregation> aggregations;

    public TDSGroupBy(List<String> columns, List<String> groupKeys, List<TDSAggregation> aggregations)
    {
        this.aggregations = aggregations;
        this.groupKeys = groupKeys;
        this.columns = columns;
    }

    /**
     * Return the list of columns on which groupBy is performed.
     *
     * @return columns
     */
    public List<String> getColumns()
    {
        return columns;
    }

    public List<String> getGroupKeys()
    {
        return groupKeys;
    }

    /**
     * Return the list of aggregation operations performed.
     *
     * @return aggregations
     */
    public List<TDSAggregation> getAggregations()
    {
        return aggregations;
    }
}
