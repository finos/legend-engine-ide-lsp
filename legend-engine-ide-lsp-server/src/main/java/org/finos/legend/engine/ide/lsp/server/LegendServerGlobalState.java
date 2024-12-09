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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPFeature;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.state.CancellationToken;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.NotebookDocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.text.GrammarSectionIndex;
import org.finos.legend.engine.ide.lsp.text.LineIndexedText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegendServerGlobalState extends AbstractState implements GlobalState
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendServerGlobalState.class);

    private final Map<String, LegendServerDocumentState> docs = new ConcurrentHashMap<>();
    private final Map<String, CancellationToken> cancellationTokens = new ConcurrentHashMap<>();
    private final LegendLanguageServer server;

    LegendServerGlobalState(LegendLanguageServer server)
    {
        super();
        this.server = server;
    }

    @Override
    public LegendServerDocumentState getDocumentState(String id)
    {
        return this.docs.get(id);
    }

    @Override
    public void forEachDocumentState(Consumer<? super DocumentState> consumer)
    {
        this.docs.values().forEach(consumer);
    }

    @Override
    public void logInfo(String message)
    {
        this.server.logInfoToClient(message);
    }

    @Override
    public void logWarning(String message)
    {
        this.server.logWarningToClient(message);
    }

    @Override
    public void logError(String message)
    {
        this.server.logErrorToClient(message);
    }

    @Override
    public ForkJoinPool getForkJoinPool()
    {
        return this.server.getForkJoinPool();
    }

    synchronized LegendServerDocumentState getOrCreateDocState(String uri)
    {
        return this.docs.computeIfAbsent(uri, k -> new LegendServerDocumentState(this, uri));
    }

    public LegendServerDocumentState getOrCreateNotebookDocState(String uri)
    {
        return this.docs.computeIfAbsent(uri, k -> new LegendServerNotebookDocumentState(this, uri));
    }

    synchronized void deleteDocState(String uri)
    {
        this.deleteDocState(uri, true);
    }

    synchronized void deleteDocState(String uri, boolean cleanProperties)
    {
        LegendServerDocumentState documentState = this.docs.remove(uri);

        if (documentState != null)
        {
            documentState.destroy();
        }

        if (cleanProperties)
        {
            this.clearProperties();
        }
    }

    synchronized void renameDoc(String oldUri, String newUri)
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

    synchronized void addFolder(String folderUri)
    {
        try
        {
            Path folderPath = Paths.get(URI.create(folderUri));
            try (Stream<Path> stream = Files.find(folderPath, Integer.MAX_VALUE,
                    (path, attr) -> attr.isRegularFile() && path.getFileName().toString().endsWith(".pure"),
                    FileVisitOption.FOLLOW_LINKS))
            {
                stream.forEach(path ->
                {
                    StringBuilder builder = new StringBuilder(folderUri);
                    folderPath.relativize(path).forEach(p ->
                    {
                        if (builder.charAt(builder.length() - 1) != '/')
                        {
                            builder.append('/');
                        }
                        builder.append(URLEncoder.encode(p.toString(), StandardCharsets.UTF_8));
                    });
                    LegendServerDocumentState docState = getOrCreateDocState(builder.toString());
                    docState.initialize();
                });
            }
            this.clearProperties();
        }
        catch (Exception e)
        {
            LOGGER.error("Error initializing document states for folder {}", folderUri, e);
        }
    }

    synchronized void removeFolder(String folderUri)
    {
        this.docs.keySet().removeIf(uri -> uri.startsWith(folderUri));
        this.clearProperties();
    }

    @Override
    public Collection<LegendLSPGrammarExtension> getAvailableGrammarExtensions()
    {
        return this.server.getGrammarLibrary().getExtensions();
    }

    @Override
    public Collection<LegendLSPFeature> getAvailableLegendLSPFeatures()
    {
        return this.server.getFeatures();
    }

    @Override
    public String getSetting(String key)
    {
        return this.server.getSetting(key);
    }

    @Override
    public CancellationToken cancellationToken(String requestId)
    {
        return this.cancellationTokens.computeIfAbsent(requestId, x -> new CancellationToken(x, this.cancellationTokens::remove));
    }

    static class LegendServerDocumentState extends AbstractState implements DocumentState
    {
        private final LegendServerGlobalState globalState;
        private final String uri;
        private Integer version;
        private GrammarSectionIndex sectionIndex;
        private volatile List<LegendServerSectionState> sectionStates;

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
        public synchronized String getText()
        {
            return (this.sectionIndex == null) ? null : this.sectionIndex.getText();
        }

        synchronized LineIndexedText getIndexedText()
        {
            return (this.sectionIndex == null) ? null : this.sectionIndex.getIndexedText();
        }

        @Override
        public synchronized int getLineCount()
        {
            return (this.sectionIndex == null) ? 0 : this.sectionIndex.getLineCount();
        }

        @Override
        public synchronized String getLine(int line)
        {
            return (this.sectionIndex == null) ? null : this.sectionIndex.getLine(line);
        }

        @Override
        public synchronized String getLines(int start, int end)
        {
            return (this.sectionIndex == null) ? null : this.sectionIndex.getLines(start, end);
        }

        @Override
        public synchronized int getSectionCount()
        {
            return this.sectionStates.size();
        }

        @Override
        public synchronized SectionState getSectionState(int n)
        {
            return this.sectionStates.get(n);
        }

        @Override
        public synchronized SectionState getSectionStateAtLine(int line)
        {
            if (this.sectionIndex != null)
            {
                int n = this.sectionIndex.getSectionNumberAtLine(line);
                if (n != -1)
                {
                    return this.sectionStates.get(n);
                }
            }
            return null;
        }

        @Override
        public void forEachSectionState(Consumer<? super SectionState> consumer)
        {
            List<LegendServerSectionState> currentSectionsStates = this.sectionStates;
            if (currentSectionsStates != null)
            {
                currentSectionsStates.forEach(consumer);
            }
        }

        Integer getVersion()
        {
            return version;
        }

        synchronized boolean isInitialized()
        {
            return this.sectionStates != null;
        }

        synchronized boolean isOpen()
        {
            return this.version != null;
        }

        synchronized boolean open(int version, String text)
        {
            if (this.version != null)
            {
                LOGGER.warn("{} already opened at version {}: overwriting with version {}", this.uri, this.version, version);
            }
            this.version = version;
            return setText(text);
        }

        synchronized void close()
        {
            this.version = null;
        }

        synchronized boolean change(int newVersion, LineIndexedText newText)
        {
            if ((this.version != null) && (newVersion < this.version))
            {
                LOGGER.warn("Changing {} from version {} to {} (out of order updates?)", this.uri, this.version, newVersion);
                return false;
            }
            this.version = newVersion;
            return setText(newText);
        }

        synchronized boolean save(String text)
        {
            return text != null && setText(text);
        }

        synchronized boolean initialize()
        {
            if (!isInitialized())
            {
                String message = "Initializing file: " + this.uri;
                this.globalState.server.logInfoToClient(message);
                LOGGER.debug(message);
                return loadText();
            }
            else
            {
                return false;
            }
        }

        synchronized boolean loadText()
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
                this.globalState.server.logWarningToClient("Error loading text for " + this.uri + ((e.getMessage() == null) ? "" : (": " + e.getMessage())));
                return false;
            }

            this.version = null;
            return setText(text);
        }

        private synchronized boolean setText(String newText)
        {
            return this.setText(LineIndexedText.index(newText));
        }

        private synchronized boolean setText(LineIndexedText newText)
        {
            if ((this.sectionIndex == null) ? (newText == null) : this.sectionIndex.getText().equals(newText))
            {
                // text is unchanged
                return false;
            }

            LOGGER.info("Clearing global and {} properties", this.uri);
            this.sectionIndex = (newText == null) ? null : parse(newText);
            this.recreateSectionStates();
            return true;
        }

        GrammarSectionIndex parse(LineIndexedText newText)
        {
            return GrammarSectionIndex.parse(newText);
        }

        public synchronized void recreateSectionStates()
        {
            this.destroy();
            this.sectionStates = createSectionStates(this.sectionIndex);
            this.clearProperties();
        }

        private void destroy()
        {
            if (this.sectionStates != null)
            {
                this.sectionStates.forEach(x ->
                {
                    if (x.extension != null)
                    {
                        x.extension.destroy(x);
                    }
                });
            }
        }

        private synchronized List<LegendServerSectionState> createSectionStates(GrammarSectionIndex index)
        {
            if (index == null)
            {
                return Collections.emptyList();
            }

            List<LegendServerSectionState> states = new ArrayList<>(index.getSectionCount());
            index.forEachSection(section ->
            {
                int n = states.size();
                LegendLSPGrammarExtension extension = this.globalState.server.getGrammarExtension(section.getGrammar());
                LegendServerSectionState sectionState = new LegendServerSectionState(this, n, section, extension);
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
        private final LegendLSPGrammarExtension extension;

        private LegendServerSectionState(LegendServerDocumentState docState, int n, GrammarSection grammarSection, LegendLSPGrammarExtension extension)
        {
            this.docState = docState;
            this.n = n;
            this.grammarSection = grammarSection;
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
            return this.grammarSection;
        }

        @Override
        public LegendLSPGrammarExtension getExtension()
        {
            return this.extension;
        }
    }

    private static class LegendServerNotebookDocumentState extends LegendServerDocumentState implements NotebookDocumentState
    {
        public LegendServerNotebookDocumentState(LegendServerGlobalState globalState, String uri)
        {
            super(globalState, uri);
        }

        @Override
        GrammarSectionIndex parse(LineIndexedText newText)
        {
            return GrammarSectionIndex.parse(newText, "purebook");
        }
    }
}
