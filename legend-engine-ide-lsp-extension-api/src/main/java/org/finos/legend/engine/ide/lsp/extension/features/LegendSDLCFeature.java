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

package org.finos.legend.engine.ide.lsp.extension.features;

import java.nio.file.Path;
import java.util.Map;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPFeature;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;

/**
 * Provide Legend SDLC features, translating/compensating from what the SDLC server expects,
 * or to what LSP can handle that SDLC server does not.
 */
public interface LegendSDLCFeature extends LegendLSPFeature
{
    @Override
    default String description()
    {
        return "SDLC Features";
    }

    /**
     * Takes a JSON that represent an SDLC Entity, and convert it to Pure grammar text
     * @param entityJson json of the entity to convert
     * @return Pure grammar text from the entityJson
     * @throws java.io.UncheckedIOException Thrown if failed to read JSON or to write Pure grammar
     */
    String entityJsonToPureText(String entityJson);

    /**
     * Split all the elements on a given document into multiple documents.  The new path of the new documents
     * follow Legend SDLC server expectations, where each element is stored on files that the path is derived from the element name.
     * <p>
     * For example, pkg1::pkg2::ElementName is expected in a file with path /pk1/pk2/ElementName.pure.
     * <p>
     * The conversion is top to bottom, splitting the document text on the lines where each element ends.  This
     * has the side effect of preserving comments that are above elements.
     * <p>
     * For example, a document with content as follows
     * <pre>
     *    Class hello::world
     *    {
     *        a: Integer[1];
     *    }
     *    // Sample comment here
     *    Class hello::moon
     *    {
     *        b: Integer[1];
     *    }
     * </pre>
     * <p>
     * Will generate two paths with content as follows
     * <ul>
     *     <li> File Path /hello/world.pure with content:
     * <pre>
     *    Class hello::world
     *    {
     *        a: Integer[1];
     *    }
     * </pre>
     *     <li> File Path /hello/moon.pure with content (the comment is preserved):
     * <pre>
     *    // Sample comment here
     *    Class hello::moon
     *    {
     *        b: Integer[1];
     *    }
     * </pre>
     * </ul>
     * <p>
     * For elements on DSL that required explicit defining it,
     * the conversion will insert the ###_DSL_NAME on top of the content.
     * <p>
     * @param rootFolder The root folder for the path of each element
     * @param documentState The document to convert into one element per file
     * @return A map of path for each element on the document to the content of each of these paths.
     * @throws UnsupportedOperationException if the start line of an element is the same as the end line of the previous element
     */
    Map<Path, String> convertToOneElementPerFile(Path rootFolder, DocumentState documentState);

    Map.Entry<String, String> contentToPureText(Map<String, ?> content);

    /**
     * Return the mapping of type to classifier path.
     *
     * @return Legend classifier path mapping
     */
    String getClassifierPathMap();
}
