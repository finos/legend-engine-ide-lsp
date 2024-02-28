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

public enum TDSSortOrder
{
    /**
     * Ascending sort operation on a query column.
     */
    ASCENDING("asc"),

    /**
     * Descending sort operation on a query column.
     */
    DESCENDING("desc");

    private final String value;

    TDSSortOrder(String value)
    {
        this.value = value;
    }

    public String getValue()
    {
        return this.value;
    }
}
