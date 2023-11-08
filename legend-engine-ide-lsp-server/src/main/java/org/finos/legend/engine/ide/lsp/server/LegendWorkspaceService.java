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

import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LegendWorkspaceService implements WorkspaceService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendWorkspaceService.class);

    private final LegendLanguageServer server;

    LegendWorkspaceService(LegendLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params)
    {
        this.server.checkReady();
        LOGGER.debug("Did change configuration: {}", params);
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
    {
        LOGGER.debug("Did change watched files: {}", params);
        this.server.runPossiblyAsync(() ->
        {
            LegendServerGlobalState globalState = this.server.getGlobalState();
            synchronized (globalState)
            {
                params.getChanges().forEach(fileEvent ->
                {
                    String uri = fileEvent.getUri();
                    switch (fileEvent.getType())
                    {
                        case Changed:
                        {
                            fileChanged(globalState, uri);
                            break;
                        }
                        case Created:
                        {
                            fileCreated(globalState, uri);
                            break;
                        }
                        case Deleted:
                        {
                            fileDeleted(globalState, uri);
                            break;
                        }
                        default:
                        {
                            LOGGER.warn("Unhandled change type for {}: {}", uri, fileEvent.getType());
                        }
                    }
                });
            }
        });
    }

    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params)
    {
        LOGGER.debug("Did change workspace folders: {}", params);
        synchronized (this.server.getGlobalState())
        {
            WorkspaceFoldersChangeEvent event = params.getEvent();
            this.server.updateWorkspaceFolders(event.getAdded(), event.getRemoved());
        }
    }

    @Override
    public void didCreateFiles(CreateFilesParams params)
    {
        LOGGER.debug("Did create files: {}", params);
        this.server.runPossiblyAsync(() ->
        {
            LegendServerGlobalState globalState = this.server.getGlobalState();
            synchronized (globalState)
            {
                params.getFiles().forEach(f -> fileCreated(globalState, f.getUri()));
            }
        });
    }

    @Override
    public void didRenameFiles(RenameFilesParams params)
    {
        LOGGER.debug("Did rename files: {}", params);
        this.server.runPossiblyAsync(() ->
        {
            LegendServerGlobalState globalState = this.server.getGlobalState();
            synchronized (globalState)
            {
                params.getFiles().forEach(f -> fileRenamed(globalState, f.getOldUri(), f.getNewUri()));
            }
        });
    }

    @Override
    public void didDeleteFiles(DeleteFilesParams params)
    {
        LOGGER.debug("Did delete files: {}", params);
        this.server.runPossiblyAsync(() ->
        {
            LegendServerGlobalState globalState = this.server.getGlobalState();
            synchronized (globalState)
            {
                params.getFiles().forEach(f -> fileDeleted(globalState, f.getUri()));
            }
        });
    }

    private void fileChanged(LegendServerGlobalState globalState, String uri)
    {
        LOGGER.debug("Changed {}", uri);
        LegendServerGlobalState.LegendServerDocumentState docState = globalState.getOrCreateDocState(uri);
        if (!docState.isOpen())
        {
            docState.clearText();
        }
    }

    private void fileCreated(LegendServerGlobalState globalState, String uri)
    {
        LOGGER.debug("Created {}", uri);
        globalState.getOrCreateDocState(uri);
    }

    private void fileDeleted(LegendServerGlobalState globalState, String uri)
    {
        LOGGER.debug("Deleted {}", uri);
        globalState.deleteDocState(uri);
    }

    private void fileRenamed(LegendServerGlobalState globalState, String oldUri, String newUri)
    {
        LOGGER.debug("Renamed {} to {}", oldUri, newUri);
        globalState.renameDoc(oldUri, newUri);
    }
}
