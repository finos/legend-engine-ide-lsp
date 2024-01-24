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

import java.util.Comparator;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.state.State;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.extension.text.LegendTextObject;
import org.finos.legend.engine.ide.lsp.text.LineIndexedText;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

abstract class AbstractLSPGrammarExtensionTest<T extends LegendLSPGrammarExtension>
{
    private static final Pattern GRAMMAR_LINE_PATTERN = Pattern.compile("^\\h*+###(?<parser>\\w++)\\h*+$\\R?", Pattern.MULTILINE);
    public static final String DOC_ID_FOR_TEXT = "file.pure";

    protected T extension = newExtension();

    @Test
    public void testDiagnostics_emptySection()
    {
        String code = "###" + this.extension.getName();
        testDiagnostics(code);
    }

    @Test
    public void testDiagnostics_emptyFile()
    {
        testDiagnostics("");
    }

    protected void testGetName(String expectedName)
    {
        Assertions.assertEquals(expectedName, this.extension.getName());
    }

    protected void testGetDeclarations(String code, LegendDeclaration... expectedDeclarations)
    {
        Comparator<LegendDeclaration> cmp = Comparator.comparing(LegendTextObject::getLocation);
        MutableList<LegendDeclaration> expected = Lists.mutable.with(expectedDeclarations).sortThis(cmp);
        MutableList<LegendDeclaration> actual = Lists.mutable.<LegendDeclaration>withAll(this.extension.getDeclarations(newSectionState(DOC_ID_FOR_TEXT, code))).sortThis(cmp);
        Assertions.assertEquals(expected, actual);
    }

    protected void testDiagnostics(String code, LegendDiagnostic... expectedDiagnostics)
    {
        Comparator<LegendDiagnostic> cmp = Comparator.comparing(LegendDiagnostic::getLocation);
        MutableList<LegendDiagnostic> expected = Lists.mutable.with(expectedDiagnostics).sortThis(cmp);
        MutableList<LegendDiagnostic> actual = Lists.mutable.<LegendDiagnostic>withAll(this.extension.getDiagnostics(newSectionState(DOC_ID_FOR_TEXT, code))).sortThis(cmp);
        Assertions.assertEquals(expected, actual);
    }

    protected void testDiagnostics(MutableMap<String, String> files, String expectedDocId, LegendDiagnostic... expectedDiagnostics)
    {
        Comparator<LegendDiagnostic> cmp = Comparator.comparing(LegendDiagnostic::getLocation);
        MutableList<LegendDiagnostic> expected = Lists.mutable.with(expectedDiagnostics).sortThis(cmp);
        MutableList<SectionState> sectionStates = newSectionStates(files);
        SectionState inputSectionState = sectionStates.detect(s -> expectedDocId.equals(s.getDocumentState().getDocumentId()));
        MutableList<LegendDiagnostic> actual = Lists.mutable.<LegendDiagnostic>withAll(this.extension.getDiagnostics(inputSectionState)).sortThis(cmp);
        Assertions.assertEquals(expected, actual);
    }

    protected Iterable<? extends LegendExecutionResult> testCommand(String code, String entityPath, String command)
    {
        SectionState sectionState = newSectionState(DOC_ID_FOR_TEXT, code);
        return this.extension.execute(sectionState, entityPath, command, Maps.fixedSize.empty());
    }

    protected abstract T newExtension();

    protected SectionState newSectionState(String docId, String text)
    {
        LineIndexedText indexedText = LineIndexedText.index(text);
        TestGlobalState globalState = new TestGlobalState();
        TestDocumentState docState = new TestDocumentState(globalState, docId, indexedText);
        TestSectionState sectionState = new TestSectionState(docState, 0, newGrammarSection(indexedText), this.extension);

        globalState.docStates.put(docId, docState);
        docState.sectionStates.add(sectionState);

        return sectionState;
    }

    protected MutableList<SectionState> newSectionStates(MutableMap<String, String> files)
    {
        TestGlobalState globalState = new TestGlobalState();
        MutableList<SectionState> sectionStates = Lists.mutable.empty();
        files.forEach((docId, text) ->
        {
            LineIndexedText indexedText = LineIndexedText.index(text);
            TestDocumentState docState = new TestDocumentState(globalState, docId, indexedText);
            TestSectionState sectionState = new TestSectionState(docState, 0, newGrammarSection(indexedText), this.extension);

            globalState.docStates.put(docId, docState);
            docState.sectionStates.add(sectionState);
            sectionStates.add(sectionState);
        });
        return sectionStates;
    }

    protected static GrammarSection newGrammarSection(LineIndexedText indexedText)
    {
        Matcher matcher = GRAMMAR_LINE_PATTERN.matcher(indexedText.getText());
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
                return indexedText.getText();
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

    private abstract static class AbstractState implements State
    {
        private final MutableMap<String, Object> properties = Maps.mutable.empty();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getProperty(String key)
        {
            return (T) this.properties.get(key);
        }

        @Override
        public void setProperty(String key, Object value)
        {
            if (value == null)
            {
                this.properties.remove(key);
            }
            else
            {
                this.properties.put(key, value);
            }
        }

        @Override
        public void clearProperties()
        {
            this.properties.clear();
        }
    }

    private static class TestGlobalState extends AbstractState implements GlobalState
    {
        private final MutableMap<String, DocumentState> docStates = Maps.mutable.empty();

        @Override
        public DocumentState getDocumentState(String id)
        {
            return this.docStates.get(id);
        }

        @Override
        public void forEachDocumentState(Consumer<? super DocumentState> consumer)
        {
            this.docStates.forEachValue(consumer::accept);
        }
    }

    private static class TestDocumentState extends AbstractState implements DocumentState
    {
        private final MutableList<SectionState> sectionStates = Lists.mutable.empty();
        private final GlobalState globalState;
        private final String id;
        private final LineIndexedText text;

        private TestDocumentState(GlobalState globalState, String id, LineIndexedText text)
        {
            this.globalState = globalState;
            this.id = id;
            this.text = text;
        }

        @Override
        public GlobalState getGlobalState()
        {
            return this.globalState;
        }

        @Override
        public String getDocumentId()
        {
            return this.id;
        }

        @Override
        public String getText()
        {
            return this.text.getText();
        }

        @Override
        public int getLineCount()
        {
            return this.text.getLineCount();
        }

        @Override
        public String getLines(int start, int end)
        {
            return this.text.getLines(start, end);
        }

        @Override
        public int getSectionCount()
        {
            return this.sectionStates.size();
        }

        @Override
        public SectionState getSectionState(int n)
        {
            return this.sectionStates.get(n);
        }

        @Override
        public void forEachSectionState(Consumer<? super SectionState> consumer)
        {
            this.sectionStates.forEach(consumer);
        }
    }

    private static class TestSectionState extends AbstractState implements SectionState
    {
        private final DocumentState docState;
        private final int n;
        private final GrammarSection section;
        private final LegendLSPGrammarExtension extension;

        private TestSectionState(DocumentState docState, int n, GrammarSection section, LegendLSPGrammarExtension extension)
        {
            this.docState = docState;
            this.n = n;
            this.section = section;
            this.extension = extension;
        }

        @Override
        public DocumentState getDocumentState()
        {
            return this.docState;
        }

        @Override
        public int getSectionNumber()
        {
            return this.n;
        }

        @Override
        public GrammarSection getSection()
        {
            return this.section;
        }

        @Override
        public LegendLSPGrammarExtension getExtension()
        {
            return this.extension;
        }
    }
}
