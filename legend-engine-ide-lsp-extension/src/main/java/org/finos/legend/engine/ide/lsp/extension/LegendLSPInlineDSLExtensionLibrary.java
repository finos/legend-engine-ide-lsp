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

import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.impl.list.fixed.ArrayAdapter;

import java.util.ServiceLoader;
import java.util.Set;

public class LegendLSPInlineDSLExtensionLibrary extends LegendLSPExtensionLibrary<LegendLSPInlineDSLExtension>
{
    private LegendLSPInlineDSLExtensionLibrary(ImmutableMap<String, LegendLSPInlineDSLExtension> extensions)
    {
        super(extensions);
    }

    public Set<String> getInlineDSLs()
    {
        return getExtensionNames();
    }

    public static LegendLSPInlineDSLExtensionLibrary fromCurrentClassLoader()
    {
        return fromClassLoader(Thread.currentThread().getContextClassLoader());
    }

    public static LegendLSPInlineDSLExtensionLibrary fromClassLoader(ClassLoader classLoader)
    {
        return fromExtensions(ServiceLoader.load(LegendLSPInlineDSLExtension.class));
    }

    public static LegendLSPInlineDSLExtensionLibrary fromExtensions(LegendLSPInlineDSLExtension... extensions)
    {
        return fromExtensions(ArrayAdapter.adapt(extensions));
    }

    public static LegendLSPInlineDSLExtensionLibrary fromExtensions(Iterable<? extends LegendLSPInlineDSLExtension> extensions)
    {
        return new LegendLSPInlineDSLExtensionLibrary(indexExtensions(extensions));
    }
}
