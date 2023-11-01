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

package org.finos.legend.engine.ide.lsp.extension.diagnostic;

import org.finos.legend.engine.ide.lsp.extension.text.Locatable;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;

import java.util.Objects;

/**
 * Diagnostic from a Legend tool, such as a parser or compiler. This is information about text at a specific location.
 * Most often it is an indication of some kind of problem, but can also be a hint or simply information.
 */
public class LegendDiagnostic implements Locatable
{
    private final TextInterval location;
    private final String message;
    private final Kind kind;
    private final Source source;

    private LegendDiagnostic(TextInterval location, String message, Kind kind, Source source)
    {
        this.location = Objects.requireNonNull(location, "location is required");
        this.message = Objects.requireNonNull(message, "message is required");
        this.kind = Objects.requireNonNull(kind, "severity is required");
        this.source = Objects.requireNonNull(source, "type is required");
    }

    @Override
    public TextInterval getLocation()
    {
        return this.location;
    }

    /**
     * Get the kind of diagnostic, e.g., Error or Warning.
     *
     * @return diagnostic kind
     */
    public Kind getKind()
    {
        return this.kind;
    }

    /**
     * Get the diagnostic message. This should explain the diagnostic in a way a user can understand.
     *
     * @return diagnostic message
     */
    public String getMessage()
    {
        return this.message;
    }

    /**
     * Get the source of the diagnostic, such as the Parser or Compiler.
     *
     * @return diagnostic source
     */
    public Source getType()
    {
        return this.source;
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
        return (this.source == that.source) &&
                (this.kind == that.kind) &&
                this.location.equals(that.location) &&
                this.message.equals(that.message);
    }

    @Override
    public int hashCode()
    {
        int hashCode = this.location.hashCode();
        hashCode = 17 * hashCode + this.message.hashCode();
        hashCode = 17 * hashCode + this.source.hashCode();
        hashCode = 17 * hashCode + this.kind.hashCode();
        return hashCode;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{location = " + this.location.toCompactString() +
                ", type = " + this.source +
                ", severity = " + this.kind +
                ", message=\"" + this.message + "\"}";
    }

    /**
     * The kind of diagnostic
     */
    public enum Kind
    {
        /**
         * A hint from the source tool.
         */
        Hint,

        /**
         * An informative message from the source tool.
         */
        Information,

        /**
         * A problem which does not normally prevent completion of the source tool.
         */
        Warning,

        /**
         * A problem which prevents the normal completion of the source tool.
         */
        Error
    }

    /**
     * Diagnostic source tool
     */
    public enum Source
    {
        /**
         * A diagnostic from a parser.
         */
        Parser,

        /**
         * A diagnostic from a compiler.
         */
        Compiler
    }

    /**
     * Create a new Legend diagnostic.
     *
     * @param location location
     * @param message  message
     * @param kind     diagnostic kind
     * @param source   diagnostic source
     * @return Legend diagnostic
     */
    public static LegendDiagnostic newDiagnostic(TextInterval location, String message, Kind kind, Source source)
    {
        return new LegendDiagnostic(location, message, kind, source);
    }
}
