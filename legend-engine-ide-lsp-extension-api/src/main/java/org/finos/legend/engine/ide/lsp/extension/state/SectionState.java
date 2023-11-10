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

import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;

/**
 * The state of a section of a document.
 */
public interface SectionState extends State
{
    /**
     * Get the state of the document that contains this section.
     * 
     * @return owning document state
     */
    DocumentState getDocumentState();

    /**
     * Get the number of the section in the document. Calling
     * {@code getDocumentState().getSectionState(getSectionNumber())} should return this section state.
     * 
     * @return section number
     * @see DocumentState#getSectionState
     */
    int getSectionNumber();

    /**
     * Get the section itself.
     * 
     * @return section
     */
    GrammarSection getSection();

    /**
     * Get the extension associated with the section.
     *
     * @return extension
     */
    LegendLSPGrammarExtension getExtension();

    @Override
    default void logInfo(String message)
    {
        getDocumentState().logInfo(message);
    }

    @Override
    default void logWarning(String message)
    {
        getDocumentState().logWarning(message);
    }

    @Override
    default void logError(String message)
    {
        getDocumentState().logError(message);
    }
}
