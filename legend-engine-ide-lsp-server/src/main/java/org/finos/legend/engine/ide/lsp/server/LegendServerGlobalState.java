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

import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.text.GrammarSectionIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

class LegendServerGlobalState extends AbstractState implements GlobalState
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendServerGlobalState.class);

    private final Map<String, LegendServerDocumentState> docs = new HashMap<>();
    private final LegendLanguageServer server;

    LegendServerGlobalState(LegendLanguageServer server)
    {
        super();
        this.server = server;
    }

    @Override
    public LegendServerDocumentState getDocumentState(String id)
    {
        synchronized (this.lock)
        {
            return this.docs.get(id);
        }
    }

    @Override
    public void forEachDocumentState(Consumer<? super DocumentState> consumer)
    {
        synchronized (this.lock)
        {
            this.docs.values().forEach(consumer);
        }
    }

    LegendServerDocumentState getOrCreateDocState(String uri)
    {
        synchronized (this.lock)
        {
            return this.docs.computeIfAbsent(uri, k -> new LegendServerDocumentState(this, uri));
        }
    }

    void deleteDocState(String uri)
    {
        synchronized (this.lock)
        {
            this.docs.remove(uri);
        }
    }

    void renameDoc(String oldUri, String newUri)
    {
        synchronized (this.lock)
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
                newState.sectionStates = newState.createSectionStates(newState.sectionIndex);
                this.docs.put(newUri, newState);
            }
        }
    }

    void addFolder(String folderUri)
    {
        try
        {
            Path folderPath = Paths.get(URI.create(folderUri));
            synchronized (this.lock)
            {
                try (Stream<Path> stream = Files.find(folderPath, Integer.MAX_VALUE,
                        (path, attr) -> attr.isRegularFile() && path.getFileName().toString().endsWith(".pure"),
                        FileVisitOption.FOLLOW_LINKS))
                {
                    stream.forEach(path ->
                    {
                        String uri = path.toUri().toString();
                        LegendServerDocumentState docState = getOrCreateDocState(uri);
                        docState.initialize();
                    });
                }
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error initializing document states for folder {}", folderUri, e);
        }
    }

    void removeFolder(String folderUri)
    {
        synchronized (this.lock)
        {
            this.docs.keySet().removeIf(uri -> uri.startsWith(folderUri));
        }
    }

    static class LegendServerDocumentState extends AbstractState implements DocumentState
    {
        private final LegendServerGlobalState globalState;
        private final String uri;
        private Integer version;
        private GrammarSectionIndex sectionIndex;
        private List<LegendServerSectionState> sectionStates;

        private LegendServerDocumentState(LegendServerGlobalState globalState, String uri)
        {
            super(globalState);
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
            synchronized (this.lock)
            {
                return (this.sectionIndex == null) ? null : this.sectionIndex.getText();
            }
        }

        @Override
        public int getSectionCount()
        {
            synchronized (this.lock)
            {
                return this.sectionStates.size();
            }
        }

        @Override
        public SectionState getSectionState(int n)
        {
            synchronized (this.lock)
            {
                return this.sectionStates.get(n);
            }
        }

        @Override
        public void forEachSectionState(Consumer<? super SectionState> consumer)
        {
            synchronized (this.lock)
            {
                this.sectionStates.forEach(consumer);
            }
        }

        boolean isInitialized()
        {
            synchronized (this.lock)
            {
                return this.sectionStates != null;
            }
        }

        boolean isOpen()
        {
            synchronized (this.lock)
            {
                return this.version != null;
            }
        }

        void open(int version, String text)
        {
            synchronized (this.lock)
            {
                if (this.version != null)
                {
                    LOGGER.warn("{} already opened at version {}: overwriting with version {}", this.uri, this.version, version);
                }
                this.version = version;
                setText(text);
            }
        }

        void close()
        {
            synchronized (this.lock)
            {
                this.version = null;
            }
        }

        void change(int newVersion)
        {
            synchronized (this.lock)
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
            synchronized (this.lock)
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
                synchronized (this.lock)
                {
                    setText(text);
                }
            }
        }

        void initialize()
        {
            synchronized (this.lock)
            {
                if (!isInitialized())
                {
                    loadText();
                }
            }
        }

        void loadText()
        {
            synchronized (this.lock)
            {
                String text;
                try
                {
                    Path path = Paths.get(URI.create(this.uri));
                    text = Files.readString(path);
                }
                catch (Exception e)
                {
                    LOGGER.warn("Error loading text for {}", this.uri, e);
                    return;
                }

                this.version = null;
                setText(text);
            }
        }

        private void setText(String newText)
        {
            if ((this.sectionIndex == null) ? (newText == null) : this.sectionIndex.getText().equals(newText))
            {
                // text is unchanged
                return;
            }

            this.sectionIndex = (newText == null) ? null : GrammarSectionIndex.parse(newText);
            this.sectionStates = createSectionStates(this.sectionIndex);
        }

        private List<LegendServerSectionState> createSectionStates(GrammarSectionIndex index)
        {
            if (index == null)
            {
                return Collections.emptyList();
            }

            List<LegendServerSectionState> states = new ArrayList<>(index.getSectionCount());
            index.forEachSection(section ->
            {
                int n = states.size();
                LegendServerSectionState sectionState = new LegendServerSectionState(this, n, section);
                LegendLSPGrammarExtension extension = this.globalState.server.getGrammarExtension(section.getGrammar());
                if (extension == null)
                {
                    LOGGER.warn("Could not initialize section {} of {}: no extension for grammar {}", n, this.uri, section.getGrammar());
                }
                else
                {
                    try
                    {
                        extension.initialize(sectionState);
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("Error initializing section {} of {}", n, this.uri, e);
                    }
                }
                states.add(sectionState);
            });
            return states;
        }
    }

    private static class LegendServerSectionState extends AbstractState implements SectionState
    {
        private final LegendServerDocumentState docState;
        private final int n;
        private final GrammarSection grammarSection;

        private LegendServerSectionState(LegendServerDocumentState docState, int n, GrammarSection grammarSection)
        {
            super(docState.lock);
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
