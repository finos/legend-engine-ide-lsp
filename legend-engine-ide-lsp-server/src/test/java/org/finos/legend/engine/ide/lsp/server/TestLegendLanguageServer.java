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

import java.util.Set;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageClient;
import org.finos.legend.engine.ide.lsp.DummyLanguageClient;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class TestLegendLanguageServer
{
    @Test
    public void testServerInitShutdown() throws Exception
    {
        // Initial state
        LegendLanguageServer server = LegendLanguageServer.builder().synchronous().build();
        server.connect(newLanguageClient());
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
        Assertions.assertNotNull(server.getLanguageClient());

        // Shut down
        Object shutDownResult = server.shutdown().get();
        Assertions.assertFalse(server.isUninitialized());
        Assertions.assertFalse(server.isInitialized());
        Assertions.assertTrue(server.isShutDown());
        Assertions.assertNull(shutDownResult);

        assertThrowsResponseError(ResponseErrorCode.RequestFailed, "Server has shut down", () -> server.initialize(new InitializeParams()));
        assertThrowsResponseError(ResponseErrorCode.RequestFailed, "Server has shut down", () -> server.initialized(new InitializedParams()));
        Assertions.assertInstanceOf(LegendWorkspaceService.class, server.getWorkspaceService());
        assertThrowsResponseError(ResponseErrorCode.RequestFailed, "Server has shut down", () -> server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams()));
        Assertions.assertInstanceOf(LegendTextDocumentService.class, server.getTextDocumentService());
        assertThrowsResponseError(ResponseErrorCode.RequestFailed, "Server has shut down", () -> server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams()));
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
    void testInitializedReloadExtensions() throws Exception
    {
        LegendLanguageServer server = LegendLanguageServer.builder().synchronous().build();
        DummyLanguageClient languageClient = newLanguageClient();
        server.connect(languageClient);
        server.initialize(new InitializeParams()).get();
        Assertions.assertTrue(server.getGrammarLibrary().getGrammars().isEmpty());
        server.initialized(new InitializedParams());

        Assertions.assertTrue(languageClient.clientLog.contains("logMessage - Info - Using app classpath"));
        Assertions.assertTrue(languageClient.clientLog.contains("refreshCodeLenses"));
        Assertions.assertTrue(languageClient.clientLog.contains("refreshDiagnostics"));
        Assertions.assertTrue(languageClient.clientLog.contains("refreshInlayHints"));
        Assertions.assertTrue(languageClient.clientLog.contains("refreshSemanticTokens"));
    }

    private void assertThrowsResponseError(ResponseErrorCode code, String message, Executable executable)
    {
        ResponseErrorException e = Assertions.assertThrows(ResponseErrorException.class, executable);
        ResponseError expected = new ResponseError(code, message, null);
        Assertions.assertEquals(expected, e.getResponseError());
        Assertions.assertEquals(message, e.getMessage());
    }


    private DummyLanguageClient newLanguageClient()
    {
        return new DummyLanguageClient();
    }
}
