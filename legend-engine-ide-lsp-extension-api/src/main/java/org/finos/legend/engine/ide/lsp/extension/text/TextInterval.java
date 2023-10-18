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

package org.finos.legend.engine.ide.lsp.extension.text;

import java.util.Objects;

/**
 * A (continuous) interval of text between two {@link TextPosition}s. Both the start and end positions are inclusive. As
 * such, there is no empty text interval.
 */
public class TextInterval
{
    private final TextPosition start;
    private final TextPosition end;

    private TextInterval(TextPosition start, TextPosition end)
    {
        Objects.requireNonNull(start, "start is required");
        Objects.requireNonNull(end, "end is required");
        if (start.isAfter(end))
        {
            throw new IllegalArgumentException("Invalid interval: start {" + start.getLine() + "," + start.getColumn() + "} is after end {" + end.getLine() + "," + end.getColumn() + "}");
        }
        this.start = start;
        this.end = end;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof TextInterval))
        {
            return false;
        }

        TextInterval that = (TextInterval) other;
        return this.start.equals(that.start) && this.end.equals(that.end);
    }

    @Override
    public int hashCode()
    {
        return this.start.hashCode() + (11 * this.end.hashCode());
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{start=" + this.start.toCompactString() + " end=" + this.end.toCompactString() + "}";
    }

    /**
     * Get the start of the interval.
     *
     * @return start of the interval
     */
    public TextPosition getStart()
    {
        return this.start;
    }

    /**
     * Get the end of the interval.
     *
     * @return end of the interval
     */
    public TextPosition getEnd()
    {
        return this.end;
    }

    /**
     * Return whether this interval subsumes {@code other}. This is true if the start of this is no later than the start
     * of {@code other} and the end of this is no earlier
     *
     * @param other other interval
     * @return whether this interval subsumes other
     */
    public boolean subsumes(TextInterval other)
    {
        return !this.start.isAfter(other.start) && !this.end.isBefore(other.end);
    }

    /**
     * Return a string representation of the interval. If {@code compact} is true, then a compact representation will be
     * returned. Otherwise, the default representation will be returned.
     *
     * @param compact whether to use the compact string representation
     * @return string representation
     * @see #toCompactString()
     * @see #toString()
     */
    public String toString(boolean compact)
    {
        return compact ? toCompactString() : toString();
    }

    /**
     * Return a compact string representation of the interval. This uses the default zero-based numbering for both line
     * and column.
     *
     * @return compact string representation
     */
    public String toCompactString()
    {
        return toCompactString(0, 0);
    }

    /**
     * Return a compact string representation of the interval using the given line and column offsets. For the default
     * zero-based numbering, use 0 for both {@code lineOffset} and {@code columnOffset}. For one-based numbering, use 1
     * for both.
     *
     * @param lineOffset   line offset
     * @param columnOffset column offset
     * @return compact string representation
     */
    public String toCompactString(int lineOffset, int columnOffset)
    {
        if (this.start.getLine() != this.end.getLine())
        {
            // different lines
            return (this.start.getLine() + lineOffset) + ":" + (this.start.getColumn() + columnOffset) + "-" +
                    (this.end.getLine() + lineOffset) + ":" + (this.end.getColumn() + columnOffset);
        }

        if (this.start.getColumn() != this.end.getColumn())
        {
            // same line, different columns
            return (this.start.getLine() + lineOffset) + ":" + (this.start.getColumn() + columnOffset) + "-" +
                    (this.end.getColumn() + columnOffset);
        }

        // start == end
        return (this.start.getLine() + lineOffset) + ":" + (this.start.getColumn() + columnOffset);
    }

    /**
     * Create a new {@link TextInterval} from two {@link TextPosition}s. Both {@code start} and {@code end} must be
     * non-null, and start may not be after end (i.e., {@code !start.isAfter(end)}). Both {@code start} and {@code end}
     * are inclusive.
     *
     * @param start interval start (inclusive)
     * @param end   interval end (inclusive)
     * @return text interval
     */
    public static TextInterval newInterval(TextPosition start, TextPosition end)
    {
        return new TextInterval(start, end);
    }

    /**
     * Create a new {@link TextInterval} from two text positions represented as line-column pairs. The start position
     * may not be after the end position. Both are inclusive.
     *
     * @param startLine   start line
     * @param startColumn start column
     * @param endLine     end line
     * @param endColumn   end column
     * @return text interval
     */
    public static TextInterval newInterval(int startLine, int startColumn, int endLine, int endColumn)
    {
        return newInterval(TextPosition.newPosition(startLine, startColumn), TextPosition.newPosition(endLine, endColumn));
    }
}
