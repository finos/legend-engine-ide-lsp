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
 * A text source location, including the document id where the source is defined and the text interval on such document
 */
public class TextLocation
{
    private final String documentId;
    private final TextInterval textInterval;

    private TextLocation(String documentId, TextInterval textInterval)
    {
        this.documentId = Objects.requireNonNull(documentId, "Document ID is required");
        this.textInterval = Objects.requireNonNull(textInterval, "Text interval is required");
    }

    /**
     * The document ID where the source is defined
     * @return document id
     */
    public String getDocumentId()
    {
        return documentId;
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
        return this.textInterval.equals(that.textInterval) && this.documentId.equals(that.documentId);
    }

    @Override
    public int hashCode()
    {
        return this.textInterval.hashCode() + (11 * this.documentId.hashCode());
    }

    @Override
    public String toString()
    {
        return "TextSource{" +
                "documentId='" + documentId + '\'' +
                ", textInterval=" + textInterval.toCompactString() +
                '}';
    }

    /**
     * Return whether this source subsumes {@code other}. This is true if the document id are equal and
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
     * Return whether this source subsumes {@code other}. This is true if the document id are equal and
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
        return this.documentId.equals(other.documentId) && this.textInterval.subsumes(other.textInterval, strict);
    }


    /**
     * Creates a new text source with the given document id, and the {@link TextInterval}
     * @param documentId document id, where the source is defined
     * @param startLine   start line
     * @param startColumn start column
     * @param endLine     end line
     * @param endColumn   end column
     * @return new text source
     */
    public static TextLocation newTextSource(String documentId, int startLine, int startColumn, int endLine, int endColumn)
    {
        return newTextSource(documentId, TextInterval.newInterval(startLine, startColumn, endLine, endColumn));
    }

    /**
     * Creates a new text source with the given document id, and the {@link TextInterval}
     * @param documentId document id, where the source is defined
     * @param textInterval text interval {@link TextInterval}
     * @return new text source
     */
    public static TextLocation newTextSource(String documentId, TextInterval textInterval)
    {
        return new TextLocation(documentId, textInterval);
    }
}
