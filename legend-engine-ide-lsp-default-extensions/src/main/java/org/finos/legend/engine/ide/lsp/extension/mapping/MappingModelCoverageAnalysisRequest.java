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

package org.finos.legend.engine.ide.lsp.extension.mapping;

import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContext;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;

public class MappingModelCoverageAnalysisRequest
{
    private final String clientVersion;
    private final String mapping;
    private final PureModelContextData model;

    public MappingModelCoverageAnalysisRequest(String clientVersion, String mapping, PureModelContextData model)
    {
        this.clientVersion = clientVersion;
        this.mapping = mapping;
        this.model = model;
    }

    public String getClientVersion()
    {
        return this.clientVersion;
    }

    public String getMapping()
    {
        return this.mapping;
    }

    public PureModelContext getModel()
    {
        return this.model;
    }
}
