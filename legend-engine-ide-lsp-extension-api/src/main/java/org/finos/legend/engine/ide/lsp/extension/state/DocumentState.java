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

import java.util.function.Consumer;

/**
 * The state of a particular document.
 */
public interface DocumentState
{
    /**
     * Get the global state that this is a part of.
     *
     * @return global state
     */
    GlobalState getGlobalState();

    /**
     * Get the id of the document. Calling {@code getGlobalState().getDocumentState(getDocumentId())} should return this
     * document state.
     *
     * @return document id
     * @see GlobalState#getDocumentState
     */
    String getDocumentId();

    /**
     * Get the text of the document.
     *
     * @return document text
     */
    String getText();

    /**
     * Get the number of sections in the document. As long as the document has text, this will be non-zero.
     *
     * @return section count
     */
    int getSectionCount();

    /**
     * Get the state for the nth section (starting from 0).
     *
     * @param n section number
     * @return state of the nth section
     */
    SectionState getSectionState(int n);

    /**
     * Apply the given consumer to each section state in order.
     *
     * @param consumer section state consumer
     */
    void forEachSectionState(Consumer<? super SectionState> consumer);
}
