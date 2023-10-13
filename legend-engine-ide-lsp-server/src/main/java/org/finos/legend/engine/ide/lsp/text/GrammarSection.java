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

import java.util.Objects;

/**
 * A section of text in a particular grammar. Line numbers are zero-based.
 */
public class GrammarSection
{
    private final LineIndexedText fullText;
    private final String grammar;
    private final int startLine;
    private final int endLine;
    private String sectionText;

    GrammarSection(LineIndexedText fullText, String grammar, int startLine, int endLine)
    {
        this.fullText = Objects.requireNonNull(fullText);
        this.grammar = Objects.requireNonNull(grammar);
        this.startLine = startLine;
        this.endLine = endLine;
    }

    @Override
    public boolean equals(Object other)
    {
        if (other == this)
        {
            return true;
        }

        if (!(other instanceof GrammarSection))
        {
            return false;
        }

        GrammarSection that = (GrammarSection) other;
        return (this.startLine == that.startLine) &&
                (this.endLine == that.endLine) &&
                this.fullText.equals(that.fullText) &&
                this.grammar.equals(that.grammar);
    }

    @Override
    public int hashCode()
    {
        return this.fullText.hashCode() + 41 * (this.startLine + (41 * this.endLine));
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{grammar=" + this.grammar + " startLine=" + this.startLine + " endLine=" + this.endLine + "}";
    }

    /**
     * Get the grammar name of the section.
     *
     * @return grammar name
     */
    public String getGrammar()
    {
        return this.grammar;
    }

    /**
     * Get the start line of the section. Note that this uses zero-based line numbering.
     *
     * @return section start line
     */
    public int getStartLine()
    {
        return this.startLine;
    }

    /**
     * Get the end line of the section. This is the last line included in the section. Note that this uses zero-based
     * line numbering.
     *
     * @return section end line
     */
    public int getEndLine()
    {
        return this.endLine;
    }

    /**
     * Get the (inclusive) start index of the section.
     *
     * @return start index (inclusive)
     */
    public int getStartIndex()
    {
        return this.fullText.getLineStart(this.startLine);
    }

    /**
     * Get the (exclusive) end index of the section.
     *
     * @return end index (exclusive)
     */
    public int getEnd()
    {
        return this.fullText.getLineEnd(this.endLine);
    }

    /**
     * Get the text of the section.
     *
     * @return section text
     */
    public String getText()
    {
        if (this.sectionText == null)
        {
            this.sectionText = this.fullText.getLines(getStartLine(), getEndLine());
        }
        return this.sectionText;
    }
}
