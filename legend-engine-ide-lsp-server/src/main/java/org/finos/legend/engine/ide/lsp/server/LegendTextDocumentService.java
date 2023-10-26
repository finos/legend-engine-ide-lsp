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

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.finos.legend.engine.ide.lsp.extension.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarLibrary;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.ide.lsp.text.GrammarSectionIndex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
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
                LOGGER.debug("Opened {} (language id: {}, version: {})", uri, doc.getLanguageId(), doc.getVersion());
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
                LOGGER.debug("Changed {} (version {})", uri, doc.getVersion());
                DocumentState state = this.docStates.get(uri);
                if (state == null)
                {
                    LOGGER.warn("Cannot process change for {}: no state", uri);
                    this.server.logWarningToClient("Cannot process change for " + uri + ": not open in language server");
                    return;
                }
                state.setVersion(doc.getVersion());
                List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
                if ((changes == null) || changes.isEmpty())
                {
                    LOGGER.debug("No changes to {}", uri);
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
                    diagnostic(new DocumentDiagnosticParams(doc));
                }
            }
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params)
    {
        this.server.checkReady();
        String uri = params.getTextDocument().getUri();
        if (isLegendFile(uri))
        {
            synchronized (this.docStates)
            {
                LOGGER.debug("Closed {}", uri);
                this.docStates.remove(uri);
            }
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params)
    {
        this.server.checkReady();
        String uri = params.getTextDocument().getUri();
        if (isLegendFile(uri))
        {
            synchronized (this.docStates)
            {
                LOGGER.debug("Saved {}", uri);
                DocumentState state = this.docStates.get(uri);
                if (state == null)
                {
                    LOGGER.warn("Cannot process save for {}: no state", uri);
                    this.server.logWarningToClient("Cannot process save for " + uri + ": not open in language server");
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

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params)
    {
        return isLegendFile(params.getTextDocument()) ?
                this.server.supplyPossiblyAsync(() -> getDocumentSymbols(params)) :
                CompletableFuture.completedFuture(Collections.emptyList());
    }

    private List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbols(DocumentSymbolParams params)
    {
        this.server.checkReady();
        String uri = params.getTextDocument().getUri();
        GrammarSectionIndex sectionIndex;
        synchronized (this.docStates)
        {
            DocumentState state = this.docStates.get(uri);
            if (state == null)
            {
                LOGGER.warn("No state for {}: cannot get symbols", uri);
                this.server.logWarningToClient("Cannot get symbols for " + uri + ": not open in language server");
                return Collections.emptyList();
            }
            sectionIndex = state.getSectionIndex();
        }

        if (sectionIndex == null)
        {
            LOGGER.warn("No text for {}: cannot get symbols", uri);
            this.server.logWarningToClient("Cannot get symbols for " + uri + ": no text in language server");
            return Collections.emptyList();
        }

        LegendLSPGrammarLibrary grammarLibrary = this.server.getGrammarLibrary();
        List<Either<SymbolInformation, DocumentSymbol>> results = new ArrayList<>();
        sectionIndex.getSections().forEach(section ->
        {
            String grammar = section.getGrammar();
            LegendLSPGrammarExtension extension = grammarLibrary.getExtension(grammar);
            if (extension == null)
            {
                String message = "Cannot find extension for grammar: " + grammar;
                LOGGER.warn(message);
                this.server.logWarningToClient(message);
            }
            else
            {
                LOGGER.debug("Getting symbols for section \"{}\" of {}", grammar, uri);
                extension.getDeclarations(section).forEach(declaration ->
                {
                    Range range = toRange(declaration.getLocation());
                    Range selectionRange = declaration.hasCoreLocation() ? toRange(declaration.getCoreLocation()) : range;
                    DocumentSymbol symbol = new DocumentSymbol(declaration.getIdentifier(), SymbolKind.Object, range, selectionRange);
                    results.add(Either.forRight(symbol));
                });
            }
        });
        return results;
    }

    private Range toRange(TextInterval interval)
    {
        return new Range(toPosition(interval.getStart()), toPosition(interval.getEnd()));
    }

    private Position toPosition(TextPosition position)
    {
        return new Position(position.getLine(), position.getColumn());
    }

    private boolean isLegendFile(TextDocumentIdentifier documentId)
    {
        return isLegendFile(documentId.getUri());
    }

    private boolean isLegendFile(String uri)
    {
        return (uri != null) && uri.endsWith(".pure");
    }

    private static class DocumentState
    {
        private int version;
        private GrammarSectionIndex sectionIndex;

        private DocumentState(int version, String text)
        {
            this.version = version;
            setText(text);
        }

        void setVersion(int version)
        {
            this.version = version;
        }

        int getVersion()
        {
            return this.version;
        }

        void setText(String newText)
        {
            this.sectionIndex = (newText == null) ? null : GrammarSectionIndex.parse(newText);
        }

        GrammarSectionIndex getSectionIndex()
        {
            return this.sectionIndex;
        }
    }

    @Override
    public CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params)
    {
        String uri = params.getTextDocument().getUri();
        GrammarSection section = this.docStates.get(uri).getSectionIndex().getSection(0);
        GrammarSectionIndex parsed = (section.getText() == null) ? null : GrammarSectionIndex.parse(section.getText());
        if (parsed == null)
        {
            return null;
        }

        Iterable<? extends LegendDiagnostic> legendDiagnostics = this.server.getGrammarLibrary().getExtension(section.getGrammar()).getDiagnostics(section);
        if(legendDiagnostics == null)
        {
            return null;
        }

        LegendDiagnostic legendDiagnostic = legendDiagnostics.iterator().next();
        Diagnostic diagnostic = new Diagnostic(this.toRange(legendDiagnostic.getLocation()), legendDiagnostic.getMessage(), legendDiagnostic.getSeverity(), legendDiagnostic.getType());

        CompletableFuture<DocumentDiagnosticReport> documentDiagnosticReport = CompletableFuture.completedFuture(new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(List.of(diagnostic))));
        return documentDiagnosticReport;
    }
}
