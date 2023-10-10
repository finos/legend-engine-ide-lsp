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

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.finos.legend.engine.ide.lsp.tools.TextTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LegendTextDocumentService implements TextDocumentService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendTextDocumentService.class);

    private final Map<String, DocumentState> docStates = new HashMap<>();

    LegendTextDocumentService()
    {
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params)
    {
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
                        LOGGER.warn("Expected at most one change, got: {}", changes.size());
                    }
                    state.setText(changes.get(0).getText());
                }
            }
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params)
    {
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
                    LOGGER.warn("no state for {}", uri);
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
        private String text;
        private int[] lineIndex;

        private DocumentState(int version, String text)
        {
            this.version = version;
            setText(text);
        }

        int getLineStart(int line)
        {
            checkLine(line);
            return getLineStart_internal(line);
        }

        int getLineEnd(int line)
        {
            checkLine(line);
            return getLineEnd_internal(line);
        }

        int getIndex(Position position)
        {
            return getIndex(position.getLine(), position.getCharacter());
        }

        int getIndex(int line, int character)
        {
            checkLine(line);
            int lineStart = getLineStart_internal(line);
            int lineEnd = getLineEnd_internal(line);
            int lineLen = lineEnd - lineStart;
            if (character >= lineLen)
            {
                throw new IndexOutOfBoundsException("Invalid character offset in line " + line + ": " + character + " (line length: " + lineLen + ")");
            }
            return lineStart + character;
        }

        int getLine(int index)
        {
            if ((index < 0) || (index >= this.text.length()))
            {
                throw new StringIndexOutOfBoundsException("index " + index + ", length " + this.text.length());
            }
            return getLine_internal(index);
        }

        void setText(String newText)
        {
            this.text = newText;
            this.lineIndex = TextTools.indexLines(newText);
        }

        private int getLineStart_internal(int line)
        {
            return this.lineIndex[line];
        }

        private int getLineEnd_internal(int line)
        {
            int nextLine = line + 1;
            return (nextLine == this.lineIndex.length) ? this.text.length() : this.lineIndex[nextLine];
        }

        private int getLine_internal(int index)
        {
            int i = Arrays.binarySearch(this.lineIndex, index);
            return (i >= 0) ? i : -(i + 2);
        }

        private void checkLine(int line)
        {
            if ((line < 0) || (line >= this.lineIndex.length))
            {
                throw new IndexOutOfBoundsException("Invalid line: " + line);
            }
        }
    }
}
