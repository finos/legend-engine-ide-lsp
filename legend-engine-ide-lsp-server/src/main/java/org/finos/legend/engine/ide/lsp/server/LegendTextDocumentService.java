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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.finos.legend.engine.ide.lsp.text.LineIndexedText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class LegendTextDocumentService implements TextDocumentService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendTextDocumentService.class);

    private final LegendLanguageServer server;
    private final Map<String, DocumentState> docStates = new HashMap<>();

    LegendTextDocumentService(LegendLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params)
    {
        this.server.checkReady();
        TextDocumentItem doc = params.getTextDocument();
        String uri = doc.getUri();
        if (isLegendFile(uri))
        {
            synchronized (this.docStates)
            {
                LOGGER.debug("opened {} (language id: {}, version: {})", uri, doc.getLanguageId(), doc.getVersion());
                this.docStates.put(uri, new DocumentState(doc.getVersion(), doc.getText()));
            }
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params)
    {
        this.server.checkReady();
        VersionedTextDocumentIdentifier doc = params.getTextDocument();
        String uri = doc.getUri();
        if (isLegendFile(uri))
        {
            synchronized (this.docStates)
            {
                LOGGER.debug("changed {} (version {})", uri, doc.getVersion());
                DocumentState state = this.docStates.get(uri);
                if (state == null)
                {
                    LOGGER.warn("cannot process change for {}: no state", uri);
                    this.server.logWarningToClient("cannot process change for " + uri + ": not open on language server");
                    return;
                }
                state.version = doc.getVersion();
                List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
                if ((changes == null) || changes.isEmpty())
                {
                    LOGGER.debug("no changes to {}", uri);
                }
                else
                {
                    if (changes.size() > 1)
                    {
                        String message = "Expected at most one change, got " + changes.size() + "; processing only the first";
                        LOGGER.warn(message);
                        this.server.logWarningToClient(message);
                    }
                    state.setText(changes.get(0).getText());
                }
            }
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params)
    {
        this.server.checkReady();
        TextDocumentIdentifier doc = params.getTextDocument();
        String uri = doc.getUri();
        if (isLegendFile(uri))
        {
            synchronized (this.docStates)
            {
                LOGGER.debug("closed {}", uri);
                this.docStates.remove(uri);
            }
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params)
    {
        this.server.checkReady();
        TextDocumentIdentifier doc = params.getTextDocument();
        String uri = doc.getUri();
        if (isLegendFile(uri))
        {
            synchronized (this.docStates)
            {
                LOGGER.debug("saved {}", uri);
                DocumentState state = this.docStates.get(uri);
                if (state == null)
                {
                    LOGGER.warn("cannot process save for {}: no state", uri);
                    this.server.logWarningToClient("cannot process save for " + uri + ": not open on language server");
                    return;
                }
                String text = params.getText();
                if (text != null)
                {
                    state.setText(text);
                }
            }
        }
    }

    private boolean isLegendFile(String uri)
    {
        return (uri != null) && uri.endsWith(".pure");
    }

    private static class DocumentState
    {
        private int version;
        private LineIndexedText text;

        private DocumentState(int version, String text)
        {
            this.version = version;
            setText(text);
        }

        String getText()
        {
            return this.text.getText();
        }

        void setText(String newText)
        {
            this.text = LineIndexedText.index(newText);
        }
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params)
    {
        this.server.logToClient("called semanticTokensRange");
        MutableList<Integer> data = Lists.mutable.empty();
        data = Lists.mutable.of(1,1,5,0,0,2,2,5,0,0,0,7,6,0,0);
        return CompletableFuture.completedFuture(new SemanticTokens(data));
    }
}
