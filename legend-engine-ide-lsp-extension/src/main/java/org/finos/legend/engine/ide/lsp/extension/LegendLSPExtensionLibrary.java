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

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;

/**
 * Abstract base class for Legend LSP extension libraries. Within the context of a single library, all extensions must
 * have a unique name.
 *
 * @param <T> extension type
 */
public abstract class LegendLSPExtensionLibrary<T extends LegendLSPExtension>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendLSPExtensionLibrary.class);

    protected final ImmutableMap<String, T> extensionsByName;

    protected LegendLSPExtensionLibrary(ImmutableMap<String, T> extensionsByName)
    {
        this.extensionsByName = extensionsByName;
    }

    /**
     * Get an extension by its name. Returns null if it cannot find the named extension in the library.
     *
     * @param name extension name
     * @return named extension or null
     */
    public T getExtension(String name)
    {
        return this.extensionsByName.get(name);
    }

    /**
     * Get an unmodifiable set of all extension names in the library.
     *
     * @return unmodifiable set of extension names
     */
    public Set<String> getExtensionNames()
    {
        return this.extensionsByName.castToMap().keySet();
    }

    /**
     * Get an unmodifiable collection of all extensions in the library.
     *
     * @return unmodifiable collection of extensions
     */
    public Collection<T> getExtensions()
    {
        return this.extensionsByName.castToMap().values();
    }

    protected static <T extends LegendLSPExtension> ImmutableMap<String, T> indexExtensions(Iterable<? extends T> extensions)
    {
        MutableMap<String, T> index = Maps.mutable.empty();
        extensions.forEach(ext ->
        {
            String name = ext.getName();
            LOGGER.debug("Indexing extension {}", name);
            T old = index.put(name, ext);
            if ((old != null) && (old != ext))
            {
                throw new IllegalArgumentException("Multiple extensions named: \"" + name + "\"");
            }
        });
        return index.toImmutable();
    }
}
