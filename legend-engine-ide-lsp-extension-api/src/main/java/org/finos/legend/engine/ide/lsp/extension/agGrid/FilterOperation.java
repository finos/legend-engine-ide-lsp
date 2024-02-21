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

public enum FilterOperation
{
    /**
     * Equals filter on a query column.
     */
    EQUALS("equals"),

    /**
     * Not equal filter on a query column.
     */
    NOT_EQUAL("notEqual"),

    /**
     * Greater than filter on a query column.
     */
    GREATER_THAN("greaterThan"),

    /**
     * Greater than or equal filter on a query column.
     */
    GREATER_THAN_OR_EQUAL("greaterThanOrEqual"),

    /**
     * Less than filter on a query column.
     */
    LESS_THAN("lessThan"),

    /**
     * Less than or equal filter on a query column.
     */
    LESS_THAN_OR_EQUAL("lessThanOrEqual"),

    /**
     * Blank(is empty) filter on a query column.
     */
    BLANK("blank"),

    /**
     * Not blank(is not empty) filter on a query column.
     */
    NOT_BLANK("notBlank");

    private final String value;

    FilterOperation(String value)
    {
        this.value = value;
    }

    public String getValue()
    {
        return value;
    }
}

