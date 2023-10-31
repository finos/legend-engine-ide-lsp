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

package org.finos.legend.engine.ide.lsp.extension;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.text.LineIndexedText;
import org.junit.jupiter.api.Assertions;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class AbstractLSPGrammarExtensionTest
{
    private static final Pattern GRAMMAR_LINE_PATTERN = Pattern.compile("^\\h*+###(?<parser>\\w++)\\h*+$\\R?", Pattern.MULTILINE);

    protected LegendLSPGrammarExtension extension = newExtension();

    protected void testGetName(String expectedName)
    {
        Assertions.assertEquals(expectedName, this.extension.getName());
    }

    protected void testGetDeclarations(String code, LegendDeclaration... expectedDeclarations)
    {
        Comparator<LegendDeclaration> cmp = Comparator.comparing(d -> d.getLocation().getStart());
        MutableList<LegendDeclaration> expected = Lists.mutable.with(expectedDeclarations).sortThis(cmp);
        MutableList<LegendDeclaration> actual = Lists.mutable.<LegendDeclaration>withAll(this.extension.getDeclarations(newGrammarSection(code))).sortThis(cmp);
        Assertions.assertEquals(expected, actual);
    }

    protected void testDiagnostics(String code, LegendDiagnostic... expectedDiagnostics)
    {
        Comparator<LegendDiagnostic> cmp = Comparator.comparing(d -> d.getLocation().getStart());
        MutableList<LegendDiagnostic> expected = Lists.mutable.with(expectedDiagnostics).sortThis(cmp);
        MutableList<LegendDiagnostic> actual = Lists.mutable.<LegendDiagnostic>withAll(this.extension.getDiagnostics(newGrammarSection(code))).sortThis(cmp);
        Assertions.assertEquals(expected, actual);
    }

    protected abstract LegendLSPGrammarExtension newExtension();

    protected static GrammarSection newGrammarSection(String text)
    {
        LineIndexedText indexedText = LineIndexedText.index(text);

        Matcher matcher = GRAMMAR_LINE_PATTERN.matcher(text);
        boolean hasGrammarLine;
        String grammar;
        int startLine;
        int endLine;
        if (matcher.find())
        {
            hasGrammarLine = true;
            grammar = matcher.group("parser");
            startLine = indexedText.getLineNumber(matcher.start());
            endLine = matcher.find() ? indexedText.getLineNumber(matcher.start()) : (indexedText.getLineCount() - 1);
        }
        else
        {
            hasGrammarLine = false;
            grammar = "Pure";
            startLine = 0;
            endLine = indexedText.getLineCount() - 1;
        }

        return new GrammarSection()
        {
            @Override
            public String getGrammar()
            {
                return grammar;
            }

            @Override
            public int getStartLine()
            {
                return startLine;
            }

            @Override
            public int getEndLine()
            {
                return endLine;
            }

            @Override
            public boolean hasGrammarDeclaration()
            {
                return hasGrammarLine;
            }

            @Override
            public String getFullText()
            {
                return text;
            }

            @Override
            public String getLines(int start, int end)
            {
                checkLine(start);
                checkLine(end);
                if (end < start)
                {
                    throw new IndexOutOfBoundsException("start (" + start + ") < end (" + end + ")");
                }
                return indexedText.getLines(start, end);
            }

            @Override
            public int getLineLength(int line)
            {
                checkLine(line);
                return indexedText.getLineLength(line);
            }

            private void checkLine(int line)
            {
                if ((line < startLine) || (line > endLine))
                {
                    throw new IndexOutOfBoundsException("Invalid line " + line + " (" + startLine + "-" + endLine + ")");
                }
            }
        };
    }
}
