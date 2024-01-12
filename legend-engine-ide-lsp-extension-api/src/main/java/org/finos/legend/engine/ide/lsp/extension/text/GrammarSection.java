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
 * <p>A section of a text document in a particular grammar. This is a continuous interval of lines, possibly starting
 * with a grammar declaration line of the form "{@code ###GrammarName}". A grammar declaration line indicates the start
 * of a new section, so it will occur only as the first line of a section. Sections after the first in a document must
 * begin with an explicit grammar declaration line. The first section may optionally begin with an explicit grammar
 * declaration line. If an explicit one is not present, the section is deemed to have an implicit grammar declaration
 * line equivalent to {@code ###Pure}.</p>
 * <br>
 * <p>A grammar section begins at the beginning of its first line and ends at the end of its final line, either with a
 * line break or the end of the text document. Sections contain only complete lines; no section will contain a partial
 * line.</p>
 */
public interface GrammarSection
{
    /**
     * Get the grammar name of the section.
     *
     * @return grammar name
     */
    String getGrammar();

    /**
     * Get the start line of the section. Note that this uses zero-based line numbering.
     *
     * @return section start line
     */
    int getStartLine();

    /**
     * Get the end line of the section. This is the last line included in the section. Note that this uses zero-based
     * line numbering. Note also that if this is the last section of the text, the end line may be empty.
     *
     * @return section end line
     */
    int getEndLine();

    /**
     * Whether this section has an explicit grammar declaration line.
     *
     * @return whether this section has an explicit grammar declaration line
     */
    boolean hasGrammarDeclaration();

    /**
     * Get the text of the section, including the grammar declaration line (if present). This is equivalent to
     * calling {@code getText(false)}.
     *
     * @return section text
     * @see #getText(boolean)
     */
    default String getText()
    {
        return getLines(getStartLine(), getEndLine());
    }

    /**
     * Get the text of the section. If {@code dropGrammarDeclaration} is true, then the grammar declaration line will
     * be dropped. Otherwise, it will be included (if present).
     *
     * @param dropGrammarDeclaration whether to drop the grammar declaration line
     * @return section text, possibly without the grammar declaration line
     */
    default String getText(boolean dropGrammarDeclaration)
    {
        int start = getStartLine();
        int end = getEndLine();
        return (hasGrammarDeclaration() && dropGrammarDeclaration) ?
                ((start == end) ? "" : getLines(start + 1, end)) :
                getLines(start, end);
    }

    /**
     * Get a single line of the section.
     *
     * @param line line number
     * @return section line
     * @throws IndexOutOfBoundsException if there is no such line in the section
     */
    default String getLine(int line)
    {
        return getLines(line, line);
    }

    /**
     * Get a single line of the section.
     * @param position the position used to figure out what line number to extract
     * @return section line
     * @throws IndexOutOfBoundsException if there is no such line in the section
     */
    default String getLine(TextPosition position)
    {
        return getLine(position.getLine());
    }

    /**
     * Get a single line of the section up to the column on the given position.
     * @param position the position used to figure out what line number to extract, and up to what column
     * @return section text between start of line and up to the position column
     * @throws IndexOutOfBoundsException if there is no such line in the section or column is bigger than line length
     */
    default String getPrecedingText(TextPosition position)
    {
        return getLine(position).substring(0, position.getColumn());
    }

    /**
     * Get a multi-line interval of the section text. Note that both {@code start} and {@code end} are inclusive.
     *
     * @param start start line (inclusive)
     * @param end   end line (inclusive)
     * @return multi-line interval
     * @throws IndexOutOfBoundsException if either start or end is invalid or if end is before start
     */
    String getLines(int start, int end);

    /**
     * Get the length of a line of the section.
     *
     * @param line line number
     * @return line length
     * @throws IndexOutOfBoundsException if there is no such line in the section
     */
    default int getLineLength(int line)
    {
        return getLine(line).length();
    }

    /**
     * Get the full text that this is a section of.
     *
     * @return full text
     */
    String getFullText();
}
