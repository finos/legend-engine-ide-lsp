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

import java.io.IOException;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPFeature;

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
     * @throws IOException Thrown if failed to read JSON or to write Pure grammar
     */
    String entityJsonToPureText(String entityJson) throws IOException;
}
