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

package org.finos.legend.engine.ide.lsp.extension;

import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;

public class LegendDiagnostic
{
    TextInterval location;
    String message;
    Severity severity;
    Type type;

    public LegendDiagnostic(TextInterval location, String message, Severity severity, Type type)
    {
        this.location = location;
        this.message = message;
        this.severity = severity;
        this.type = type;
    }

    enum Severity { info, warning, error }

    enum Type { parser, compiler }
}
