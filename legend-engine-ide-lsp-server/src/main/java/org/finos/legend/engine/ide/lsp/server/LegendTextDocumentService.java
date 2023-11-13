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
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
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
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.ide.lsp.server.LegendServerGlobalState.LegendServerDocumentState;
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

    LegendTextDocumentService(LegendLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
        synchronized (globalState)
        {
            this.server.checkReady();
            TextDocumentItem doc = params.getTextDocument();
            String uri = doc.getUri();
            LOGGER.debug("Opened {} (language id: {}, version: {})", uri, doc.getLanguageId(), doc.getVersion());
            LegendServerDocumentState docState = globalState.getOrCreateDocState(uri);
            docState.open(doc.getVersion(), doc.getText());
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
        synchronized (globalState)
        {
            this.server.checkReady();
            VersionedTextDocumentIdentifier doc = params.getTextDocument();
            String uri = doc.getUri();
            LOGGER.debug("Changed {} (version {})", uri, doc.getVersion());

            LegendServerDocumentState docState = globalState.getDocumentState(uri);
            if (docState == null)
            {
                LOGGER.warn("Change to {} (version {}) before it was opened", uri, doc.getVersion());
                docState = globalState.getOrCreateDocState(uri);
            }

            List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
            if ((changes == null) || changes.isEmpty())
            {
                LOGGER.debug("No changes to {}", uri);
                docState.change(doc.getVersion());
                return;
            }

            if (changes.size() > 1)
            {
                String message = "Expected at most one change to " + uri + ", got " + changes.size() + "; processing only the first";
                LOGGER.warn(message);
                this.server.logWarningToClient(message);
            }
            docState.change(doc.getVersion(), changes.get(0).getText());
            try
            {
                publishDiagnosticsToClient(docState);
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
        LegendServerGlobalState globalState = this.server.getGlobalState();
        synchronized (globalState)
        {
            this.server.checkReady();
            String uri = params.getTextDocument().getUri();
            LOGGER.debug("Closed {}", uri);
            LegendServerDocumentState docState = globalState.getDocumentState(uri);
            if (docState == null)
            {
                LOGGER.warn("Closed notification for a document that is not open: {}", uri);
            }
            else
            {
                docState.close();
            }
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
        synchronized (globalState)
        {
            this.server.checkReady();
            String uri = params.getTextDocument().getUri();
            LOGGER.debug("Saved {}", uri);
            LegendServerDocumentState docState = globalState.getDocumentState(uri);
            if (docState == null)
            {
                LOGGER.warn("Saved notification for a document that is not open: {}", uri);
            }
            else
            {
                docState.save(params.getText());
            }
        }
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params)
    {
        return this.server.supplyPossiblyAsync(() -> getDocumentSymbols(params));
    }

    private List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbols(DocumentSymbolParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
        synchronized (globalState)
        {
            this.server.checkReady();
            String uri = params.getTextDocument().getUri();
            DocumentState docState = globalState.getDocumentState(uri);
            if (docState == null)
            {
                LOGGER.warn("No state for {}: cannot get symbols", uri);
                this.server.logWarningToClient("Cannot get symbols for " + uri + ": not open in language server");
                return Collections.emptyList();
            }

            List<Either<SymbolInformation, DocumentSymbol>> results = new ArrayList<>();
            docState.forEachSectionState(sectionState ->
            {
                String grammar = sectionState.getSection().getGrammar();
                LegendLSPGrammarExtension extension = sectionState.getExtension();
                if (extension == null)
                {
                    LOGGER.warn("Could not get symbols for section {} of {}: no extension for grammar {}", sectionState.getSectionNumber(), uri, grammar);
                    return;
                }

                LOGGER.debug("Getting symbols for section {} ({}) of {}", sectionState.getSectionNumber(), grammar, uri);
                extension.getDeclarations(sectionState).forEach(declaration ->
                {
                    Range range = toRange(declaration.getLocation());
                    Range selectionRange = declaration.hasCoreLocation() ? toRange(declaration.getCoreLocation()) : range;
                    DocumentSymbol symbol = new DocumentSymbol(declaration.getIdentifier(), SymbolKind.Object, range, selectionRange);
                    results.add(Either.forRight(symbol));
                });
            });
            return results;
        }
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params)
    {
        return this.server.supplyPossiblyAsync(() -> getSemanticTokensRange(params));
    }

    private SemanticTokens getSemanticTokensRange(SemanticTokensRangeParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
        synchronized (globalState)
        {
            String uri = params.getTextDocument().getUri();
            DocumentState docState = globalState.getDocumentState(uri);
            if (docState == null)
            {
                LOGGER.warn("No state for {}: cannot get symbols", uri);
                this.server.logWarningToClient("Cannot get symbols for " + uri + ": not open in language server");
                return new SemanticTokens(Collections.emptyList());
            }

            List<Integer> data = new ArrayList<>();
            Range range = params.getRange();
            int rangeStartLine = range.getStart().getLine();
            int rangeEndLine = range.getEnd().getLine();
            // If the character position of the range end is 0, then the line is exclusive; otherwise, it is inclusive
            boolean isRangeEndLineInclusive = range.getEnd().getCharacter() != 0;
            int previousTokenLine = 0;
            for (int i = 0; i < docState.getSectionCount(); i++)
            {
                SectionState sectionState = docState.getSectionState(i);
                GrammarSection section = sectionState.getSection();
                if (isRangeEndLineInclusive ? (section.getStartLine() > rangeEndLine) : (section.getStartLine() >= rangeEndLine))
                {
                    // past the end of the range
                    break;
                }
                if (section.getEndLine() >= rangeStartLine)
                {
                    LegendLSPGrammarExtension extension = sectionState.getExtension();
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
                            int endLine = Math.min(section.getEndLine(), isRangeEndLineInclusive ? rangeEndLine : (rangeEndLine - 1));
                            for (int n = startLine; n <= endLine; n++)
                            {
                                int previousTokenStart = 0;
                                String line = section.getLine(n);
                                int startIndex = (n == rangeStartLine) ? range.getStart().getCharacter() : 0;
                                int endIndex = (n == rangeEndLine) ? Math.min(range.getEnd().getCharacter(), line.length()) : line.length();
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
        String completionTrigger = this.server.getGlobalState().getOrCreateDocState(uri).getSectionState(0).getSection().getLine(line).substring(0, character);

        String grammar = this.server.getGlobalState().getDocumentState(uri).getSectionState(0).getSection().getGrammar();
        LegendCompletion legendCompletions = this.server.getGrammarExtension(grammar).getCompletions(completionTrigger);

        return getCompletionItems(legendCompletions);
    }

    private List<CompletionItem> getCompletionItems(LegendCompletion legendCompletions)
    {
        List<CompletionItem> completions = new ArrayList<>();

        for (String suggestion : legendCompletions.getSuggestions())
        {
            CompletionItem completionItem = new CompletionItem(suggestion);
            completionItem.setInsertText(suggestion);
            CompletionItemLabelDetails detail = new CompletionItemLabelDetails();
            detail.setDescription(legendCompletions.getType());
            completionItem.setLabelDetails(detail);
            completions.add(completionItem);
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

    private void publishDiagnosticsToClient(DocumentState docState)
    {
        LanguageClient client = this.server.getLanguageClient();
        if (client != null)
        {
            List<Diagnostic> diagnostics = getDiagnostics(docState);
            client.publishDiagnostics(new PublishDiagnosticsParams(docState.getDocumentId(), diagnostics));
        }
    }

    private List<Diagnostic> getDiagnostics(DocumentDiagnosticParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
        synchronized (globalState)
        {
            String uri = params.getTextDocument().getUri();
            DocumentState docState = globalState.getDocumentState(uri);
            if (docState == null)
            {
                LOGGER.warn("No state for {}: cannot get diagnostics", uri);
                this.server.logWarningToClient("Cannot get diagnostics for " + uri + ": not open in language server");
                return Collections.emptyList();
            }

            return getDiagnostics(docState);
        }
    }

    private List<Diagnostic> getDiagnostics(DocumentState docState)
    {
        List<Diagnostic> diagnostics = new ArrayList<>();
        docState.forEachSectionState(sectionState ->
        {
            String grammar = sectionState.getSection().getGrammar();
            LegendLSPGrammarExtension extension = sectionState.getExtension();
            if (extension == null)
            {
                LOGGER.warn("Could not get diagnostics for section of {}: no extension for grammar {}", docState.getDocumentId(), grammar);
                return;
            }
            extension.getDiagnostics(sectionState).forEach(d -> diagnostics.add(toDiagnostic(d)));
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

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params)
    {
        return this.server.supplyPossiblyAsync(() -> getCodeLenses(params));
    }

    private List<? extends CodeLens> getCodeLenses(CodeLensParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
        synchronized (globalState)
        {
            String uri = params.getTextDocument().getUri();
            DocumentState docState = globalState.getDocumentState(uri);
            if (docState == null)
            {
                LOGGER.warn("No state for {}: cannot get code lenses", uri);
                this.server.logWarningToClient("Cannot get code lenses for " + uri + ": not open in language server");
                return Collections.emptyList();
            }

            List<CodeLens> codeLenses = new ArrayList<>();
            docState.forEachSectionState(sectionState ->
            {
                LegendLSPGrammarExtension extension = sectionState.getExtension();
                if (extension == null)
                {
                    LOGGER.warn("Could not get code lenses for section {} of {}: no extension for grammar {}", sectionState.getSectionNumber(), docState.getDocumentId(), sectionState.getSection().getGrammar());
                    return;
                }
                extension.getCommands(sectionState).forEach(c ->
                {
                    Command command = new Command(c.getTitle(), LegendLanguageServer.LEGEND_COMMAND_ID, List.of(uri, sectionState.getSectionNumber(), c.getEntity(), c.getId()));
                    codeLenses.add(new CodeLens(toRange(c.getLocation()), command, null));
                });
            });
            return codeLenses;
        }
    }

    private static Range toRange(TextInterval interval)
    {
        Position start = new Position(interval.getStart().getLine(), interval.getStart().getColumn());
        // Range end position is exclusive, whereas TextInterval end is inclusive
        Position end = new Position(interval.getEnd().getLine(), interval.getEnd().getColumn() + 1);
        return new Range(start, end);
    }
}
