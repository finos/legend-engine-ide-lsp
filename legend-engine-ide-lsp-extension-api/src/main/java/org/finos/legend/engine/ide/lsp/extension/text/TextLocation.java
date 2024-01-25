/*
 * Copyright 2024 Goldman Sachs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.legend.engine.ide.lsp.extension.text;

import java.util.Objects;

/**
 * A text source location, including the source URI and the text interval on that source URI
 */
public class TextLocation
{
    private final String sourceUri;
    private final TextInterval textInterval;

    private TextLocation(String sourceUri, TextInterval textInterval)
    {
        this.sourceUri = Objects.requireNonNull(sourceUri, "Source URI is required");
        this.textInterval = Objects.requireNonNull(textInterval, "Text interval is required");
    }

    /**
     * The source URI for this source
     * @return source URI
     */
    public String getSourceUri()
    {
        return sourceUri;
    }

    /**
     * The text interval for this source
     * @return text interval
     */
    public TextInterval getTextInterval()
    {
        return textInterval;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof TextLocation))
        {
            return false;
        }

        TextLocation that = (TextLocation) other;
        return this.textInterval.equals(that.textInterval) && this.sourceUri.equals(that.sourceUri);
    }

    @Override
    public int hashCode()
    {
        return this.textInterval.hashCode() + (11 * this.sourceUri.hashCode());
    }

    @Override
    public String toString()
    {
        return "TextSource{" +
                "sourceUri='" + sourceUri + '\'' +
                ", textInterval=" + textInterval.toCompactString() +
                '}';
    }

    /**
     * Return whether this source subsumes {@code other}. This is true if the source URI are equal and
     * if this text interval subsumes {@code other} text interval.
     * <br>
     * See {@link TextInterval#subsumes(TextInterval)} for more on interval subsumes
     *
     * @param other other source
     * @return whether this source subsumes other
     * @see #subsumes(TextLocation, boolean)
     */
    public boolean subsumes(TextLocation other)
    {
        return subsumes(other, false);
    }

    /**
     * Return whether this source subsumes {@code other}. This is true if the source URI are equal and
     * if this text interval subsumes {@code other} text interval.
     * <br>
     * See {@link TextInterval#subsumes(TextInterval, boolean)} for more on interval subsumes
     *
     * @param other  other source
     * @param strict whether the subsumption must be strict
     * @return whether this source subsumes other
     */
    public boolean subsumes(TextLocation other, boolean strict)
    {
        return this.sourceUri.equals(other.sourceUri) && this.textInterval.subsumes(other.textInterval, strict);
    }


    /**
     * Creates a new text source with the given source URI, and the {@link TextInterval}
     * @param sourceURI source URI, where the source is defined
     * @param startLine   start line
     * @param startColumn start column
     * @param endLine     end line
     * @param endColumn   end column
     * @return new text source
     */
    public static TextLocation newTextSource(String sourceURI, int startLine, int startColumn, int endLine, int endColumn)
    {
        return newTextSource(sourceURI, TextInterval.newInterval(startLine, startColumn, endLine, endColumn));
    }

    /**
     * Creates a new text source with the given source URI, and the {@link TextInterval}
     * @param sourceURI source URI, where the source is defined
     * @param textInterval text interval {@link TextInterval}
     * @return new text source
     */
    public static TextLocation newTextSource(String sourceURI, TextInterval textInterval)
    {
        return new TextLocation(sourceURI, textInterval);
    }
}
