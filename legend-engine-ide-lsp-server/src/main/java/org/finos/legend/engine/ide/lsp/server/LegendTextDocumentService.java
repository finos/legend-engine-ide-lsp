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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.finos.legend.engine.ide.lsp.commands.LegendCommandExecutionHandler;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendClientCommand;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.ide.lsp.server.LegendServerGlobalState.LegendServerDocumentState;
import org.finos.legend.engine.ide.lsp.text.LineIndexedText;
import org.finos.legend.engine.ide.lsp.text.TextTools;
import org.finos.legend.engine.ide.lsp.utils.LegendToLSPUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegendTextDocumentService implements TextDocumentService
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
        TextDocumentItem doc = params.getTextDocument();
        String uri = doc.getUri();
        LOGGER.debug("Opening {} (language id: {}, version: {})", uri, doc.getLanguageId(), doc.getVersion());
        LegendServerDocumentState docState = globalState.getOrCreateDocState(uri);

        this.server.runPossiblyAsync(() ->
        {
            if (docState.open(doc.getVersion(), doc.getText()))
            {
                globalState.clearProperties();
            }
            globalState.logInfo(String.format("Opened %s (language id: %s, version: %d)", uri, doc.getLanguageId(), doc.getVersion()));
        });
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
        VersionedTextDocumentIdentifier doc = params.getTextDocument();
        String uri = doc.getUri();
        LOGGER.debug("Changing {} (version {})", uri, doc.getVersion());

        LegendServerDocumentState docState = globalState.getDocumentState(uri);
        if (docState == null)
        {
            LOGGER.warn("Change to {} (version {}) before it was opened", uri, doc.getVersion());
            docState = globalState.getOrCreateDocState(uri);
        }

        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();

        if (applyChanges(docState, doc.getVersion(), changes))
        {
            this.server.runPossiblyAsync(() ->
            {
                globalState.clearProperties();
                this.server.getLanguageClient().refreshDiagnostics();
                this.server.getLanguageClient().refreshSemanticTokens();
                this.server.getLanguageClient().refreshCodeLenses();
                LOGGER.debug("Changed {} (version {})", uri, doc.getVersion());
            });
        }
    }

    public static boolean applyChanges(LegendServerDocumentState finalDocState, Integer version, List<TextDocumentContentChangeEvent> changes)
    {
        LineIndexedText indexedText = finalDocState.getIndexedText();
        for (TextDocumentContentChangeEvent changeEvent : changes)
        {
            if (changeEvent.getRange() == null)
            {
                indexedText = LineIndexedText.index(changeEvent.getText());
            }
            else
            {
                indexedText = indexedText.replace(
                        changeEvent.getText(),
                        changeEvent.getRange().getStart().getLine(),
                        changeEvent.getRange().getStart().getCharacter(),
                        changeEvent.getRange().getEnd().getLine(),
                        changeEvent.getRange().getEnd().getCharacter()
                );
            }
        }
        return finalDocState.change(version, indexedText);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
        String uri = params.getTextDocument().getUri();
        LegendServerDocumentState docState = globalState.getDocumentState(uri);
        if (docState == null)
        {
            LOGGER.warn("Closed notification for a document that is not open: {}", uri);
        }
        else
        {
            docState.close();
            LOGGER.debug("Closed {}", uri);
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
        String uri = params.getTextDocument().getUri();
        LOGGER.debug("Saving {}", uri);
        LegendServerDocumentState docState = globalState.getDocumentState(uri);

        if (docState == null)
        {
            LOGGER.warn("Saved notification for a document that is not open: {}", uri);
            return;
        }

        this.server.runPossiblyAsync(() ->
        {
            if (docState.save(params.getText()))
            {
                globalState.clearProperties();
            }
            LOGGER.debug("Saved {}", uri);
        });
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params)
    {
        return this.server.supplyPossiblyAsync(() -> getDocumentSymbols(params));
    }

    private List<Either<SymbolInformation, DocumentSymbol>> getDocumentSymbols(DocumentSymbolParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
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
            extension.getDeclarations(sectionState).forEach(declaration -> results.add(Either.forRight(this.toDocumentSymbol(declaration, null))));
        });
        return results;
    }

    private DocumentSymbol toDocumentSymbol(LegendDeclaration declaration, DocumentSymbol parent)
    {
        Range range = LegendToLSPUtilities.toRange(declaration.getLocation().getTextInterval());
        Range selectionRange = declaration.hasCoreLocation() ? LegendToLSPUtilities.toRange(declaration.getCoreLocation().getTextInterval()) : range;
        String identifier = declaration.getIdentifier();
        SymbolKind symbolKind = LegendToLSPUtilities.getSymbolKind(declaration);
        if (parent != null)
        {
            identifier = parent.getName() + "." + identifier;
            if (parent.getKind().equals(SymbolKind.Enum))
            {
                symbolKind = SymbolKind.EnumMember;
            }
        }
        DocumentSymbol symbol = new DocumentSymbol(identifier, symbolKind, range, selectionRange);
        symbol.setDetail(declaration.getClassifier());
        symbol.setChildren(declaration.getChildren().stream().map(c -> this.toDocumentSymbol(c, symbol)).collect(Collectors.toList()));
        return symbol;
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params)
    {
        return this.server.supplyPossiblyAsync(() -> getSemanticTokensRange(params));
    }

    private SemanticTokens getSemanticTokensRange(SemanticTokensRangeParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
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
        AtomicInteger previousTokenLine = new AtomicInteger(0);
        docState.forEachSectionState(sectionState ->
        {
            GrammarSection section = sectionState.getSection();
            if (isRangeEndLineInclusive ? (section.getStartLine() > rangeEndLine) : (section.getStartLine() >= rangeEndLine))
            {
                // past the end of the range
                return;
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
                        int startLine = Math.max(section.hasGrammarDeclaration() ? (section.getStartLine() + 1) : section.getStartLine(), rangeStartLine);
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
                                int deltaLine = n - previousTokenLine.get();
                                int deltaStart = tokenStart - previousTokenStart;
                                int length = matcher.end() - tokenStart;
                                data.add(deltaLine);
                                data.add(deltaStart);
                                data.add(length);
                                data.add(0);
                                data.add(0);
                                previousTokenLine.set(n);
                                previousTokenStart = tokenStart;
                            }
                        }
                    }
                }
            }
        });

        return new SemanticTokens(data);
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

    private List<Diagnostic> getDiagnostics(DocumentDiagnosticParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
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

    private List<Diagnostic> getDiagnostics(DocumentState docState)
    {
        List<Diagnostic> diagnostics = new ArrayList<>();
        this.getLegendDiagnostics(docState).forEach(d ->
        {
            if (d.getLocation().getDocumentId().equals(docState.getDocumentId()))
            {
                diagnostics.add(LegendToLSPUtilities.toDiagnostic(d));
            }
        });
        return diagnostics;
    }

    protected Set<LegendDiagnostic> getLegendDiagnostics(DocumentState docState)
    {
        Set<LegendDiagnostic> diagnostics = new HashSet<>();
        docState.forEachSectionState(sectionState ->
        {
            LegendLSPGrammarExtension extension = sectionState.getExtension();
            if (extension == null)
            {
                GrammarSection section = sectionState.getSection();
                if (section.hasGrammarDeclaration())
                {
                    int lineNum = section.getStartLine();
                    String line = section.getLine(lineNum);
                    int start = line.indexOf("###") + 3;
                    int end = TextTools.indexOfWhitespace(line, start, line.length());
                    StringBuilder message = new StringBuilder("Unknown grammar: ").append(section.getGrammar());
                    Set<String> grammars = this.server.getGrammarLibrary().getGrammars();
                    if (!grammars.isEmpty())
                    {
                        message.append(grammars.stream().sorted().collect(Collectors.joining(", ", "\nAvailable grammars: ", "")));
                    }
                    diagnostics.add(
                            LegendDiagnostic.newDiagnostic(
                                    TextLocation.newTextSource(docState.getDocumentId(), lineNum, start, lineNum, ((end == -1) ? line.length() : end) - 1),
                                    message.toString(),
                                    LegendDiagnostic.Kind.Error,
                                    LegendDiagnostic.Source.Parser
                            )
                    );
                }
                LOGGER.warn("Could not get diagnostics for section of {}: no extension for grammar {}", docState.getDocumentId(), section.getGrammar());
                return;
            }
            extension.getDiagnostics(sectionState).forEach(diagnostics::add);
        });
        return diagnostics;
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams completionParams)
    {
        return this.server.supplyPossiblyAsync(() -> Either.forLeft(getCompletions(completionParams)));
    }

    private List<CompletionItem> getCompletions(CompletionParams completionParams)
    {
        String uri = completionParams.getTextDocument().getUri();
        int line = completionParams.getPosition().getLine();
        int character = completionParams.getPosition().getCharacter();
        TextPosition location = TextPosition.newPosition(line, character);
        LegendServerGlobalState globalState = this.server.getGlobalState();
        DocumentState docState = globalState.getDocumentState(uri);
        if (docState == null)
        {
            LOGGER.warn("No state for {}: cannot get completions", uri);
            return Collections.emptyList();
        }
        SectionState sectionState = docState.getSectionStateAtLine(line);
        if (sectionState == null)
        {
            LOGGER.warn("Cannot find section state for line {} of {}: cannot get completions", line, uri);
            return Collections.emptyList();
        }

        List<LegendCompletion> completionItems = List.of();

        String upToSuggestLocation = sectionState.getSection().getLineUpTo(location);
        if (upToSuggestLocation.isEmpty() || upToSuggestLocation.startsWith("#"))
        {
            completionItems = this.server.getGrammarLibrary()
                    .getGrammars()
                    .stream()
                    .map(x -> new LegendCompletion("Section - " + x, "###" + x))
                    .collect(Collectors.toList());
        }

        if (sectionState.getExtension() != null)
        {
            Iterable<? extends LegendCompletion> legendCompletions = sectionState.getExtension().getCompletions(sectionState, location);

            completionItems = Stream.concat(
                    StreamSupport.stream(legendCompletions.spliterator(), false),
                    completionItems.stream()
            ).collect(Collectors.toList());
        }


        return getCompletionItems(completionItems, line, character, upToSuggestLocation);
    }

    private List<CompletionItem> getCompletionItems(Iterable<? extends LegendCompletion> legendCompletions, int line, int endColumn, String upToSuggestLocation)
    {
        List<CompletionItem> completions = new ArrayList<>();
        int startColumn = -1;

        for (LegendCompletion legendCompletion : legendCompletions)
        {
            String suggestion = legendCompletion.getSuggestion();
            if (startColumn == -1)
            {
                int upToSuggestLocationLength = upToSuggestLocation.length();
                int suggestionLength = suggestion.length();
                if (upToSuggestLocationLength > suggestionLength)
                {
                    int commonSubstringStartColumn = findCommonSubstringStartColumn(upToSuggestLocation.substring(upToSuggestLocationLength - suggestionLength, upToSuggestLocationLength), suggestion);
                    startColumn = upToSuggestLocationLength - suggestionLength + commonSubstringStartColumn;
                }
                else
                {
                    startColumn = findCommonSubstringStartColumn(upToSuggestLocation, suggestion.substring(0, upToSuggestLocationLength));
                }
            }
            CompletionItem completionItem = new CompletionItem(suggestion);
            completionItem.setInsertText(suggestion);
            completionItem.setTextEdit(Either.forLeft(new TextEdit(LegendToLSPUtilities.newRange(line, startColumn, line, endColumn), suggestion)));
            CompletionItemLabelDetails detail = new CompletionItemLabelDetails();
            detail.setDescription(legendCompletion.getDescription());
            completionItem.setLabelDetails(detail);
            completions.add(completionItem);
        }

        return completions;
    }

    private int findCommonSubstringStartColumn(String upToSuggestLocation, String suggestion)
    {
        int suggestionLength = suggestion.length();
        int startColumn = 0;
        while (startColumn < suggestionLength)
        {
            int upToSuggestLocationIndex = startColumn;
            int suggestionIndex = 0;
            while (upToSuggestLocationIndex < suggestionLength && Character.toLowerCase(upToSuggestLocation.charAt(upToSuggestLocationIndex)) == Character.toLowerCase(suggestion.charAt(suggestionIndex)))
            {
                upToSuggestLocationIndex++;
                suggestionIndex++;
                if (upToSuggestLocationIndex == suggestionLength)
                {
                    return startColumn;
                }
            }
            startColumn++;
        }

        return startColumn;
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params)
    {
        return this.server.supplyPossiblyAsync(() -> getCodeLenses(params));
    }

    private List<? extends CodeLens> getCodeLenses(CodeLensParams params)
    {
        LegendServerGlobalState globalState = this.server.getGlobalState();
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
                Command command = new Command(c.getTitle(), c instanceof LegendClientCommand ? LegendLanguageServer.LEGEND_CLIENT_COMMAND_ID : LegendCommandExecutionHandler.LEGEND_COMMAND_ID, List.of(uri, sectionState.getSectionNumber(), c.getEntity(), c.getId(), c.getExecutableArgs(), c.getInputParameters()));
                codeLenses.add(new CodeLens(LegendToLSPUtilities.toRange(c.getLocation().getTextInterval()), command, null));
            });
        });
        return codeLenses;
    }

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params)
    {
        return this.server.supplyPossiblyAsync(() ->
        {
            String uri = params.getTextDocument().getUri();
            LegendServerDocumentState documentState = this.server.getGlobalState().getDocumentState(uri);
            if (documentState == null)
            {
                LOGGER.warn("No state for {}: cannot go to definition", uri);
                return Either.forRight(List.of());
            }

            TextPosition textPosition = TextPosition.newPosition(params.getPosition().getLine(), params.getPosition().getCharacter());

            SectionState sectionState = documentState.getSectionStateAtLine(textPosition.getLine());

            if (sectionState == null)
            {
                LOGGER.warn("Cannot find section state for line {} of {}: cannot go to definition", textPosition.getLine(), uri);
                return Either.forRight(List.of());
            }

            LegendLSPGrammarExtension extension = sectionState.getExtension();

            if (extension == null)
            {
                LOGGER.warn("No extension available for section state on line {} of {}: cannot go to definition", textPosition.getLine(), uri);
                return Either.forRight(List.of());
            }

            Optional<LegendReference> reference = extension.getLegendReference(sectionState, textPosition);

            return Either.forRight(reference.map(x ->
                            {
                                TextLocation referencedLocation = x.getDeclarationLocation();
                                Range range = LegendToLSPUtilities.toRange(referencedLocation.getTextInterval());
                                return List.of(new LocationLink(
                                        referencedLocation.getDocumentId(),
                                        range,
                                        range,
                                        x.hasCoreLocation() ? LegendToLSPUtilities.toRange(x.getCoreLocation().getTextInterval()) : LegendToLSPUtilities.toRange(x.getLocation().getTextInterval())
                                ));
                            }
                    ).orElse(List.of())
            );
        });
    }

    /**
     * Compute the references of a given declaration.
     * <p>
     * The implementation first looks through the declaration at the given position,
     * and then computes references that link to that declaration.
     *
     * @param params request params, including the location of the declaration to look for references to
     * @return the list of references to the declaration at the given position
     */
    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params)
    {
        return this.server.supplyPossiblyAsync(() ->
        {
            String uri = params.getTextDocument().getUri();
            TextPosition textPosition = TextPosition.newPosition(params.getPosition().getLine(), params.getPosition().getCharacter());

            LegendServerDocumentState currDocumentState = this.server.getGlobalState().getDocumentState(uri);
            if (currDocumentState == null)
            {
                LOGGER.warn("No state for {}: cannot go to definition", uri);
                return List.of();
            }

            SectionState currSectionState = currDocumentState.getSectionStateAtLine(textPosition.getLine());

            if (currSectionState == null)
            {
                LOGGER.warn("Cannot find section state for line {} of {}: cannot go to definition", textPosition.getLine(), uri);
                return List.of();
            }

            LegendLSPGrammarExtension currExtension = currSectionState.getExtension();

            if (currExtension == null)
            {
                LOGGER.warn("No extension available for section state on line {} of {}: cannot go to definition", textPosition.getLine(), uri);
                return List.of();
            }

            TextLocation location = TextLocation.newTextSource(uri, TextInterval.newInterval(textPosition, textPosition));

            // try to find the declaration that we want to find references/usage
            Optional<? extends LegendDeclaration> maybeDeclarationToFindReferences = currExtension.getDeclaration(currSectionState, textPosition);

            if (maybeDeclarationToFindReferences.isEmpty())
            {
                return List.of();
            }

            LegendDeclaration declarationToFindReferences = maybeDeclarationToFindReferences.get();

            // narrow down through the children, in case the request is for one of these
            boolean cont = declarationToFindReferences.hasChildren();
            while (cont)
            {
                // flag to stop looping
                cont = false;
                for (LegendDeclaration child : declarationToFindReferences.getChildren())
                {
                    // check if child is a better candidate for the interested declaration to look for references
                    if (child.getLocation().subsumes(location))
                    {
                        // update declaration for reference lookups
                        declarationToFindReferences = child;
                        // narrow down even further if the new declaration has children
                        cont = declarationToFindReferences.hasChildren();
                        break;
                    }
                }
            }

            TextLocation locationToLookForReferences = declarationToFindReferences.getLocation();
            List<Location> references = new ArrayList<>();

            this.server.getGlobalState()
                    .forEachDocumentState(documentState ->
                            documentState.forEachSectionState(sectionState ->
                                    {
                                        LegendLSPGrammarExtension extension = sectionState.getExtension();
                                        if (extension != null)
                                        {
                                            extension.getLegendReferences(sectionState)
                                                    // we look into the referenced location to match the declaration we care
                                                    .filter(ref -> locationToLookForReferences.equals(ref.getDeclarationLocation()))
                                                    .map(ref -> new Location(ref.getLocation().getDocumentId(), LegendToLSPUtilities.toRange(ref.getLocation().getTextInterval())))
                                                    .forEach(references::add);
                                        }
                                    }
                            )
                    );


            if (params.getContext().isIncludeDeclaration())
            {
                references.add(new Location(locationToLookForReferences.getDocumentId(), LegendToLSPUtilities.toRange(locationToLookForReferences.getTextInterval())));
            }

            return references;
        });
    }
}
