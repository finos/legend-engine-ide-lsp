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

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPInlineDSLExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class TestLegendLanguageServer
{

    @Test
    public void testServerInitShutdown() throws Exception
    {
        // Initial state
        LegendLanguageServer server = LegendLanguageServer.builder().synchronous().build();
        Assertions.assertTrue(server.isUninitialized());
        Assertions.assertFalse(server.isInitialized());
        Assertions.assertFalse(server.isShutDown());

        assertThrowsResponseError(ResponseErrorCode.ServerNotInitialized, "Server is not initialized", () -> server.initialized(new InitializedParams()));
        Assertions.assertInstanceOf(LegendWorkspaceService.class, server.getWorkspaceService());
        assertThrowsResponseError(ResponseErrorCode.ServerNotInitialized, "Server is not initialized", () -> server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams()));
        Assertions.assertInstanceOf(LegendTextDocumentService.class, server.getTextDocumentService());
        assertThrowsResponseError(ResponseErrorCode.ServerNotInitialized, "Server is not initialized", () -> server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams()));

        // Initialize
        InitializeResult initializeResult = server.initialize(new InitializeParams()).get();
        Assertions.assertFalse(server.isUninitialized());
        Assertions.assertTrue(server.isInitialized());
        Assertions.assertFalse(server.isShutDown());
        Assertions.assertNotNull(initializeResult);

        assertThrowsResponseError(ResponseErrorCode.RequestFailed, "Server is already initialized", () -> server.initialize(new InitializeParams()));
        server.initialized(new InitializedParams());
        Assertions.assertInstanceOf(LegendWorkspaceService.class, server.getWorkspaceService());
        Assertions.assertInstanceOf(LegendTextDocumentService.class, server.getTextDocumentService());
        Assertions.assertNull(server.getLanguageClient());

        // Shut down
        Object shutDownResult = server.shutdown().get();
        Assertions.assertFalse(server.isUninitialized());
        Assertions.assertFalse(server.isInitialized());
        Assertions.assertTrue(server.isShutDown());
        Assertions.assertNull(shutDownResult);

        assertThrowsResponseError(ResponseErrorCode.RequestFailed, "Server has shut down", () -> server.initialize(new InitializeParams()));
        assertThrowsResponseError(ResponseErrorCode.RequestFailed, "Server has shut down", () -> server.initialized(new InitializedParams()));
        assertThrowsResponseError(ResponseErrorCode.RequestFailed, "Server has shut down", server::getWorkspaceService);
        assertThrowsResponseError(ResponseErrorCode.RequestFailed, "Server has shut down", server::getTextDocumentService);
        assertThrowsResponseError(ResponseErrorCode.RequestFailed, "Server has shut down", server::getLanguageClient);

        Assertions.assertNull(server.shutdown().get());
    }

    @Test
    public void testConnectLanguageClient()
    {
        LanguageClient client = newLanguageClient();
        LegendLanguageServer server = LegendLanguageServer.builder().synchronous().build();
        server.initialize(new InitializeParams());
        Assertions.assertNull(server.getLanguageClient());
        server.connect(client);
        Assertions.assertSame(client, server.getLanguageClient());
        server.connect(client);
        Assertions.assertSame(client, server.getLanguageClient());
        assertThrowsResponseError(ResponseErrorCode.RequestFailed, "Already connected to a language client", () -> server.connect(newLanguageClient()));
    }

    @Test
    public void testGrammars()
    {
        LegendLSPGrammarExtension ext1 = () -> "ext1";
        LegendLSPGrammarExtension ext2 = () -> "ext2";
        LegendLSPGrammarExtension ext3 = () -> "ext3";

        LegendLanguageServer noGrammars = LegendLanguageServer.builder().synchronous().build();
        noGrammars.initialize(new InitializeParams());
        Assertions.assertEquals(Set.of(), noGrammars.getGrammarLibrary().getGrammars());
        Assertions.assertEquals(Set.of(), Set.copyOf(noGrammars.getGrammarLibrary().getExtensions()));

        LegendLanguageServer oneGrammar = LegendLanguageServer.builder().synchronous().withGrammar(ext1).build();
        oneGrammar.initialize(new InitializeParams());
        Assertions.assertEquals(Set.of("ext1"), oneGrammar.getGrammarLibrary().getGrammars());
        Assertions.assertEquals(Set.of(ext1), Set.copyOf(oneGrammar.getGrammarLibrary().getExtensions()));

        LegendLanguageServer threeGrammars = LegendLanguageServer.builder().synchronous().withGrammars(ext1, ext2, ext3).build();
        threeGrammars.initialize(new InitializeParams());
        Assertions.assertEquals(Set.of("ext1", "ext2", "ext3"), threeGrammars.getGrammarLibrary().getGrammars());
        Assertions.assertEquals(Set.of(ext1, ext2, ext3), Set.copyOf(threeGrammars.getGrammarLibrary().getExtensions()));

        IllegalArgumentException e = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> LegendLanguageServer.builder().withGrammars(ext1, ext2, () -> "ext1", ext3));
        Assertions.assertEquals("Multiple extensions named: \"ext1\"", e.getMessage());
    }

    @Test
    public void testKeywordHighlighting() throws Exception
    {
        LegendLSPGrammarExtension pureExtension = newExtension("Pure", Set.of("Date", "Integer", "String", "Float", "StrictDate", "Boolean", "let", "true", "false"));
        LegendLanguageServer server = LegendLanguageServer.builder().synchronous().withGrammar(pureExtension).build();

        String uri = "file:///testKeywordHighlighting.pure";
        String code = "###Pure\n" +
                "Class vscodelsp::test::Employee\n" +
                "{\n" +
                "    id       : Integer[1];\n" +
                "    hireDate : Date[1];\n" +
                "    hireType : String[1];\n" +
                "    Float : Float[1];\n" +
                "    String : String[1];\n" +
                "    firmName : String[0..1];\n" +
                "    employeeDetails : employeeDetails[1];\n" +
                "}";

        server.initialize(new InitializeParams()).get();
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri,"",0,code)));
        CompletableFuture<SemanticTokens> semanticTokens = server.getTextDocumentService().semanticTokensRange(new SemanticTokensRangeParams(new TextDocumentIdentifier(uri), new Range(new Position(0,0),new Position(6,0))));

        List<Integer> expectedCoordinates = Arrays.asList(3, 15, 7, 0, 0, 1, 15, 4, 0, 0, 1, 15, 6, 0, 0, 1, 4, 5, 0, 0, 0, 8, 5, 0, 0, 1, 4, 6, 0, 0, 0, 9, 6, 0, 0, 1, 15, 6, 0, 0);

        try
        {
            Assertions.assertEquals(expectedCoordinates, semanticTokens.get().getData());
        }
        catch (InterruptedException e)
        {
            Assertions.fail();
        }
        catch (ExecutionException e)
        {
            Assertions.fail();
        }
    }

    @Test
    public void testInlineDSLs()
    {
        LegendLSPInlineDSLExtension ext1 = () -> "ext1";
        LegendLSPInlineDSLExtension ext2 = () -> "ext2";
        LegendLSPInlineDSLExtension ext3 = () -> "ext3";

        LegendLanguageServer noDSLs = LegendLanguageServer.builder().synchronous().build();
        noDSLs.initialize(new InitializeParams());
        Assertions.assertEquals(Set.of(), noDSLs.getInlineDSLLibrary().getInlineDSLs());
        Assertions.assertEquals(Set.of(), Set.copyOf(noDSLs.getInlineDSLLibrary().getExtensions()));

        LegendLanguageServer oneDSL = LegendLanguageServer.builder().synchronous().withInlineDSL(ext1).build();
        oneDSL.initialize(new InitializeParams());
        Assertions.assertEquals(Set.of("ext1"), oneDSL.getInlineDSLLibrary().getInlineDSLs());
        Assertions.assertEquals(Set.of(ext1), Set.copyOf(oneDSL.getInlineDSLLibrary().getExtensions()));

        LegendLanguageServer threeDSLs = LegendLanguageServer.builder().synchronous().withInlineDSLs(ext1, ext2, ext3).build();
        threeDSLs.initialize(new InitializeParams());
        Assertions.assertEquals(Set.of("ext1", "ext2", "ext3"), threeDSLs.getInlineDSLLibrary().getInlineDSLs());
        Assertions.assertEquals(Set.of(ext1, ext2, ext3), Set.copyOf(threeDSLs.getInlineDSLLibrary().getExtensions()));

        IllegalArgumentException e = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> LegendLanguageServer.builder().withInlineDSLs(ext1, ext2, () -> "ext1", ext3));
        Assertions.assertEquals("Multiple extensions named: \"ext1\"", e.getMessage());
    }

    @Test
    public void testKeywordHighlighting() throws Exception
    {
        LegendLSPGrammarExtension baseExtension = () -> "baseExtension";
        LegendLanguageServer server = LegendLanguageServer.builder().synchronous().withGrammar(baseExtension).build();

        String uri = "file:///testKeywordHighlighting.pure";
        String code = "###Pure\n" +
                "Class vscodelsp::test::Employee\n" +
                "{\n" +
                "    id       : Integer[1];\n" +
                "    hireDate : Date[1];\n" +
                "    hireType : String[1];\n" +
                "    Float : Float[1];\n" +
                "    String : String[1];\n" +
                "    firmName : String[0..1];\n" +
                "    employeeDetails : employeeDetails[1];\n" +
                "}";

        server.initialize(new InitializeParams()).get();
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri,"",0,code)));
        CompletableFuture<SemanticTokens> semanticTokens = server.getTextDocumentService().semanticTokensRange(new SemanticTokensRangeParams(new TextDocumentIdentifier(uri), new Range(new Position(0,0),new Position(6,0))));

        List<Integer> expectedCoordinates = Arrays.asList(3, 15, 7, 0, 0, 1, 15, 4, 0, 0, 1, 15, 6, 0, 0, 1, 4, 5, 0, 0, 0, 8, 5, 0, 0, 1, 4, 6, 0, 0, 0, 9, 6, 0, 0, 1, 15, 6, 0, 0);

        Assertions.assertEquals(expectedCoordinates, semanticTokens.get().getData());
    }

    private void assertThrowsResponseError(ResponseErrorCode code, String message, Executable executable)
    {
        ResponseErrorException e = Assertions.assertThrows(ResponseErrorException.class, executable);
        ResponseError expected = new ResponseError(code, message, null);
        Assertions.assertEquals(expected, e.getResponseError());
        Assertions.assertEquals(message, e.getMessage());
    }

    private LanguageClient newLanguageClient()
    {
        return new LanguageClient()
        {
            @Override
            public void telemetryEvent(Object object)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public void publishDiagnostics(PublishDiagnosticsParams diagnostics)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public void showMessage(MessageParams messageParams)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public void logMessage(MessageParams message)
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    private LegendLSPGrammarExtension newExtension( String name, Iterable <String> keywords)
    {
        return new LegendLSPGrammarExtension() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public Iterable<? extends String> getKeywords() {
                return keywords;
            }
        };
    }
}
