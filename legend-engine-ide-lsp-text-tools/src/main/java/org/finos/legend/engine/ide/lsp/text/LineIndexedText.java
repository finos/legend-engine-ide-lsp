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

package org.finos.legend.engine.ide.lsp.text;

import java.util.Arrays;
import java.util.Objects;

/**
 * Text with a line index, using zero-based numbering.
 */
public class LineIndexedText
{
    private final String text;
    private final int[] lineIndex;

    private LineIndexedText(String text)
    {
        this.text = Objects.requireNonNull(text);
        this.lineIndex = TextTools.indexLines(text);
    }

    @Override
    public boolean equals(Object other)
    {
        return (this == other) || ((other instanceof LineIndexedText) && this.text.equals(((LineIndexedText) other).text));
    }

    @Override
    public int hashCode()
    {
        return this.text.hashCode();
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{text=\"" + this.text + "\"}";
    }

    /**
     * Get the full text.
     *
     * @return text
     */
    public String getText()
    {
        return this.text;
    }

    /**
     * Get the number of lines in the text.
     *
     * @return number of lines
     */
    public int getLineCount()
    {
        return this.lineIndex.length;
    }

    /**
     * Get the start of a line as an index in the text. If the line is non-empty, then this is the index of the first
     * character of the line. An empty line can only occur at the end of the text. As such, if the line is empty, then
     * the index will be equal to the length of the text. If this is the first line (i.e., {@code line == 0}), then the
     * result will be {@code 0}. Otherwise, the result will be equal to {@code getLineEnd(line - 1)}.
     *
     * @param line line number
     * @return start of line
     * @throws IndexOutOfBoundsException if there is no such line
     * @see #getLineEnd
     */
    public int getLineStart(int line)
    {
        checkLine(line);
        return lineStart(line);
    }

    /**
     * Get the end of a line as an (exclusive) index in the text. That is, it is the first index after the end of the
     * line. If this the last line, then the result will be the length of the text. Otherwise, the result will be equal
     * to {@code getLineStart(line + 1)}.
     *
     * @param line line number
     * @return end of line (exclusive)
     * @throws IndexOutOfBoundsException if there is no such line
     * @see #getLineStart
     */
    public int getLineEnd(int line)
    {
        checkLine(line);
        return lineEnd(line);
    }

    /**
     * Get the length of a line. This will be equal to {@code getLineEnd(line) - getLineStart(line)}.
     *
     * @param line line number
     * @return length of line
     * @throws IndexOutOfBoundsException if there is no such line
     */
    public int getLineLength(int line)
    {
        checkLine(line);
        return lineLength(line);
    }

    /**
     * Get the text index of a line and column.
     *
     * @param line   line number
     * @param column column number in the line
     * @return text index of the line and column
     * @throws IndexOutOfBoundsException if there is no such line or column
     */
    public int getIndex(int line, int column)
    {
        checkLine(line);
        checkColumn(line, column);
        return lineStart(line) + column;
    }

    /**
     * Get the numbered line as a {@link String}. This is equivalent to
     * {@code getText().substring(getLineStart(line), getLineEnd(line))}.
     *
     * @param line line number
     * @return line
     * @throws IndexOutOfBoundsException if there is no such line
     */
    public String getLine(int line)
    {
        checkLine(line);
        return this.text.substring(lineStart(line), lineEnd(line));
    }

    /**
     * Get a multi-line interval of the text as a {@link String}. Note that both {@code start} and {@code end} are
     * inclusive. This is equivalent to {@code getText().substring(getLineStart(start), getLineEnd(end))}.
     *
     * @param start start line (inclusive)
     * @param end   end line (inclusive)
     * @return multi-line segment
     * @throws IndexOutOfBoundsException if there is no such line as start or end, or if start > end
     * @see #getLine
     */
    public String getLines(int start, int end)
    {
        checkLineInterval(start, end);
        return this.text.substring(lineStart(start), lineEnd(end));
    }

    /**
     * Get an interval of the text between two line:column positions as a {@link String}. Note that both the start and
     * end positions are inclusive. This is equivalent to
     * {@code getText().substring(getIndex(startLine, startColumn), getIndex(endLine, endColumn) + 1)}.
     *
     * @param startLine   start line (inclusive)
     * @param startColumn start column (inclusive)
     * @param endLine     end line (inclusive)
     * @param endColumn   end column (inclusive)
     * @return text interval
     * @throws IndexOutOfBoundsException if the start or end position is invalid, or if end is before start
     */
    public String getInterval(int startLine, int startColumn, int endLine, int endColumn)
    {
        checkInterval(startLine, startColumn, endLine, endColumn);
        return this.text.substring(lineStart(startLine) + startColumn, lineStart(endLine) + endColumn + 1);
    }

    /**
     * Get the line number of the given text index.
     *
     * @param index text index
     * @return line number
     * @throws IndexOutOfBoundsException if there is no such index in the text
     */
    public int getLineNumber(int index)
    {
        checkIndex(index);
        int i = Arrays.binarySearch(this.lineIndex, index);
        return (i >= 0) ? i : -(i + 2);
    }

    /**
     * Return whether {@code line} is a valid line number.
     *
     * @param line line number
     * @return whether line is a valid line number
     */
    public boolean isValidLine(int line)
    {
        return (0 <= line) && (line < getLineCount());
    }

    /**
     * Return whether {@code column} is a valid column index for {@code line} (which must itself be valid).
     *
     * @param line   line
     * @param column column
     * @return whether line:column is valid
     */
    public boolean isValidColumn(int line, int column)
    {
        return isValidLine(line) && (0 <= column) && (column < lineLength(line));
    }

    /**
     * Return whether the given line interval is valid. This is true if both {@code startLine} and {@code endLine} are
     * valid lines and {@code startLine} is not after {@code endLine}.
     *
     * @param startLine start line
     * @param endLine   end line
     * @return whether the line interval is valid
     */
    public boolean isValidInterval(int startLine, int endLine)
    {
        return (0 <= startLine) && (startLine <= endLine) && (endLine < getLineCount());
    }

    /**
     * <p>Return whether the given column interval is valid for the given line. This is true if all the following
     * hold:</p>
     * <ul>
     *     <li>{@code isValidColumn(line, startColumn)}</li>
     *     <li>{@code isValidColumn(line, endColumn)}</li>
     *     <li>{@code startColumn <= endColumn}</li>
     * </ul>
     *
     * @param line        line
     * @param startColumn start column
     * @param endColumn   end column
     * @return whether the interval is valid
     * @see #isValidColumn
     */
    public boolean isValidInterval(int line, int startColumn, int endColumn)
    {
        return isValidLine(line) &&
                (0 <= startColumn) &&
                (startColumn <= endColumn) &&
                (endColumn < lineLength(line));
    }

    /**
     * <p>Return whether the given interval is valid. This is true if all of the following hold:</p>
     * <ul>
     *     <li>{@code isValidColumn(startLine, startColumn)}</li>
     *     <li>{@code isValidColumn(endLine, endColumn)}</li>
     *     <li>{@code startLine <= endLine}</li>
     *     <li>if {@code startLine == endLine}, then {@code startColumn <= endColumn}</li>
     * </ul>
     *
     * @param startLine   start line
     * @param startColumn start column
     * @param endLine     end line
     * @param endColumn   end column
     * @return whether the interval is valid
     * @see #isValidColumn
     */
    public boolean isValidInterval(int startLine, int startColumn, int endLine, int endColumn)
    {
        return (startLine == endLine && isValidInterval(startLine, startColumn, endColumn))
                ||
                ((startLine < endLine) && isValidColumn(startLine, startColumn) && isValidColumn(endLine, endColumn));
    }

    /**
     * Return whether the given text index is valid.
     *
     * @param index text index
     * @return whether index is valid
     */
    public boolean isValidIndex(int index)
    {
        return (0 <= index) && (index < this.text.length());
    }

    // Helpers that assume the line and column have been validated

    private int lineStart(int line)
    {
        return this.lineIndex[line];
    }

    // NB: lineEnd is EXCLUSIVE
    private int lineEnd(int line)
    {
        int nextLine = line + 1;
        return (nextLine < getLineCount()) ? lineStart(nextLine) : this.text.length();
    }

    private int lineLength(int line)
    {
        return lineEnd(line) - lineStart(line);
    }

    // Line/column validations

    private void checkLine(int line)
    {
        if (!isValidLine(line))
        {
            throw new IndexOutOfBoundsException("Invalid line number: " + line + "; line count: " + this.lineIndex.length);
        }
    }

    private void checkLineInterval(int start, int end)
    {
        if (!isValidInterval(start, end))
        {
            throw new IndexOutOfBoundsException("Invalid line interval: start " + start + ", end " + end + ", line count " + getLineCount());
        }
    }

    private void checkColumn(int line, int col)
    {
        if (!isValidColumn(line, col))
        {
            throw new IndexOutOfBoundsException("Invalid column for line " + line + ": " + col + ", length: " + lineLength(line));
        }
    }

    private void checkInterval(int startLine, int startColumn, int endLine, int endColumn)
    {
        if (!isValidInterval(startLine, startColumn, endLine, endColumn))
        {
            throw new IndexOutOfBoundsException("Invalid interval " + startLine + ":" + startColumn + "-" + endLine + ":" + endColumn);
        }
    }

    private void checkIndex(int index)
    {
        if (!isValidIndex(index))
        {
            throw new IndexOutOfBoundsException("index " + index + ", length " + this.text.length());
        }
    }

    // Factory method

    /**
     * Index {@code text} by line.
     *
     * @param text text
     * @return text with a line index
     */
    public static LineIndexedText index(String text)
    {
        return new LineIndexedText(text);
    }
}
