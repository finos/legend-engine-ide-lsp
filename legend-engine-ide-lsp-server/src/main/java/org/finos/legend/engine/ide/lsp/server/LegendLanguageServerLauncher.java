package org.finos.legend.engine.ide.lsp.server;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

public class LegendLanguageServerLauncher
{
    public static void main(String[] args)
    {
        LegendLanguageServer.Builder builder = new LegendLanguageServer.Builder();
        LegendLanguageServer server = builder.build();
        server.initialize(null);
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, System.in, System.out);
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
    }
}