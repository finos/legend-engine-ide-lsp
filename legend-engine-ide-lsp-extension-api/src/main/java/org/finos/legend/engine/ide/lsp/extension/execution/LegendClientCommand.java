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

import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;

import java.util.Map;

public class LegendClientCommand extends LegendCommand
{
    private LegendClientCommand(String entity, String id, String title, TextLocation location, Map<String, String> executableArgs, Map<String, LegendInputParamter> inputParameters)
    {
        super(entity, id, title, location, executableArgs, inputParameters);
    }

    public static LegendClientCommand newCommand(String entity, String id, String title, TextLocation location, Map<String, String> executableArgs, Map<String, LegendInputParamter> inputParameters)
    {
        return new LegendClientCommand(entity, id, title, location, executableArgs, inputParameters);
    }
}
