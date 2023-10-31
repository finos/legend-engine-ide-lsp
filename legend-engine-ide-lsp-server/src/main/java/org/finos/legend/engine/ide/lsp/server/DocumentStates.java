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

import org.finos.legend.engine.ide.lsp.text.GrammarSectionIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

class DocumentStates
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentStates.class);

    private final Map<String, DocumentState> states = new HashMap<>();

    DocumentState getState(String uri)
    {
        synchronized (this)
        {
            return this.states.get(uri);
        }
    }

    void removeState(String uri)
    {
        synchronized (this)
        {
            this.states.remove(uri);
        }
    }

    DocumentState newState(String uri, int version, String text)
    {
        synchronized (this)
        {
            return this.states.compute(uri, (key, current) ->
            {
                if (current == null)
                {
                    return new DocumentState(uri, version, text);
                }
                if (current.getVersion() > version)
                {
                    LOGGER.warn("Previous document state for {} has more recent version ({} > {}): leaving in place", uri, current.getVersion(), version);
                }
                else
                {
                    LOGGER.warn("Overwriting previous document state for {} at version {} with version {}", uri, current.getVersion(), version);
                    current.update(version, text);
                }
                return current;
            });
        }
    }

    static class DocumentState
    {
        private final String uri;
        private int version;
        private GrammarSectionIndex sectionIndex;

        private DocumentState(String uri, int version, String text)
        {
            this.uri = uri;
            this.version = version;
            this.sectionIndex = (text == null) ? null : GrammarSectionIndex.parse(text);
        }

        String getUri()
        {
            return this.uri;
        }

        synchronized int getVersion()
        {
            return this.version;
        }

        synchronized GrammarSectionIndex getSectionIndex()
        {
            return this.sectionIndex;
        }

        synchronized boolean update(int version)
        {
            if (version < this.version)
            {
                LOGGER.warn("Trying to update {} from version {} to {}", this.uri, this.version, version);
                return false;
            }
            this.version = version;
            return true;
        }

        synchronized boolean update(String text)
        {
            boolean changed = (this.sectionIndex == null) ? (text != null) : !this.sectionIndex.getText().equals(text);
            if (changed)
            {
                this.sectionIndex = (text == null) ? null : GrammarSectionIndex.parse(text);
            }
            return changed;
        }

        synchronized boolean update(int version, String text)
        {
            if (version < this.version)
            {
                LOGGER.warn("Trying to update {} from version {} to {}: update rejected", this.uri, this.version, version);
                return false;
            }
            if (version == this.version)
            {
                LOGGER.warn("Trying to update {} from version {} to {}", this.uri, this.version, version);
                return update(text);
            }

            // version > this.version
            this.version = version;
            update(text);
            return true;
        }
    }
}
