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

package org.finos.legend.engine.ide.lsp.extension.state;

import java.util.Collection;
import java.util.function.Consumer;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;

/**
 * Global state, allowing access to the state for all documents.
 */
public interface GlobalState extends State
{
    /**
     * Get the state for the document with the given id. Returns null if it cannot find such a state, including if there
     * is no such document.
     *
     * @param id document id
     * @return document state or null
     */
    DocumentState getDocumentState(String id);

    /**
     * Apply the given consumer to each document state. No particular order is guaranteed.
     *
     * @param consumer document state consumer
     */
    void forEachDocumentState(Consumer<? super DocumentState> consumer);

    /**
     * List of available grammar extensions.  This is useful for extensions that need to dispatch to other extensions
     * for further processing
     * @return Collection of available extensions
     */
    Collection<LegendLSPGrammarExtension> getAvailableGrammarExtensions();
}
