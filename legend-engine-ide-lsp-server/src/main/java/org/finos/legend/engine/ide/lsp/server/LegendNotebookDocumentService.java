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

package org.finos.legend.engine.ide.lsp.server;

import java.util.List;
import org.eclipse.lsp4j.DidChangeNotebookDocumentParams;
import org.eclipse.lsp4j.DidCloseNotebookDocumentParams;
import org.eclipse.lsp4j.DidOpenNotebookDocumentParams;
import org.eclipse.lsp4j.DidSaveNotebookDocumentParams;
import org.eclipse.lsp4j.NotebookDocumentChangeEventCellStructure;
import org.eclipse.lsp4j.NotebookDocumentChangeEventCellTextContent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.services.NotebookDocumentService;
import org.finos.legend.engine.ide.lsp.text.LineIndexedText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegendNotebookDocumentService implements NotebookDocumentService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendNotebookDocumentService.class);

    private final LegendLanguageServer server;

    LegendNotebookDocumentService(LegendLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public void didOpen(DidOpenNotebookDocumentParams params)
    {
        params.getCellTextDocuments().forEach(this::openNotebookDocument);
    }

    private void openNotebookDocument(TextDocumentItem textDocumentItem)
    {
        LegendServerGlobalState.LegendServerDocumentState docState = this.server.getGlobalState().getOrCreateNotebookDocState(textDocumentItem.getUri());
        docState.change(textDocumentItem.getVersion(), LineIndexedText.index(textDocumentItem.getText()));
    }

    @Override
    public void didChange(DidChangeNotebookDocumentParams params)
    {
        // structure for when cells are added / removed
        NotebookDocumentChangeEventCellStructure structure = params.getChange().getCells().getStructure();
        if (structure != null)
        {
            // new cells
            structure.getDidOpen().forEach(this::openNotebookDocument);
            // removed cells
            structure.getDidClose().forEach(this::closeNotebookDocument);
        }

        // actual changes to cell content
        List<NotebookDocumentChangeEventCellTextContent> textContent = params.getChange().getCells().getTextContent();
        if (textContent != null)
        {
            textContent.forEach(textContentForCell ->
            {
                VersionedTextDocumentIdentifier document = textContentForCell.getDocument();
                LegendServerGlobalState.LegendServerDocumentState documentState = this.server.getGlobalState().getDocumentState(document.getUri());

                if (LegendTextDocumentService.applyChanges(documentState, document.getVersion(), textContentForCell.getChanges()))
                {
                    LOGGER.debug("Changed {} (version {})", document.getUri(), document.getVersion());
                }
            });
        }
    }

    @Override
    public void didSave(DidSaveNotebookDocumentParams params)
    {
        // should not happen...
    }

    @Override
    public void didClose(DidCloseNotebookDocumentParams params)
    {

    }

    private void closeNotebookDocument(TextDocumentIdentifier x)
    {
        this.server.getGlobalState().deleteDocState(x.getUri(), false);
    }
}
