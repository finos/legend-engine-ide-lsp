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

package org.finos.legend.engine.ide.lsp.extension.execution;

import java.util.Objects;

public class LegendExecutionSource
{
    private final String entityPath;
    private final SourceType sourceType;
    final String _type;

    protected LegendExecutionSource(String entityPath, SourceType type, String _type)
    {
        this.entityPath = Objects.requireNonNull(entityPath, "entity path is required");
        this.sourceType = Objects.requireNonNull(type, "source type is required");
        this._type = _type == null ? "legendExecutionSource" : _type;
    }

    public String getEntityPath()
    {
        return entityPath;
    }

    public SourceType getSourceType()
    {
        return sourceType;
    }

    public static LegendExecutionSource newSource(String entityPath, SourceType type)
    {
        return new LegendExecutionSource(entityPath, type, null);
    }

    public enum SourceType
    {
        TEST, QUERY
    }
}
