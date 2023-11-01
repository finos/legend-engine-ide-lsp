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

import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>An index of the grammar sections in a text document.</p>
 * <br>
 * <p>A grammar section ({@link GrammarSection}) is a continuous interval of lines, possibly starting with a grammar
 * declaration line of the form "{@code ###GrammarName}". A grammar declaration line indicates the start of a new
 * section, so it will occur only as the first line of a section. Sections after the first must begin with an explicit
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
     * Applies the given consumer to each section in order. Equivalent to {@code getSections.forEach(consumer)}.
     *
     * @param consumer section consumer
     */
    public void forEachSection(Consumer<? super GrammarSection> consumer)
    {
        this.sections.forEach(consumer);
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
        if (!this.text.isValidLine(line))
        {
            throw new IndexOutOfBoundsException("Invalid line number: " + line + "; line count: " + this.text.getLineCount());
        }
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
            return new GrammarSectionIndex(text, newGrammarSection(text, false, PURE_GRAMMAR_NAME, 0, text.getLineCount() - 1));
        }

        List<GrammarSection> sections = new ArrayList<>();
        if ((matcher.start() > 0) && !TextTools.isBlank(text.getText(), 0, matcher.start()))
        {
            sections.add(newGrammarSection(text, false, PURE_GRAMMAR_NAME, 0, text.getLineNumber(matcher.start() - 1)));
        }

        int index = matcher.start();
        String grammarName = matcher.group("parser");
        while (matcher.find())
        {
            sections.add(newGrammarSection(text, true, grammarName, text.getLineNumber(index), text.getLineNumber(matcher.start() - 1)));
            index = matcher.start();
            grammarName = matcher.group("parser");
        }
        sections.add(newGrammarSection(text, true, grammarName, text.getLineNumber(index), text.getLineCount() - 1));
        return new GrammarSectionIndex(text, List.copyOf(sections));
    }

    private static GrammarSection newGrammarSection(LineIndexedText text, boolean hasGrammarDeclaration, String grammar, int startLine, int endLine)
    {
        return new SimpleGrammarSection(text, hasGrammarDeclaration, grammar, startLine, endLine);
    }

    private static class SimpleGrammarSection implements GrammarSection
    {
        private final LineIndexedText fullText;
        private final boolean hasGrammarDeclaration;
        private final String grammar;
        private final int startLine;
        private final int endLine;

        private SimpleGrammarSection(LineIndexedText fullText, boolean hasGrammarDeclaration, String grammar, int startLine, int endLine)
        {
            this.fullText = Objects.requireNonNull(fullText);
            this.hasGrammarDeclaration = hasGrammarDeclaration;
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
            return (this.startLine == that.getStartLine()) &&
                    (this.endLine == that.getEndLine()) &&
                    (this.hasGrammarDeclaration == that.hasGrammarDeclaration()) &&
                    this.fullText.getText().equals(that.getFullText()) &&
                    this.grammar.equals(that.getGrammar());
        }

        @Override
        public int hashCode()
        {
            return this.fullText.hashCode() + 41 * (this.startLine + (41 * this.endLine));
        }

        @Override
        public String toString()
        {
            return getClass().getSimpleName() + "{grammar=" + this.grammar + " startLine=" + this.startLine + " endLine=" + this.endLine + " hasGrammarDeclaration=" + this.hasGrammarDeclaration + "}";
        }

        @Override
        public String getGrammar()
        {
            return this.grammar;
        }

        @Override
        public int getStartLine()
        {
            return this.startLine;
        }

        @Override
        public int getEndLine()
        {
            return this.endLine;
        }

        @Override
        public boolean hasGrammarDeclaration()
        {
            return this.hasGrammarDeclaration;
        }

        @Override
        public String getLines(int start, int end)
        {
            checkLineNumber(start);
            checkLineNumber(end);
            return this.fullText.getLines(start, end);
        }

        @Override
        public int getLineLength(int line)
        {
            checkLineNumber(line);
            return this.fullText.getLineLength(line);
        }

        @Override
        public String getFullText()
        {
            return this.fullText.getText();
        }

        private void checkLineNumber(int line)
        {
            if ((line < this.startLine) || (line > this.endLine))
            {
                throw new IndexOutOfBoundsException("Line " + line + " is outside of section (lines " + this.startLine + "-" + this.endLine + ")");
            }
        }
    }
}
