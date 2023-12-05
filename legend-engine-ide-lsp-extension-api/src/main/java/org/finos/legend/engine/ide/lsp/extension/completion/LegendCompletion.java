// Copyright 2023 Goldman Sachs
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

package org.finos.legend.engine.ide.lsp.extension.completion;

import java.util.Objects;

public class LegendCompletion
{
    private final String description;

    private final String suggestion;

    public LegendCompletion(String description, String suggestion)
    {
        this.description = description;
        this.suggestion = Objects.requireNonNull(suggestion, "suggestion is required");
    }

    public String getDescription()
    {
        return description;
    }

    public String getSuggestion()
    {
        return suggestion;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof LegendCompletion))
        {
            return false;
        }

        LegendCompletion that = (LegendCompletion) other;
        return
                (this.description.equals(that.description)) && this.suggestion.equals(that.suggestion);
    }

    @Override
    public int hashCode()
    {
        return  17 * this.description.hashCode() + this.suggestion.hashCode();
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{description = " + this.description +
                ", suggestion = \"" + this.suggestion + "\"}";
    }

}
