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
    private String[] lines;

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
        return lineText(line);
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
        checkLineRange(start, end);
        if (start == end)
        {
            // special case for a single line
            return lineText(start);
        }
        int startIndex = lineStart(start);
        int endIndex = lineEnd(end);
        return this.text.substring(startIndex, endIndex);
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
        if (startLine == endLine)
        {
            // single-line interval
            checkLine(startLine);
            if ((startColumn == 0) && (endColumn <= startColumn) && (endColumn == (lineLength(startLine) - 1)))
            {
                // entire line
                return lineText(startLine);
            }

            checkColumnRange(startLine, startColumn, endColumn);
            int lineStart = lineStart(startLine);
            return this.text.substring(lineStart + startColumn, lineStart + endColumn + 1);
        }

        // multi-line interval
        checkLineRange(startLine, endLine);
        checkColumn(startLine, startColumn);
        checkColumn(endLine, endColumn);
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

    private String lineText(int line)
    {
        if (this.lines == null)
        {
            this.lines = new String[getLineCount()];
        }

        String result = this.lines[line];
        if (result == null)
        {
            this.lines[line] = result = this.text.substring(lineStart(line), lineEnd(line));
        }
        return result;
    }

    // Line/column validations

    void checkLine(int line)
    {
        if ((line < 0) || (line >= this.lineIndex.length))
        {
            throw new IndexOutOfBoundsException("Invalid line number: " + line + "; line count: " + this.lineIndex.length);
        }
    }

    private void checkLineRange(int start, int end)
    {
        int count = getLineCount();
        if ((start < 0) || (end < start) || (end >= count))
        {
            throw new IndexOutOfBoundsException("Invalid line range: start " + start + ", end " + end + ", line count " + count);
        }
    }

    private void checkColumn(int line, int col)
    {
        int length = lineLength(line);
        if ((col < 0) || (col >= length))
        {
            throw new IndexOutOfBoundsException("Invalid column for line " + line + ": " + col + ", length: " + length);
        }
    }

    private void checkColumnRange(int line, int startCol, int endCol)
    {
        int length = lineLength(line);
        if ((startCol < 0) || (endCol < startCol) || (endCol >= length))
        {
            throw new IndexOutOfBoundsException("Invalid column range for line " + line + ": start " + startCol + ", end " + endCol + ", length " + length);
        }
    }

    private void checkIndex(int index)
    {
        if ((index < 0) || (index >= this.text.length()))
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
