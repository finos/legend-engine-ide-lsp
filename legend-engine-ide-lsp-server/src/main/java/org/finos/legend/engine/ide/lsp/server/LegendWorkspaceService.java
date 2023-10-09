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
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LegendWorkspaceService implements WorkspaceService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendWorkspaceService.class);

    LegendWorkspaceService()
    {
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params)
    {
        LOGGER.debug("Configuration change: {}", params);
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
    {
        LOGGER.debug("Watched files change: {}", params);
    }
}
