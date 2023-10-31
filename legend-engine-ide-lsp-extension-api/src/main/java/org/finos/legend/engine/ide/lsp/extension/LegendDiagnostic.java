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

package org.finos.legend.engine.ide.lsp.extension;

import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;

import java.util.Objects;

public class LegendDiagnostic
{
    private final TextInterval location;
    private final String message;
    private final Severity severity;
    private final Type type;

    public LegendDiagnostic(TextInterval location, String message, Severity severity, Type type)
    {
        this.location = Objects.requireNonNull(location, "location is required");
        this.message = Objects.requireNonNull(message, "message is required");
        this.severity = Objects.requireNonNull(severity, "severity is required");
        this.type = Objects.requireNonNull(type, "type is required");
    }

    public TextInterval getLocation()
    {
        return this.location;
    }

    public Severity getSeverity()
    {
        return this.severity;
    }

    public String getMessage()
    {
        return this.message;
    }

    public Type getType()
    {
        return this.type;
    }


    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof LegendDiagnostic))
        {
            return false;
        }
        LegendDiagnostic that = (LegendDiagnostic) other;
        return (this.type == that.type) &&
                (this.severity == that.severity) &&
                this.location.equals(that.location) &&
                this.message.equals(that.message);
    }

    @Override
    public int hashCode()
    {
        int hashCode = this.location.hashCode();
        hashCode = 17 * hashCode + this.message.hashCode();
        hashCode = 17 * hashCode + this.type.hashCode();
        hashCode = 17 * hashCode + this.severity.hashCode();
        return hashCode;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{location = " + this.location.toCompactString() +
                ", type = " + this.type +
                ", severity = " + this.severity +
                ", message=\"" + this.message + "\"}";
    }

    public enum Severity
    {
        Hint, Information, Warning, Error
    }

    public enum Type
    {
        Parser, Compiler
    }
}
