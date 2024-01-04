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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
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

    private final Map<String, T> extensionsByName;

    /**
     * Constructor for the library, which takes an index of extensions by name. Generally, this should be the index
     * created by the builder.
     *
     * @param extensionsByName index of extensions by name
     */
    protected LegendLSPExtensionLibrary(Map<String, T> extensionsByName)
    {
        this.extensionsByName = Map.copyOf(extensionsByName);
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
        return this.extensionsByName.keySet();
    }

    /**
     * Get an unmodifiable collection of all extensions in the library.
     *
     * @return unmodifiable collection of extensions
     */
    public Collection<T> getExtensions()
    {
        return this.extensionsByName.values();
    }

    /**
     * Abstract base class for extension library builders.
     *
     * @param <E> extension class
     * @param <L> extension library class
     */
    public abstract static class AbstractBuilder<E extends LegendLSPExtension, L extends LegendLSPExtensionLibrary<E>>
    {
        private final Map<String, E> extensions = new HashMap<>();

        /**
         * Build the extension library.
         *
         * @return extension library
         */
        public L build()
        {
            Map<String, E> map = Map.copyOf(this.extensions);
            LOGGER.debug("Building library with extensions: {}", map.keySet());
            return build(map);
        }

        /**
         * Build the extension library from the given unmodifiable map of extensions by name.
         *
         * @param extensionIndex unmodifiable map of extensions by name
         * @return extension library
         */
        protected abstract L build(Map<String, E> extensionIndex);

        /**
         * Add the extension to the builder. Throws an {@link IllegalArgumentException} if there is already an extension
         * with that name.
         *
         * @param extension extension to add
         */
        public void addExtension(E extension)
        {
            Objects.requireNonNull(extension, "extension may not be null");
            String name = extension.getName();
            E old = this.extensions.putIfAbsent(name, extension);
            if (old == extension)
            {
                LOGGER.warn("Extension {} already added", name);
            }
            else if (old != null)
            {
                String message = "Multiple extensions named: \"" + name + "\"";
                LOGGER.error(message);
                throw new IllegalArgumentException(message);
            }
        }

        /**
         * Add all the given extensions to the builder. This is equivalent to repeatedly calling {@link #addExtension}
         * on the individual extensions.
         *
         * @param extensions extensions to add
         */
        @SuppressWarnings("unchecked")
        public void addExtensions(E... extensions)
        {
            for (E extension : extensions)
            {
                addExtension(extension);
            }
        }

        /**
         * Add all the given extensions to the builder. This is equivalent to repeatedly calling {@link #addExtension}
         * on the individual extensions.
         *
         * @param extensions extensions to add
         */
        public void addExtensions(Iterable<? extends E> extensions)
        {
            extensions.forEach(this::addExtension);
        }

        /**
         * Add all extensions found in the given class loader.
         *
         * @param classLoader class loader to load extensions from
         */
        public void addExtensionsFrom(ClassLoader classLoader)
        {
            addExtensions(ServiceLoader.load(getExtensionClass(), classLoader));
        }

        /**
         * Get the extension class for the library.
         *
         * @return extension class
         */
        protected abstract Class<E> getExtensionClass();
    }
}
