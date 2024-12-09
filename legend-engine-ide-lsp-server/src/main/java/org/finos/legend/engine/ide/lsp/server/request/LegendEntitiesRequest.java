/*
 * Copyright 2024 Goldman Sachs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.legend.engine.ide.lsp.server.request;

import java.util.List;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.util.Preconditions;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

public class LegendEntitiesRequest
{
    @NonNull
    private List<TextDocumentIdentifier> textDocuments;

    @NonNull
    private List<String> entityPaths;

    public LegendEntitiesRequest()
    {
        this(List.of(), List.of());
    }

    public LegendEntitiesRequest(@NonNull List<TextDocumentIdentifier> textDocuments, @NonNull List<String> entityPaths)
    {
        this.setTextDocuments(textDocuments);
        this.setEntityPaths(entityPaths);
    }

    public List<TextDocumentIdentifier> getTextDocuments()
    {
        return this.textDocuments;
    }

    public void setTextDocuments(@NonNull List<TextDocumentIdentifier> textDocument)
    {
        this.textDocuments = Preconditions.checkNotNull(textDocument, "textDocuments");
    }

    public List<String> getEntityPaths()
    {
        return this.entityPaths;
    }

    public void setEntityPaths(@NonNull List<String> entityPaths)
    {
        this.entityPaths = Preconditions.checkNotNull(entityPaths, "entityPaths");
    }
}
