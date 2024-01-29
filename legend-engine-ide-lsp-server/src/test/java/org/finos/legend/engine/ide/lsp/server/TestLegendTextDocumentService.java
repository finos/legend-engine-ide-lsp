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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestLegendTextDocumentService
{
    @Test
    public void testKeywordHighlighting() throws Exception
    {
        LegendLSPGrammarExtension extWithKeywords = newExtension("TestGrammar", Arrays.asList("Date", "Integer", "String", "Float", "StrictDate", "Boolean", "let", "true", "false"));
        LegendLSPGrammarExtension extNoKeywords = newExtension("EmptyGrammar", Collections.emptyList());
        LegendLanguageServer server = newServer(extWithKeywords, extNoKeywords);

        String uri = "file:///testKeywordHighlighting.pure";
        String code = "\n" +
                "\r\n" +
                "###TestGrammar\n" +
                "Class vscodelsp::test::Employee\r\n" +
                "{\n" +
                "    id       : Integer[1];\n" +
                "    hireDate : Date[1];\r\n" +
                "    hireType : String[1];\n" +
                "    employeeDetails : vscodelsp::test::EmployeeDetails[1];\n" +
                "}\n" +
                "\n";

        TextDocumentService service = server.getTextDocumentService();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "", 0, code)));
        SemanticTokens semanticTokens = service.semanticTokensRange(new SemanticTokensRangeParams(new TextDocumentIdentifier(uri), newRange(3, 0, 10, 0))).get();

        Assertions.assertEquals(Arrays.asList(5, 15, 7, 0, 0, 1, 15, 4, 0, 0, 1, 15, 6, 0, 0), semanticTokens.getData());
    }

    @Test
    public void testNoKeywordHighlighting() throws Exception
    {
        LegendLSPGrammarExtension extWithKeywords = newExtension("TestGrammar", Arrays.asList("Date", "Integer", "String", "Float", "StrictDate", "Boolean", "let", "true", "false"));
        LegendLSPGrammarExtension extNoKeywords = newExtension("EmptyGrammar", Collections.emptyList());
        LegendLanguageServer server = newServer(extWithKeywords, extNoKeywords);

        String uri = "file:///testNoKeywordHighlighting.pure";
        String code = "\r\n" +
                "###EmptyGrammar\n" +
                "Class vscodelsp::test::Employee\r\n" +
                "{\n" +
                "    id       : Integer[1];\n" +
                "    hireDate : Date[1];\r\n" +
                "    hireType : String[1];\n" +
                "    employeeDetails : vscodelsp::test::EmployeeDetails[1];\n" +
                "}\n" +
                "\n";

        TextDocumentService service = server.getTextDocumentService();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "", 0, code)));
        SemanticTokens semanticTokens = service.semanticTokensRange(new SemanticTokensRangeParams(new TextDocumentIdentifier(uri), newRange(2, 0, 9, 0))).get();

        Assertions.assertEquals(Collections.emptyList(), semanticTokens.getData());
    }

    @Test
    public void testCompletion() throws Exception
    {
        LegendLSPGrammarExtension ext = newExtension("TestGrammar", Collections.emptyList());
        LegendLanguageServer server = newServer(ext);

        String uri = "file:///testCompletion.pure";
        String code = "\n" +
                "###TestGrammar\n" +
                "Class vscodelsp::test::Employee\n" +
                "{\n" +
                "    completionTrigger;\n" +
                "    id       : Integer[1];\n" +
                "    hireDate : Date[1];\n" +
                "    hireType : String[1];\n" +
                "    employeeDetails : vscodelsp::test::EmployeeDetails[1];\n" +
                "}\n" +
                "\n";

        TextDocumentService service = server.getTextDocumentService();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "", 0, code)));

        List<CompletionItem> completions = service.completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(4, 21))).get().getLeft();
        List<String> suggestions = completions.stream().map(CompletionItem::getInsertText).collect(Collectors.toList());
        List<String> labels = completions.stream().map(CompletionItem::getLabel).collect(Collectors.toList());
        List<String> descriptions = completions.stream().map(r -> r.getLabelDetails().getDescription()).collect(Collectors.toList());

        Assertions.assertEquals(Arrays.asList("completionSuggestion1", "completionSuggestion2"), suggestions);
        Assertions.assertEquals(Arrays.asList("completionSuggestion1", "completionSuggestion2"), labels);
        Assertions.assertEquals(Arrays.asList("Test completion", "Test completion"), descriptions);
    }

    @Test
    public void testNoCompletion() throws Exception
    {
        LegendLSPGrammarExtension ext = newExtension("TestGrammar", Collections.emptyList());
        LegendLanguageServer server = newServer(ext);

        String uri = "file:///testCompletion.pure";
        String code = "\n" +
                "###TestGrammar\n" +
                "Class vscodelsp::test::Employee\n" +
                "{\n" +
                "    completionTrigger;\n" +
                "    id       : Integer[1];\n" +
                "    hireDate : Date[1];\n" +
                "    hireType : String[1];\n" +
                "    employeeDetails : vscodelsp::test::EmployeeDetails[1];\n" +
                "}\n" +
                "\n";

        TextDocumentService service = server.getTextDocumentService();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "", 0, code)));

        Integer completionsSize =
                service.completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(0, 1))).get().getLeft().size() +
                        service.completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(4, 2))).get().getLeft().size() +
                        service.completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(4, 3))).get().getLeft().size() +
                        service.completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(4, 22))).get().getLeft().size();

        Assertions.assertEquals(0, completionsSize);
    }

    @Test
    public void testBoilerplate() throws Exception
    {
        LegendLSPGrammarExtension ext = newExtension("TestGrammar", Collections.emptyList());
        LegendLanguageServer server = newServer(ext);

        String uri = "file:///testCompletion.pure";
        String code = "\n" +
                "###TestGrammar\n" +
                "Class vscodelsp::test::Employee\n" +
                "{\n" +
                "    completionTrigger;\n" +
                "    id       : Integer[1];\n" +
                "    hireDate : Date[1];\n" +
                "    hireType : String[1];\n" +
                "    employeeDetails : vscodelsp::test::EmployeeDetails[1];\n" +
                "}\n" +
                "\n";

        TextDocumentService service = server.getTextDocumentService();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "", 0, code)));

        List<CompletionItem> completions = service.completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(2, 0))).get().getLeft();
        List<String> suggestions = completions.stream().map(CompletionItem::getInsertText).collect(Collectors.toList());
        List<String> labels = completions.stream().map(CompletionItem::getLabel).collect(Collectors.toList());
        List<String> descriptions = completions.stream().map(r -> r.getLabelDetails().getDescription()).collect(Collectors.toList());

        Assertions.assertEquals(Arrays.asList("boilerplateSuggestion1", "boilerplateSuggestion2", "###TestGrammar"), suggestions);
        Assertions.assertEquals(Arrays.asList("boilerplateSuggestion1", "boilerplateSuggestion2", "###TestGrammar"), labels);
        Assertions.assertEquals(Arrays.asList("Test boilerplate", "Test boilerplate", "Section - TestGrammar"), descriptions);
    }


    @Test
    public void testNoBoilerplate() throws Exception
    {
        LegendLSPGrammarExtension ext = newExtension("TestGrammar", Collections.emptyList());
        LegendLanguageServer server = newServer(ext);

        String uri = "file:///testCompletion.pure";
        String code = "\n" +
                "###TestGrammar\n" +
                "Class vscodelsp::test::Employee\n" +
                "{\n" +
                "    completionTrigger;\n" +
                "    id       : Integer[1];\n" +
                "    hireDate : Date[1];\n" +
                "    hireType : String[1];\n" +
                "    employeeDetails : vscodelsp::test::EmployeeDetails[1];\n" +
                "}\n" +
                "\n";

        TextDocumentService service = server.getTextDocumentService();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "", 0, code)));

        Integer completionsSize =
                service.completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(2, 1))).get().getLeft().size() +
                        service.completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(2, 2))).get().getLeft().size() +
                        service.completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(2, 3))).get().getLeft().size() +
                        service.completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(3, 1))).get().getLeft().size() +
                        service.completion(new CompletionParams(new TextDocumentIdentifier(uri), new Position(4, 1))).get().getLeft().size();

        Assertions.assertEquals(0, completionsSize);
    }


    @Test
    void testDefaultCompletionsAdded() throws Exception
    {
        LegendLSPGrammarExtension extWithKeywords = newExtension("TestGrammar", Arrays.asList("Date", "Integer", "String", "Float", "StrictDate", "Boolean", "let", "true", "false"));
        LegendLSPGrammarExtension extNoKeywords = newExtension("EmptyGrammar", Collections.emptyList());
        LegendLanguageServer server = newServer(extWithKeywords, extNoKeywords);

        String uri = "file:///testNoKeywordHighlighting.pure";
        String code = "\r\n" +
                "###EmptyGrammar\n" +
                "Class vscodelsp::test::Employee\r\n" +
                "{\n" +
                "    id       : Integer[1];\n" +
                "    hireDate : Date[1];\r\n" +
                "    hireType : String[1];\n" +
                "    employeeDetails : vscodelsp::test::EmployeeDetails[1];\n" +
                "}\n" +
                "###T\n";

        TextDocumentService service = server.getTextDocumentService();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "", 0, code)));

        BiConsumer<Position, Set<String>> asserter = (position, expected) ->
        {
            CompletionParams completionParams = new CompletionParams(
                    new TextDocumentIdentifier(uri),
                    position
            );
            List<CompletionItem> items = service.completion(completionParams).join().getLeft();
            Assertions.assertEquals(2, items.size());
            Assertions.assertEquals(expected, items.stream().map(CompletionItem::getLabel).collect(Collectors.toSet()));
        };

        // start of line
        asserter.accept(new Position(9, 0), Set.of("###TestGrammar", "###EmptyGrammar"));
        // with # preceding
        asserter.accept(new Position(9, 1), Set.of("##TestGrammar", "##EmptyGrammar"));
        // with ## preceding
        asserter.accept(new Position(9, 2), Set.of("#TestGrammar", "#EmptyGrammar"));
        // with ### preceding
        asserter.accept(new Position(9, 3), Set.of("TestGrammar", "EmptyGrammar"));
        // with ###T preceding
        asserter.accept(new Position(9, 4), Set.of("TestGrammar", "EmptyGrammar"));
    }

    @Test
    void testReferenceComputed() throws ExecutionException, InterruptedException
    {
        String uri = "file:///testReference.pure";
        String code = "\n" +
                "###TestGrammar\n" +
                "Class vscodelsp::test::Employee\r\n" +
                "{\n" +
                "    id       : Integer[1];\n" +
                "    hireDate : Date[1];\r\n" +
                "    hireType : String[1];\n" +
                "    employeeDetails : vscodelsp::test::EmployeeDetails[1];\n" +
                "}\n" +
                "\n" +
                "Class vscodelsp::test::EmployeeDetails\r\n" +
                "{\n" +
                "    id       : Integer[1];\n" +
                "}\n" +
                "\n";

        LegendReference reference = LegendReference.builder()
                .withLocation(TextLocation.newTextSource(uri, 8, 23, 8, 55))
                .withReferencedLocation(TextLocation.newTextSource(uri, 11, 1, 14, 2))
                .build();

        LegendLSPGrammarExtension extWithKeywords = newExtension(
                "TestGrammar",
                Arrays.asList("Date", "Integer", "String", "Float", "StrictDate", "Boolean", "let", "true", "false"),
                List.of(reference));
        LegendLanguageServer server = newServer(extWithKeywords);

        TextDocumentService service = server.getTextDocumentService();
        service.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "", 0, code)));

        Either<List<? extends Location>, List<? extends LocationLink>> noDefinition = service.definition(new DefinitionParams(new TextDocumentIdentifier(uri), new Position(6, 2))).join();
        Assertions.assertTrue(noDefinition.isRight());
        Assertions.assertTrue(noDefinition.getRight().isEmpty());

        Either<List<? extends Location>, List<? extends LocationLink>> definition = service.definition(new DefinitionParams(new TextDocumentIdentifier(uri), new Position(8, 27))).join();
        Assertions.assertTrue(definition.isRight());
        Assertions.assertEquals(1, definition.getRight().size());
        Assertions.assertEquals(
                new LocationLink(uri,
                        newRange(11, 1, 14, 3),
                        newRange(11, 1, 14, 3),
                        newRange(8, 23, 8, 56)
                ), definition.getRight().get(0));
    }

    private static LegendLanguageServer newServer(LegendLSPGrammarExtension... extensions) throws ExecutionException, InterruptedException
    {
        LegendLanguageServer server = LegendLanguageServer.builder().synchronous().withGrammars(extensions).build();
        server.initialize(new InitializeParams()).get();
        return server;
    }

    private static LegendLSPGrammarExtension newExtension(String name, Iterable<String> keywords)
    {
        return newExtension(name, keywords, List.of());
    }

    private static LegendLSPGrammarExtension newExtension(String name, Iterable<String> keywords, List<LegendReference> references)
    {
        return new LegendLSPGrammarExtension()
        {

            private final List<String> COMPLETION_TRIGGERS = List.of("completionTrigger");

            private final List<String> COMPLETION_SUGGESTIONS = List.of("completionSuggestion1", "completionSuggestion2");

            private final List<String> BOILERPLATE_SUGGESTIONS = List.of("boilerplateSuggestion1", "boilerplateSuggestion2");

            @Override
            public String getName()
            {
                return name;
            }

            @Override
            public Iterable<? extends String> getKeywords()
            {
                return keywords;
            }

            public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
            {
                String codeLine = section.getSection().getLine(location.getLine()).substring(0, location.getColumn());
                List<LegendCompletion> legendCompletions = new ArrayList<>();

                if (codeLine.isEmpty())
                {
                    BOILERPLATE_SUGGESTIONS.stream().map(s -> new LegendCompletion("Test boilerplate", s)).forEach(legendCompletions::add);
                }

                if (COMPLETION_TRIGGERS.stream().anyMatch(codeLine::endsWith))
                {
                    COMPLETION_SUGGESTIONS.stream().map(s -> new LegendCompletion("Test completion", s)).forEach(legendCompletions::add);
                }

                return legendCompletions;
            }

            @Override
            public Optional<LegendReference> getLegendReference(SectionState sectionState, TextPosition textPosition)
            {
                return references.stream().filter(x -> x.getLocation().getTextInterval().includes(textPosition)).findAny();
            }
        };
    }

    private static Range newRange(int startLine, int startChar, int endLine, int endChar)
    {
        return new Range(newPosition(startLine, startChar), newPosition(endLine, endChar));
    }

    private static Position newPosition(int line, int character)
    {
        return new Position(line, character);
    }
}
