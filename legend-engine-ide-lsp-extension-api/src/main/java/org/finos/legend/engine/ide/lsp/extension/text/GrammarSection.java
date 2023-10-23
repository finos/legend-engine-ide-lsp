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

package org.finos.legend.engine.ide.lsp.extension.text;

/**
 * <p>A section of a text document in a particular grammar. This is a continuous interval of lines, possibly starting
 * with a grammar declaration line of the form "{@code ###GrammarName}". A grammar declaration line indicates the start
 * of a new section, so it will occur only as the first line of a section. Sections after the first in a document must
 * begin with an explicit grammar declaration line. The first section may optionally begin with an explicit grammar
 * declaration line. If an explicit one is not present, the section is deemed to have an implicit grammar declaration
 * line equivalent to {@code ###Pure}.</p>
 * <br>
 * <p>A grammar section begins at the beginning of its first line and ends at the end of its final line, either with a
 * line break or the end of the text document. Sections contain only complete lines; no section will contain a partial
 * line.</p>
 */
public interface GrammarSection
{
    /**
     * Get the grammar name of the section.
     *
     * @return grammar name
     */
    String getGrammar();

    /**
     * Get the start line of the section. Note that this uses zero-based line numbering.
     *
     * @return section start line
     */
    int getStartLine();

    /**
     * Get the end line of the section. This is the last line included in the section. Note that this uses zero-based
     * line numbering. Note also that if this is the last section of the text, the end line may be empty.
     *
     * @return section end line
     */
    int getEndLine();

    /**
     * Get the text of the section.
     *
     * @return section text
     */
    String getText();

    /**
     * Get the full text that this is a section of.
     *
     * @return full text
     */
    String getFullText();

    /**
     * Get a single line of the section.
     *
     * @param line line number
     * @return section line
     * @throws IndexOutOfBoundsException if there is no such line in the section
     */
    String getLine(int line);

    /**
     * Get the length of a line of the section.
     *
     * @param line line number
     * @return line length
     * @throws IndexOutOfBoundsException if there is no such line in the section
     */
    int getLineLength(int line);
}
