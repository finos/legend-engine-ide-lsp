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

import java.util.List;

public class LegendCompletion
{
    String trigger;
    String type;
    List<? extends String> suggestions;

    private static final List<String> ATTRIBUTE_TYPES = List.of("Integer ", "Date ", "StrictDate ", "String ", "Boolean ");

    private static final List<String> ATTRIBUTE_TYPES_TRIGGERS = List.of(": ");

    private static final List<String> ATTRIBUTE_TYPES_SUGGESTIONS = ATTRIBUTE_TYPES;

    private static final List<String> ATTRIBUTE_MULTIPLICITIES_TRIGGERS = ATTRIBUTE_TYPES;

    private static final List<String> ATTRIBUTE_MULTIPLICITIES_SUGGESTIONS = List.of("[0..1];\n", "[0..*];\n", "[1];\n", "[*];\n");

    private Boolean matchTrigger(String codeLine, List<String> triggers)
    {
        for (String triggerWord: triggers)
        {
            if ((codeLine.length() - triggerWord.length()) > 0 &&
                    codeLine.substring(codeLine.length() - triggerWord.length()).equals(triggerWord))
            {
                return true;
            }
        }
        return false;
    }

    public LegendCompletion(String trigger)
    {
        this.setTrigger(trigger);
        this.setSuggestions();
        this.setType();
    }

    public List<? extends String> getSuggestions()
    {
        return suggestions;
    }

    public String getType()
    {
        return type;
    }

    public String getTrigger()
    {
        return trigger;
    }

    private void setTrigger(String trigger)
    {
        this.trigger = trigger;
    }

    private void setType()
    {
        this.type = "";
        if (ATTRIBUTE_MULTIPLICITIES_TRIGGERS.contains(trigger))
        {
            this.type = "Attribute multiplicities";
        }
        if (ATTRIBUTE_TYPES_TRIGGERS.contains(trigger))
        {
            this.type = "Attribute type";
        }
    }

    private void setSuggestions()
    {
        this.suggestions = List.of();
        if (matchTrigger(trigger, ATTRIBUTE_MULTIPLICITIES_TRIGGERS))
        {
            this.suggestions = ATTRIBUTE_MULTIPLICITIES_SUGGESTIONS;
        }
        if (matchTrigger(trigger, ATTRIBUTE_TYPES_TRIGGERS))
        {
            this.suggestions = ATTRIBUTE_TYPES_SUGGESTIONS;
        }
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
        return (this.trigger.equals(that.trigger)) &&
                (this.type.equals(that.type)) &&
                this.suggestions.equals(that.suggestions);
    }

    @Override
    public int hashCode()
    {
        int hashCode = this.trigger.hashCode();
        hashCode = 17 * hashCode + this.type.hashCode();
        hashCode = 17 * hashCode + this.suggestions.hashCode();
        return hashCode;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{trigger = " + this.trigger +
                ", type = " + this.type +
                ", suggestions = " + this.suggestions + "\"}";
    }

}
