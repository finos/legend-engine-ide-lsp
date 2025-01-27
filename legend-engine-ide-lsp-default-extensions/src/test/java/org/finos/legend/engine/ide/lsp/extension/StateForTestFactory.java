/*
 * Copyright 2024 Goldman Sachs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.legend.engine.ide.lsp.extension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.finos.legend.engine.ide.lsp.extension.state.CancellationToken;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.NotebookDocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.state.State;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.text.LineIndexedText;

public class StateForTestFactory
{
    private static final Pattern GRAMMAR_LINE_PATTERN = Pattern.compile("^\\h*+###(?<parser>\\w++)\\h*+$\\R?", Pattern.MULTILINE);
    private final List<LegendLSPFeature> features;
    private final List<LegendLSPGrammarExtension> legendLSPGrammarExtensions;

    public StateForTestFactory()
    {
        this.legendLSPGrammarExtensions = loadAvailableExtensions();
        this.features = loadAvailableFeatures();
        this.getLegendLSPGrammarExtensions().forEach(x -> x.startup(new TestGlobalState()));
    }

    public List<LegendLSPGrammarExtension> getLegendLSPGrammarExtensions()
    {
        return legendLSPGrammarExtensions;
    }

    private static List<LegendLSPGrammarExtension> loadAvailableExtensions()
    {
        List<LegendLSPGrammarExtension> grammars = new ArrayList<>();

        ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader classLoader = threadClassLoader == null ? AbstractLSPGrammarExtensionTest.class.getClassLoader() : threadClassLoader;

        ServiceLoader.load(LegendLSPExtensionLoader.class, classLoader)
                .forEach(
                        x -> x.loadLegendLSPGrammarExtensions(classLoader)
                                .forEach(grammars::add)
                );

        return grammars;
    }

    private static List<LegendLSPFeature> loadAvailableFeatures()
    {
        List<LegendLSPFeature> features = new ArrayList<>();

        ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader classLoader = threadClassLoader == null ? AbstractLSPGrammarExtensionTest.class.getClassLoader() : threadClassLoader;

        ServiceLoader.load(LegendLSPExtensionLoader.class, classLoader)
                .forEach(
                        x -> x.loadLegendLSPFeatureExtensions(classLoader)
                                .forEach(features::add)
                );

        return features;
    }

    public GlobalState newGlobalState()
    {
        return new TestGlobalState();
    }

    public SectionState newSectionState(String docId, String text)
    {
        TestGlobalState globalState = new TestGlobalState();
        return this.newSectionState(globalState, docId, text);
    }

    public SectionState newSectionState(GlobalState gs, String docId, String text)
    {
        TestGlobalState globalState = (TestGlobalState) gs;
        LineIndexedText indexedText = LineIndexedText.index(text);
        TestDocumentState docState = new TestDocumentState(globalState, docId, indexedText);
        TestSectionState sectionState = new TestSectionState(docState, 0, newGrammarSection(indexedText));

        globalState.docStates.put(docId, docState);
        docState.sectionStates.add(sectionState);
        gs.clearProperties();
        return sectionState;
    }

    public SectionState newPureBookSectionState(String notebookDocumentUri, String notebookCellUri, String text)
    {
        TestGlobalState globalState = new TestGlobalState();
        return this.newPureBookSectionState(globalState, notebookDocumentUri, notebookCellUri, text);
    }

    public SectionState newPureBookSectionState(GlobalState gs, String notebookDocumentUri, String notebookCellUri, String text)
    {
        TestGlobalState globalState = (TestGlobalState) gs;
        LineIndexedText indexedText = LineIndexedText.index(text);
        TestDocumentState docState = new TestPureBookDocumentState(globalState, notebookDocumentUri, notebookCellUri, indexedText);
        TestSectionState sectionState = new TestSectionState(docState, 0, newGrammarSection(indexedText, "purebook"));

        globalState.docStates.put(notebookCellUri, docState);
        docState.sectionStates.add(sectionState);
        gs.clearProperties();

        return sectionState;
    }

    protected static GrammarSection newGrammarSection(LineIndexedText indexedText)
    {
        return newGrammarSection(indexedText, "Pure");
    }

    protected static GrammarSection newGrammarSection(LineIndexedText indexedText, String defaultGrammar)
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
            grammar = defaultGrammar;
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
            public String getInterval(int startLine, int startColumn, int endLine, int endColumn)
            {
                checkLine(startLine);
                checkLine(endLine);
                return indexedText.getInterval(startLine, startColumn, endLine, endColumn);
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

    public MutableList<SectionState> newSectionStates(MutableMap<String, String> files)
    {
        TestGlobalState globalState = new TestGlobalState();
        return newSectionStates(globalState, files);
    }

    public MutableList<SectionState> newSectionStates(GlobalState gs, MutableMap<String, String> files)
    {
        TestGlobalState globalState = (TestGlobalState) gs;
        MutableList<SectionState> sectionStates = Lists.mutable.empty();
        files.forEach((docId, text) ->
        {
            LineIndexedText indexedText = LineIndexedText.index(text);
            TestDocumentState docState = new TestDocumentState(globalState, docId, indexedText);
            TestSectionState sectionState = new TestSectionState(docState, 0, newGrammarSection(indexedText));

            globalState.docStates.put(docId, docState);
            docState.sectionStates.add(sectionState);
            sectionStates.add(sectionState);
        });
        return sectionStates;
    }

    private class TestGlobalState extends AbstractState implements GlobalState
    {
        private final MutableMap<String, DocumentState> docStates = Maps.mutable.empty();
        private final Map<String, CancellationToken> cancellationTokens = new ConcurrentHashMap<>();

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

        @Override
        public Collection<LegendLSPGrammarExtension> getAvailableGrammarExtensions()
        {
            return legendLSPGrammarExtensions;
        }

        @Override
        public Collection<LegendLSPFeature> getAvailableLegendLSPFeatures()
        {
            return StateForTestFactory.this.features;
        }

        @Override
        public String getSetting(String key)
        {
            if (key.equals(Constants.LEGEND_PROTOCOL_VERSION))
            {
                return "vX_X_X";
            }
            return null;
        }

        @Override
        public CancellationToken cancellationToken(String requestId)
        {
            requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
            return this.cancellationTokens.computeIfAbsent(requestId, x -> new CancellationToken(x, this.cancellationTokens::remove));
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

    private static class TestPureBookDocumentState extends TestDocumentState implements NotebookDocumentState
    {
        private final String notebookDocumentUri;

        private TestPureBookDocumentState(GlobalState globalState, String notebookDocumentUri, String notebookCellUri, LineIndexedText text)
        {
            super(globalState, notebookCellUri, text);
            this.notebookDocumentUri = notebookDocumentUri;
        }

        @Override
        public String getNotebookDocumentId()
        {
            return this.notebookDocumentUri;
        }
    }

    private class TestSectionState extends AbstractState implements SectionState
    {
        private final DocumentState docState;
        private final int n;
        private final GrammarSection section;
        private final LegendLSPGrammarExtension extension;

        private TestSectionState(DocumentState docState, int n, GrammarSection section)
        {
            this.docState = docState;
            this.n = n;
            this.section = section;
            this.extension = legendLSPGrammarExtensions.stream().filter(x -> x.getName().equals(section.getGrammar())).findAny().orElse(null);
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

}
