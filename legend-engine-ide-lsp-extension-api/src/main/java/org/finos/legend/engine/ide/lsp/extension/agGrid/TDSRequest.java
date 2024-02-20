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

public class TDSRequest
{
    private final Integer startRow;

    private final Integer endRow;

    private final List<String> columns;

    private final List<Filter> filter;

    private final List<TDSSort> sort;

    private final List<TDSGroupBy> groupBy;

    public TDSRequest(Integer startRow, Integer endRow, List<String> columns, List<Filter> filter, List<TDSSort> sort, List<TDSGroupBy> groupBy)
    {
        this.columns = columns;
        this.startRow = startRow;
        this.endRow = endRow;
        this.filter = filter;
        this.sort = sort;
        this.groupBy = groupBy;
    }

    /**
     * Return the startRow of tds.
     *
     * @return startRow
     */
    public Integer getStartRow()
    {
        return startRow;
    }

    /**
     * Return the endRow of tds.
     *
     * @return endRow
     */
    public Integer getEndRow()
    {
        return endRow;
    }

    /**
     * Return the list of columns.
     *
     * @return columns
     */
    public List<String> getColumns()
    {
        return columns;
    }

    /**
     * Return the list of filter conditions applied on tds.
     *
     * @return filter
     */
    public List<Filter> getFilter()
    {
        return filter;
    }

    /**
     * Return the list of sort operations performed on tds.
     *
     * @return sort
     */
    public List<TDSSort> getSort()
    {
        return sort;
    }

    /**
     * Return the list of groupBy operations performed on tds.
     *
     * @return groupBy
     */
    public List<TDSGroupBy> getGroupBy()
    {
        return groupBy;
    }
}
