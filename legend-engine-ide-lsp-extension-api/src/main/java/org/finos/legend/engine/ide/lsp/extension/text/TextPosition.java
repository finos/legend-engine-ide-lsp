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

/**
 * A position in text, with a line and column number. Both line and column use zero-based numbering.
 */
public class TextPosition implements Comparable<TextPosition>
{
    private final int line;
    private final int column;

    private TextPosition(int line, int column)
    {
        if (line < 0)
        {
            throw new IllegalArgumentException("Invalid line: " + line);
        }
        if (column < 0)
        {
            throw new IllegalArgumentException("Invalid column: " + column);
        }
        this.line = line;
        this.column = column;
    }

    @Override
    public boolean equals(Object other)
    {
        return (this == other) || ((other instanceof TextPosition) && equals((TextPosition) other));
    }

    @Override
    public int hashCode()
    {
        return this.line + (13 * this.column);
    }

    @Override
    public String toString()
    {
        return "TextPosition{line=" + this.line + " col=" + this.column + "}";
    }

    @Override
    public int compareTo(TextPosition other)
    {
        return compare(this, other);
    }

    /**
     * Get the (zero-based) line number.
     *
     * @return line number
     */
    public int getLine()
    {
        return this.line;
    }

    /**
     * Get the (zero-based) column number.
     *
     * @return column number
     */
    public int getColumn()
    {
        return this.column;
    }

    /**
     * Return a string representation of the position. If {@code compact} is true, then a compact representation will be
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
     * Return a compact string representation of the position. This uses the default zero-based numbering for both line
     * and column.
     *
     * @return compact string representation
     * @see #toCompactString(int, int)
     */
    public String toCompactString()
    {
        return toCompactString(0, 0);
    }

    /**
     * Return a compact string representation of the position using the given line and column offsets. For the default
     * zero-based numbering, use 0 for both {@code lineOffset} and {@code columnOffset}. For one-based numbering, use 1
     * for both.
     *
     * @param lineOffset   line offset
     * @param columnOffset column offset
     * @return compact string representation
     */
    public String toCompactString(int lineOffset, int columnOffset)
    {
        return (this.line + lineOffset) + ":" + (this.column + columnOffset);
    }

    /**
     * <p>Return whether this text position equals {@code other}, which must be non-null. This is true if their lines and
     * columns are equal.</p>
     * <br>
     * <p>For any two text positions, {@code x} and {@code y}, exactly one of the following is true:</p>
     * <ul>
     *     <li>{@code x.equals(y)}</li>
     *     <li>{@code x.isBefore(y)}</li>
     *     <li>{@code x.isAfter(y)}</li>
     * </ul>
     *
     * @param other other text position
     * @return whether the text positions are equal
     * @see #isBefore
     * @see #isAfter
     */
    public boolean equals(TextPosition other)
    {
        return (this.line == other.line) && (this.column == other.column);
    }

    /**
     * <p>Return whether this text position is (strictly) before {@code other}. This is true if either its line number is
     * strictly less than the line number of {@code other}, or if the line numbers are equal and its column number is
     * strictly less than the column number of {@code other}. This method is equivalent to
     * {@code other.isAfter(this)}.</p>
     * <br>
     * <p>For any two text positions, {@code x} and {@code y}, exactly one of the following is true:</p>
     * <ul>
     *     <li>{@code x.equals(y)}</li>
     *     <li>{@code x.isBefore(y)}</li>
     *     <li>{@code x.isAfter(y)}</li>
     * </ul>
     *
     * @param other other text position
     * @return whether this is strictly before other
     * @see #isAfter
     * @see #equals(TextPosition)
     */
    public boolean isBefore(TextPosition other)
    {
        return compareTo(other) < 0;
    }

    /**
     * <p>Return whether this text position is (strictly) after {@code other}. This is true if either its line number is
     * strictly greater than the line number of {@code other}, or if the line numbers are equal and its column number is
     * strictly greater than the column number of {@code other}. This method is equivalent to
     * {@code other.isBefore(this)}.</p>
     * <br>
     * <p>For any two text positions, {@code x} and {@code y}, exactly one of the following is true:</p>
     * <ul>
     *     <li>{@code x.equals(y)}</li>
     *     <li>{@code x.isBefore(y)}</li>
     *     <li>{@code x.isAfter(y)}</li>
     * </ul>
     *
     * @param other other text position
     * @return whether this is strictly after other
     * @see #isBefore
     * @see #equals(TextPosition)
     */
    public boolean isAfter(TextPosition other)
    {
        return compareTo(other) > 0;
    }

    /**
     * Compare two text positions, first by line, then by column.
     *
     * @param pos1 first position
     * @param pos2 second position
     * @return comparison result
     */
    public static int compare(TextPosition pos1, TextPosition pos2)
    {
        int cmp = Integer.compare(pos1.line, pos2.line);
        return (cmp != 0) ? cmp : Integer.compare(pos1.column, pos2.column);
    }

    /**
     * Create a new {@link TextPosition}. Both line and column are zero-based.
     *
     * @param line   line number
     * @param column column number
     * @return position
     */
    public static TextPosition newPosition(int line, int column)
    {
        return new TextPosition(line, column);
    }
}
