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

package org.finos.legend.engine.ide.lsp.extension;

import java.util.Collections;
import java.util.Map;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommandType;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendInputParameter;
import org.finos.legend.engine.protocol.pure.v1.model.SourceInformation;

public interface CommandConsumer
{
    default void accept(String id, String title, SourceInformation sourceInfo)
    {
        accept(id, title, sourceInfo, Collections.emptyMap());
    }

    default void accept(String id, String title, SourceInformation sourceInfo, LegendCommandType type)
    {
        accept(id, title, sourceInfo, Collections.emptyMap(), Collections.emptyMap(), type);
    }

    default void accept(String id, String title, SourceInformation sourceInfo, Map<String, String> arguments)
    {
        accept(id, title, sourceInfo, arguments, Collections.emptyMap(), LegendCommandType.SERVER);
    }

    void accept(String id, String title, SourceInformation sourceInfo, Map<String, String> arguments, Map<String, LegendInputParameter> inputParameters, LegendCommandType type);
}
