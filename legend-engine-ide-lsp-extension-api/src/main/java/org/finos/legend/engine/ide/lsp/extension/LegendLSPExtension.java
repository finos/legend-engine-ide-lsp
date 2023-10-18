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

import java.util.Set;

/**
 * An LSP extension for Legend Engine representing some kind of grammar or DSL.
 */
public interface LegendLSPExtension
{
    /**
     * The name of the extension. This should be a non-empty string.
     *
     * @return extension name
     */
    String getName();

    /**
     * A collection of keywords defined by the extension.
     *
     * @return collection of keywords
     */
    default Iterable<? extends String> getKeywords()
    {
        return Set.of("Date","Integer","String","Float","StrictDate","Boolean","let","true","false");
    }
}
