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

package org.finos.legend.engine.ide.lsp.server.request;

import java.util.List;
import java.util.Objects;
import org.eclipse.lsp4j.jsonrpc.validation.NonNull;

/**
 * Represent a request with JSON files to convert to Pure text files
 */
public class LegendJsonToPureRequest
{
    @NonNull
    private List<String> jsonFileUris;

    public LegendJsonToPureRequest()
    {
    }

    public LegendJsonToPureRequest(List<String> jsonFileUris)
    {
        this.jsonFileUris = Objects.requireNonNull(jsonFileUris);
    }

    public List<String> getJsonFileUris()
    {
        return jsonFileUris;
    }
}
