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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>An index of the grammar sections in a text document.</p>
 * <br>
 * <p>A grammar section ({@link GrammarSection}) is a continuous interval of lines, possibly starting with a grammar
 * declaration line of the form "{@code ###GrammarName}". A grammar declaration line indicates the start of a new
 * section, so they occur only as the first line of a section. Sections after the first must begin with an explicit
 * grammar declaration line. The first section may optionally begin with an explicit grammar declaration line. If an
 * explicit one is not present, the section is deemed to have an implicit grammar declaration line equivalent to
 * {@code ###Pure}.</p>
 * <br>
 * <p>Each section begins at the beginning of its first line and ends at the end of its final line, either with a line
 * break or the end of the text. Sections contain only complete lines; no section will contain a partial line.</p>
 */
public class GrammarSectionIndex
{
    private static final String PURE_GRAMMAR_NAME = "Pure";
    private static final Pattern GRAMMAR_LINE_PATTERN = Pattern.compile("^\\h*+###(?<parser>\\w++)\\h*+$\\R?", Pattern.MULTILINE);

    private final LineIndexedText text;
    private final List<GrammarSection> sections;

    private GrammarSectionIndex(LineIndexedText text, List<GrammarSection> sections)
    {
        this.text = text;
        this.sections = sections;
    }

    private GrammarSectionIndex(LineIndexedText text, GrammarSection section)
    {
        this(text, Collections.singletonList(section));
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof GrammarSectionIndex))
        {
            return false;
        }
        GrammarSectionIndex that = (GrammarSectionIndex) other;
        return this.text.equals(that.text) && this.sections.equals(that.sections);
    }

    @Override
    public int hashCode()
    {
        return this.text.hashCode();
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + "{sections=" + this.sections + "}";
    }

    /**
     * Get the full original text.
     *
     * @return original text
     */
    public String getText()
    {
        return this.text.getText();
    }

    /**
     * Get an unmodifiable list of the sections. These are in the order they appear in the original text document.
     *
     * @return sections
     */
    public List<GrammarSection> getSections()
    {
        return this.sections;
    }

    /**
     * Get the number of sections.
     *
     * @return number of sections
     */
    public int getSectionCount()
    {
        return this.sections.size();
    }

    /**
     * Get a section by number.
     *
     * @param n section number
     * @return section
     * @throws IndexOutOfBoundsException if there is no such section
     */
    public GrammarSection getSection(int n)
    {
        return this.sections.get(n);
    }

    /**
     * Get the section at the given index of the text document. Note that there is not necessarily a section at every
     * index, so this method may return null.
     *
     * @param index character index in text document
     * @return section or null
     * @throws IndexOutOfBoundsException if there is no such index in the text document
     * @see #getSectionAtLine
     */
    public GrammarSection getSectionAtIndex(int index)
    {
        return getSectionAtLine(this.text.getLineNumber(index));
    }

    /**
     * Get the section at the given line of the text document. Note that there is not necessarily a section at every
     * line, so this method may return null.
     *
     * @param line line number
     * @return section or null
     * @throws IndexOutOfBoundsException if there is no such line in the text document
     * @see #getSectionAtIndex
     */
    public GrammarSection getSectionAtLine(int line)
    {
        this.text.checkLineNumber(line);
        for (GrammarSection section : this.sections)
        {
            int startLine = section.getStartLine();
            if ((line == startLine) || ((line > startLine) && (line <= section.getEndLine())))
            {
                return section;
            }
            if (line < startLine)
            {
                return null;
            }
        }
        return null;
    }

    /**
     * Parse the given text document and return the resulting {@link GrammarSectionIndex}.
     *
     * @param text text document
     * @return grammar section index
     */
    public static GrammarSectionIndex parse(String text)
    {
        return parse(LineIndexedText.index(text));
    }

    /**
     * Parse the given text document and return the resulting {@link GrammarSectionIndex}.
     *
     * @param text line indexed text document
     * @return grammar section index
     */
    public static GrammarSectionIndex parse(LineIndexedText text)
    {
        Matcher matcher = GRAMMAR_LINE_PATTERN.matcher(text.getText());
        if (!matcher.find())
        {
            return new GrammarSectionIndex(text, new GrammarSection(text, PURE_GRAMMAR_NAME, 0, text.getLineCount() - 1));
        }

        List<GrammarSection> sections = new ArrayList<>();
        if ((matcher.start() > 0) && !TextTools.isBlank(text.getText(), 0, matcher.start()))
        {
            sections.add(new GrammarSection(text, PURE_GRAMMAR_NAME, 0, text.getLineNumber(matcher.start() - 1)));
        }

        int index = matcher.start();
        String grammarName = matcher.group("parser");
        while (matcher.find())
        {
            sections.add(new GrammarSection(text, grammarName, text.getLineNumber(index), text.getLineNumber(matcher.start() - 1)));
            index = matcher.start();
            grammarName = matcher.group("parser");
        }
        sections.add(new GrammarSection(text, grammarName, text.getLineNumber(index), text.getLineCount() - 1));
        return new GrammarSectionIndex(text, List.copyOf(sections));
    }
}
