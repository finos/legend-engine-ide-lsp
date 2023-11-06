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

package org.finos.legend.engine.ide.lsp.server;

import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.text.GrammarSectionIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class LegendServerGlobalState implements GlobalState
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendServerGlobalState.class);

    private final Map<String, LegendServerDocumentState> docs = new HashMap<>();

    @Override
    public LegendServerDocumentState getDocumentState(String id)
    {
        synchronized (this)
        {
            return this.docs.get(id);
        }
    }

    @Override
    public void forEachDocumentState(Consumer<? super DocumentState> consumer)
    {
        synchronized (this)
        {
            this.docs.values().forEach(consumer);
        }
    }

    LegendServerDocumentState getOrCreateDocState(String uri)
    {
        synchronized (this)
        {
            return this.docs.computeIfAbsent(uri, k -> new LegendServerDocumentState(this, uri));
        }
    }

    void deleteDocState(String uri)
    {
        synchronized (this)
        {
            this.docs.remove(uri);
        }
    }

    void renameDoc(String oldUri, String newUri)
    {
        synchronized (this)
        {
            if (this.docs.containsKey(newUri))
            {
                throw new IllegalStateException("Cannot rename " + oldUri + " to " + newUri + ": file already exists");
            }

            LegendServerDocumentState oldState = this.docs.remove(oldUri);
            if (oldState != null)
            {
                LegendServerDocumentState newState = new LegendServerDocumentState(this, newUri);
                newState.version = oldState.version;
                newState.sectionIndex = oldState.sectionIndex;
                newState.sectionStates = oldState.sectionStates;
                this.docs.put(newUri, newState);
            }
        }
    }

    static class LegendServerDocumentState implements DocumentState
    {
        private final LegendServerGlobalState globalState;
        private final String uri;
        private Integer version;
        private GrammarSectionIndex sectionIndex;
        private List<LegendServerSectionState> sectionStates;

        private LegendServerDocumentState(LegendServerGlobalState globalState, String uri)
        {
            this.globalState = globalState;
            this.uri = uri;
        }

        @Override
        public GlobalState getGlobalState()
        {
            return this.globalState;
        }

        @Override
        public String getDocumentId()
        {
            return this.uri;
        }

        @Override
        public String getText()
        {
            synchronized (this.globalState)
            {
                initializeText();
                return (this.sectionIndex == null) ? null : this.sectionIndex.getText();
            }
        }

        @Override
        public int getSectionCount()
        {
            synchronized (this.globalState)
            {
                initializeText();
                return this.sectionStates.size();
            }
        }

        @Override
        public SectionState getSectionState(int n)
        {
            synchronized (this.globalState)
            {
                initializeText();
                return this.sectionStates.get(n);
            }
        }

        @Override
        public void forEachSectionState(Consumer<? super SectionState> consumer)
        {
            synchronized (this.globalState)
            {
                initializeText();
                this.sectionStates.forEach(consumer);
            }
        }

        boolean isOpen()
        {
            synchronized (this.globalState)
            {
                return this.version != null;
            }
        }

        void open(int version, String text)
        {
            synchronized (this.globalState)
            {
                if (this.version != null)
                {
                    LOGGER.warn("{} already opened at version {}: overwriting", this.uri, this.version);
                }
                this.version = version;
                setText(text);
            }
        }

        void close()
        {
            synchronized (this.globalState)
            {
                this.version = null;
                clearText();
            }
        }

        void change(int newVersion)
        {
            synchronized (this.globalState)
            {
                if ((this.version != null) && (newVersion < this.version))
                {
                    LOGGER.warn("Changing {} from version {} to {}", this.uri, this.version, newVersion);
                }
                this.version = newVersion;
            }
        }

        void change(int newVersion, String newText)
        {
            synchronized (this.globalState)
            {
                if ((this.version != null) && (newVersion <= this.version))
                {
                    LOGGER.warn("Changing {} from version {} to {}", this.uri, this.version, newVersion);
                }
                this.version = newVersion;
                setText(newText);
            }
        }

        void save(String text)
        {
            if (text != null)
            {
                synchronized (this.globalState)
                {
                    setText(text);
                }
            }
        }

        void clearText()
        {
            synchronized (this.globalState)
            {
                this.sectionIndex = null;
                this.sectionStates = null;
            }
        }

        private void initializeText()
        {
            if (this.sectionStates == null)
            {
                try
                {
                    Path path = Paths.get(URI.create(this.uri));
                    String text = Files.readString(path);
                    setText(text);
                }
                catch (Exception e)
                {
                    LOGGER.warn("Error initializing state for {}", this.uri, e);
                    this.sectionIndex = null;
                    this.sectionStates = Collections.emptyList();
                }
            }
        }

        private void setText(String newText)
        {
            if ((this.sectionIndex == null) ? (newText == null) : this.sectionIndex.getText().equals(newText))
            {
                // text is unchanged
                return;
            }

            if (newText == null)
            {
                this.sectionIndex = null;
                this.sectionStates = Collections.emptyList();
            }
            else
            {
                this.sectionIndex = GrammarSectionIndex.parse(newText);
                this.sectionStates = new ArrayList<>(this.sectionIndex.getSectionCount());
                this.sectionIndex.forEachSection(section -> this.sectionStates.add(new LegendServerSectionState(this, this.sectionStates.size(), section)));
            }
        }
    }

    private static class LegendServerSectionState implements SectionState
    {
        private final LegendServerDocumentState docState;
        private final int n;
        private final GrammarSection grammarSection;

        private LegendServerSectionState(LegendServerDocumentState docState, int n, GrammarSection grammarSection)
        {
            this.docState = docState;
            this.n = n;
            this.grammarSection = grammarSection;
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
            return this.grammarSection;
        }
    }
}
