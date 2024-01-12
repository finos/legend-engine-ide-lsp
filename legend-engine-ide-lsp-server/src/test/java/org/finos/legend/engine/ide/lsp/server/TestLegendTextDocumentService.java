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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
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

    private static void assertExpectedCompletionItems(String uri, TextDocumentService service, Position position, Set<String> expected) throws InterruptedException, ExecutionException
    {

    }

    private static LegendLanguageServer newServer(LegendLSPGrammarExtension... extensions) throws ExecutionException, InterruptedException
    {
        LegendLanguageServer server = LegendLanguageServer.builder().synchronous().withGrammars(extensions).build();
        server.initialize(new InitializeParams()).get();
        return server;
    }

    private static LegendLSPGrammarExtension newExtension(String name, Iterable<String> keywords)
    {
        return new LegendLSPGrammarExtension()
        {
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
