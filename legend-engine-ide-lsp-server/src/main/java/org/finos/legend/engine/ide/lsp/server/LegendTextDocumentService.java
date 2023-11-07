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

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.ide.lsp.server.DocumentStates.DocumentState;
import org.finos.legend.engine.ide.lsp.text.GrammarSectionIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LegendTextDocumentService implements TextDocumentService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendTextDocumentService.class);

    private final LegendLanguageServer server;
    private final DocumentStates docStates = new DocumentStates();

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
        LOGGER.debug("Opened {} (language id: {}, version: {})", uri, doc.getLanguageId(), doc.getVersion());
        DocumentState state = this.docStates.newState(uri, doc.getVersion(), doc.getText());
        if (state.getVersion() != doc.getVersion())
        {
            this.server.logWarningToClient("Tried to open " + uri + " version " + doc.getVersion() + ", but found other version: " + state.getVersion());
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params)
    {
        this.server.checkReady();
        VersionedTextDocumentIdentifier doc = params.getTextDocument();
        String uri = doc.getUri();
        LOGGER.debug("Changed {} (version {})", uri, doc.getVersion());
        DocumentState state = this.docStates.getState(uri);
        if (state == null)
        {
            LOGGER.warn("Cannot process change for {}: no state", uri);
            this.server.logWarningToClient("Cannot process change for " + uri + ": not open in language server");
            return;
        }

        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
        if ((changes == null) || changes.isEmpty())
        {
            LOGGER.debug("No changes to {}", uri);
            state.update(doc.getVersion());
            return;
        }

        if (changes.size() > 1)
        {
            String message = "Expected at most one change to " + uri + ", got " + changes.size() + "; processing only the first";
            LOGGER.warn(message);
            this.server.logWarningToClient(message);
        }
        if (state.update(doc.getVersion(), changes.get(0).getText()))
        {
            try
            {
                publishDiagnosticsToClient(state);
            }
            catch (Exception e)
            {
                LOGGER.error("Error publishing diagnostics for {} to client", uri, e);
                this.server.logErrorToClient("Error publishing diagnostics for " + uri + " to client: see server log for more details");
            }
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params)
    {
        this.server.checkReady();
        String uri = params.getTextDocument().getUri();
        LOGGER.debug("Closed {}", uri);
        this.docStates.removeState(uri);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params)
    {
        this.server.checkReady();
        String uri = params.getTextDocument().getUri();
        LOGGER.debug("Saved {}", uri);
        DocumentState state = this.docStates.getState(uri);
        if (state == null)
        {
            LOGGER.warn("Cannot process save for {}: no state", uri);
            this.server.logWarningToClient("Cannot process save for " + uri + ": not open in language server");
            return;
        }

        String text = params.getText();
        if (text != null)
        {
            state.update(text);
        }
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params)
    {
        return this.server.supplyPossiblyAsync(() -> getDocumentSymbols(params));
    }

    private List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbols(DocumentSymbolParams params)
    {
        this.server.checkReady();
        String uri = params.getTextDocument().getUri();
        DocumentState state = this.docStates.getState(uri);
        if (state == null)
        {
            LOGGER.warn("No state for {}: cannot get symbols", uri);
            this.server.logWarningToClient("Cannot get symbols for " + uri + ": not open in language server");
            return Collections.emptyList();
        }

        GrammarSectionIndex sectionIndex = state.getSectionIndex();
        if (sectionIndex == null)
        {
            LOGGER.warn("No text for {}: cannot get symbols", uri);
            this.server.logWarningToClient("Cannot get symbols for " + uri + ": no text in language server");
            return Collections.emptyList();
        }

        List<Either<SymbolInformation, DocumentSymbol>> results = new ArrayList<>();
        sectionIndex.forEachSection(section ->
        {
            String grammar = section.getGrammar();
            LegendLSPGrammarExtension extension = this.server.getGrammarExtension(grammar);
            if (extension == null)
            {
                LOGGER.warn("Could not get symbols for section of {}: no extension for grammar {}", uri, grammar);
                return;
            }

            LOGGER.debug("Getting symbols for section \"{}\" of {}", grammar, uri);
            extension.getDeclarations(section).forEach(declaration ->
            {
                Range range = toRange(declaration.getLocation());
                Range selectionRange = declaration.hasCoreLocation() ? toRange(declaration.getCoreLocation()) : range;
                DocumentSymbol symbol = new DocumentSymbol(declaration.getIdentifier(), SymbolKind.Object, range, selectionRange);
                results.add(Either.forRight(symbol));
            });
        });
        return results;
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params)
    {
        return this.server.supplyPossiblyAsync(() -> getSemanticTokensRange(params));
    }

    private SemanticTokens getSemanticTokensRange(SemanticTokensRangeParams params)
    {
        String uri = params.getTextDocument().getUri();
        DocumentState state = this.docStates.getState(uri);
        if (state == null)
        {
            LOGGER.warn("No state for {}: cannot get symbols", uri);
            this.server.logWarningToClient("Cannot get symbols for " + uri + ": not open in language server");
            return new SemanticTokens(Collections.emptyList());
        }

        GrammarSectionIndex sectionIndex = state.getSectionIndex();
        if (sectionIndex == null)
        {
            LOGGER.warn("No text for {}: cannot get semantic tokens", uri);
            this.server.logWarningToClient("Cannot get semantic tokens for " + uri + ": no text in language server");
            return new SemanticTokens(Collections.emptyList());
        }

        List<Integer> data = new ArrayList<>();
        Range range = params.getRange();
        int rangeStartLine = range.getStart().getLine();
        int rangeEndLine = range.getEnd().getLine();
        int previousTokenLine = 0;
        for (GrammarSection section : sectionIndex.getSections())
        {
            if (section.getStartLine() > rangeEndLine)
            {
                // past the end of the range
                break;
            }
            if (section.getEndLine() >= rangeStartLine)
            {
                LegendLSPGrammarExtension extension = this.server.getGrammarExtension(section.getGrammar());
                if (extension == null)
                {
                    LOGGER.warn("Could not get semantic tokens for section of {}: no extension for grammar {}", uri, section.getGrammar());
                }
                else
                {
                    List<String> keywords = new ArrayList<>();
                    extension.getKeywords().forEach(kw -> keywords.add(Pattern.quote(kw)));
                    if (!keywords.isEmpty())
                    {
                        Pattern pattern = Pattern.compile("(?<!\\w)(" + String.join("|", keywords) + ")(?!\\w)");
                        int startLine = Math.max(section.getStartLine(), rangeStartLine);
                        int endLine = Math.min(section.getEndLine(), rangeEndLine);
                        for (int n = startLine; n <= endLine; n++)
                        {
                            int previousTokenStart = 0;
                            String line = section.getLine(n);
                            int startIndex = (n == rangeStartLine) ? range.getStart().getCharacter() : 0;
                            int endIndex = (n == rangeEndLine) ? (range.getEnd().getCharacter() + 1) : line.length();
                            Matcher matcher = pattern.matcher(line).region(startIndex, endIndex);
                            while (matcher.find())
                            {
                                int tokenStart = matcher.start();
                                int deltaLine = n - previousTokenLine;
                                int deltaStart = tokenStart - previousTokenStart;
                                int length = matcher.end() - tokenStart;
                                data.add(deltaLine);
                                data.add(deltaStart);
                                data.add(length);
                                data.add(0);
                                data.add(0);
                                previousTokenLine = n;
                                previousTokenStart = tokenStart;
                            }
                        }
                    }
                }
            }
        }
        return new SemanticTokens(data);
    }

    private Boolean matchTrigger(String codeLine, List<String> triggers)
    {
        for (String triggerWord: triggers)
        {
            if ((codeLine.length() - triggerWord.length()) > 0 &&
                    codeLine.substring(codeLine.length() - triggerWord.length()).equals(triggerWord))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams completionParams)
    {
        return this.server.supplyPossiblyAsync(() -> LegendCompletion(completionParams)).thenApply(Either::forLeft);
    }

    private List<CompletionItem> LegendCompletion(CompletionParams completionParams)
    {
        String uri = completionParams.getTextDocument().getUri();
        int line = completionParams.getPosition().getLine();
        int character = completionParams.getPosition().getCharacter();
        String trigger = this.docStates.getState(uri).getSectionIndex().getSection(0).getLine(line).substring(0, character);

        List<String> types = List.of("Integer ", "Date ", "StrictDate ", "String ", "Float ", "Boolean ");
        List<String> multiplicities = List.of("[0..1];", "[0..*];", "[1];", "[*];");

        List<CompletionItem> completions = new ArrayList<>();

        if (matchTrigger(trigger, types))
        {
            for (String completionString : multiplicities)
            {
                CompletionItem completionItem = new CompletionItem(completionString);
                completionItem.setKind(CompletionItemKind.TypeParameter);
                completionItem.setInsertText(completionString);

                CompletionItemLabelDetails detail = new CompletionItemLabelDetails();
                detail.setDescription("Attribute multiplicity");
                completionItem.setLabelDetails(detail);

                completions.add(completionItem);
            }
        }

        if (matchTrigger(trigger, List.of(": ")))
        {
            for (String completionString : types)
            {
                CompletionItem completionItem = new CompletionItem(completionString);
                completionItem.setKind(CompletionItemKind.TypeParameter);
                completionItem.setInsertText(completionString);

                CompletionItemLabelDetails detail = new CompletionItemLabelDetails();
                detail.setDescription("Attribute type");
                completionItem.setLabelDetails(detail);

                completions.add(completionItem);
            }
        }
        return completions;
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem completionItem)
    {
        return CompletableFuture.completedFuture(completionItem);
    }

    @Override
    public CompletableFuture<DocumentDiagnosticReport> diagnostic(DocumentDiagnosticParams params)
    {
        return this.server.supplyPossiblyAsync(() -> getDiagnosticReport(params));
    }

    private DocumentDiagnosticReport getDiagnosticReport(DocumentDiagnosticParams params)
    {
        this.server.checkReady();
        List<Diagnostic> diagnostics = getDiagnostics(params);
        return new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(diagnostics));
    }

    private void publishDiagnosticsToClient(DocumentState state)
    {
        LanguageClient client = this.server.getLanguageClient();
        if (client != null)
        {
            List<Diagnostic> diagnostics = getDiagnostics(state);
            client.publishDiagnostics(new PublishDiagnosticsParams(state.getUri(), diagnostics));
        }
    }

    private List<Diagnostic> getDiagnostics(DocumentDiagnosticParams params)
    {
        String uri = params.getTextDocument().getUri();
        DocumentState state = this.docStates.getState(uri);
        if (state == null)
        {
            LOGGER.warn("No state for {}: cannot get diagnostics", uri);
            this.server.logWarningToClient("Cannot get diagnostics for " + uri + ": not open in language server");
            return Collections.emptyList();
        }

        return getDiagnostics(state);
    }

    private List<Diagnostic> getDiagnostics(DocumentState state)
    {
        GrammarSectionIndex sectionIndex = state.getSectionIndex();
        if (sectionIndex == null)
        {
            LOGGER.warn("No text for {}: cannot get diagnostics", state.getUri());
            this.server.logWarningToClient("Cannot get diagnostics for " + state.getUri() + ": no text in language server");
            return Collections.emptyList();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        sectionIndex.forEachSection(section ->
        {
            LegendLSPGrammarExtension extension = this.server.getGrammarExtension(section.getGrammar());
            if (extension == null)
            {
                LOGGER.warn("Could not get diagnostics for section of {}: no extension for grammar {}", state.getUri(), section.getGrammar());
                return;
            }
            extension.getDiagnostics(section).forEach(d -> diagnostics.add(toDiagnostic(d)));
        });
        return diagnostics;
    }

    private Diagnostic toDiagnostic(LegendDiagnostic diagnostic)
    {
        return new Diagnostic(toRange(diagnostic.getLocation()), diagnostic.getMessage(), toDiagnosticSeverity(diagnostic.getKind()), diagnostic.getType().toString());
    }

    private DiagnosticSeverity toDiagnosticSeverity(LegendDiagnostic.Kind kind)
    {
        switch (kind)
        {
            case Warning:
            {
                return DiagnosticSeverity.Warning;
            }
            case Information:
            {
                return DiagnosticSeverity.Information;
            }
            case Hint:
            {
                return DiagnosticSeverity.Hint;
            }
            case Error:
            {
                return DiagnosticSeverity.Error;
            }
            default:
            {
                LOGGER.warn("Unknown diagnostic severity {}, defaulting to Error.", kind);
                return DiagnosticSeverity.Error;
            }
        }
    }

    private Range toRange(TextInterval interval)
    {
        return new Range(toPosition(interval.getStart()), toPosition(interval.getEnd()));
    }

    private Position toPosition(TextPosition position)
    {
        return new Position(position.getLine(), position.getColumn());
    }
}
