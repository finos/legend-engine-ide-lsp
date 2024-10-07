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

package org.finos.legend.engine.ide.lsp.extension.state;

import java.util.function.Consumer;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;

/**
 * The state of a particular document.
 */
public interface DocumentState extends State
{
    /**
     * Get the global state that this is a part of.
     *
     * @return global state
     */
    GlobalState getGlobalState();

    /**
     * Get the id of the document. Calling {@code getGlobalState().getDocumentState(getDocumentId())} should return this
     * document state.
     *
     * @return document id
     * @see GlobalState#getDocumentState
     */
    String getDocumentId();

    /**
     * Get the text of the document.
     *
     * @return document text
     */
    String getText();

    /**
     * Get the number of lines in the document.
     *
     * @return number of lines
     */
    int getLineCount();

    /**
     * Get the text of a line.
     *
     * @param line line number (zero based)
     * @return line text
     */
    default String getLine(int line)
    {
        return getLines(line, line);
    }

    /**
     * Get a multi-line interval of the text. Note that both {@code start} and {@code end} are inclusive.
     *
     * @param start start line (inclusive)
     * @param end   end line (inclusive)
     * @return multi-line interval
     * @throws IndexOutOfBoundsException if either start or end is invalid or if end is before start
     */
    String getLines(int start, int end);

    /**
     * Get the number of sections in the document. As long as the document has text, this will be non-zero.
     *
     * @return section count
     */
    int getSectionCount();

    /**
     * Get the state for the nth section (starting from 0).
     *
     * @param n section number
     * @return state of the nth section
     */
    SectionState getSectionState(int n);

    /**
     * Get the state for the section at the given line. Note that there is not necessarily a section at every
     * line, so this method may return null.
     *
     * @param line line number (zero based)
     * @return state of the section at the given line
     */
    default SectionState getSectionStateAtLine(int line)
    {
        if ((line < 0) || (line >= getLineCount()))
        {
            throw new IndexOutOfBoundsException("Invalid line number: " + line + "; line count: " + getLineCount());
        }

        int low = 0;
        int high = getSectionCount() - 1;
        while (low <= high)
        {
            int mid = (low + high) >>> 1;
            SectionState state = getSectionState(mid);
            if (line < state.getSection().getStartLine())
            {
                high = mid - 1;
            }
            else if (line > state.getSection().getEndLine())
            {
                low = mid + 1;
            }
            else
            {
                return state;
            }
        }
        return null;
    }

    /**
     * Apply the given consumer to each section state in order.
     *
     * @param consumer section state consumer
     */
    void forEachSectionState(Consumer<? super SectionState> consumer);

    @Override
    default void logInfo(String message)
    {
        getGlobalState().logInfo(message);
    }

    @Override
    default void logWarning(String message)
    {
        getGlobalState().logWarning(message);
    }

    @Override
    default void logError(String message)
    {
        getGlobalState().logError(message);
    }

    default TextLocation getTextLocation()
    {
        int lineCount = this.getLineCount();
        int endColumn = lineCount == 0 ? 0 : this.getLine(lineCount - 1).length();
        return TextLocation.newTextSource(this.getDocumentId(), 0, 0, lineCount, endColumn);
    }
}
