// Copyright 2024 Goldman Sachs
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

package org.finos.legend.engine.ide.lsp;

import com.google.gson.JsonNull;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;

public class DummyLanguageClient implements LanguageClient
{
    public final LinkedBlockingQueue<String> clientLog = new LinkedBlockingQueue();

    @Override
    public void telemetryEvent(Object object)
    {
        clientLog.add("telemetryEvent");
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics)
    {
        clientLog.add("publishDiagnostics");
    }

    @Override
    public void showMessage(MessageParams messageParams)
    {
        clientLog.add("showMessage");
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void logMessage(MessageParams message)
    {
        clientLog.add(String.format("logMessage - %s - %s", message.getType().name(), message.getMessage()));
    }

    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams)
    {
        clientLog.add(String.format("configuration - %s", configurationParams.getItems().stream().map(ConfigurationItem::getSection).collect(Collectors.joining())));
        return CompletableFuture.completedFuture(configurationParams.getItems().stream().map(x -> JsonNull.INSTANCE).collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<Void> refreshSemanticTokens()
    {
        clientLog.add("refreshSemanticTokens");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> refreshCodeLenses()
    {
        clientLog.add("refreshCodeLenses");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> refreshInlayHints()
    {
        clientLog.add("refreshInlayHints");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> refreshInlineValues()
    {
        clientLog.add("refreshInlineValues");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> refreshDiagnostics()
    {
        clientLog.add("refreshDiagnostics");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void notifyProgress(ProgressParams params)
    {
        clientLog.add("notifyProgress");
    }
}
