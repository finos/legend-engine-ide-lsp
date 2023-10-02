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

import java.util.Map;
import java.util.Set;

/**
 * Legend LSP extension library for inline DSL extensions.
 */
public class LegendLSPInlineDSLLibrary extends LegendLSPExtensionLibrary<LegendLSPInlineDSLExtension>
{
    private LegendLSPInlineDSLLibrary(Map<String, LegendLSPInlineDSLExtension> extensions)
    {
        super(extensions);
    }

    /**
     * Get an unmodifiable set of all the inline DSLs in the library. This is equivalent to {@link #getExtensionNames()}.
     *
     * @return unmodifiable set of inline DSLs
     */
    public Set<String> getInlineDSLs()
    {
        return getExtensionNames();
    }

    /**
     * Create a new library from the extensions found in the current thread's context class loader. An exception will be
     * thrown if any two extensions have the same name.
     *
     * @return extension library
     */
    public static LegendLSPInlineDSLLibrary fromCurrentClassLoader()
    {
        return fromClassLoader(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Create a new library from the extensions found in the given class loader. An exception will be thrown if any two
     * extensions have the same name.
     *
     * @param classLoader class loader to load the extensions from
     * @return extension library
     */
    public static LegendLSPInlineDSLLibrary fromClassLoader(ClassLoader classLoader)
    {
        return builder().withExtensionsFrom(classLoader).build();
    }

    /**
     * Create a new library from an array of extensions. An exception will be thrown if any two extensions have the
     * same name.
     *
     * @param extensions extensions for the library
     * @return extension library
     */
    public static LegendLSPInlineDSLLibrary fromExtensions(LegendLSPInlineDSLExtension... extensions)
    {
        return builder().withExtensions(extensions).build();
    }

    /**
     * Create a new library from an iterable of extensions. An exception will be thrown if any two extensions have the
     * same name.
     *
     * @param extensions extensions for the library
     * @return extension library
     */
    public static LegendLSPInlineDSLLibrary fromExtensions(Iterable<? extends LegendLSPInlineDSLExtension> extensions)
    {
        return builder().withExtensions(extensions).build();
    }

    /**
     * Return a new library builder.
     *
     * @return library builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Builder for {@link LegendLSPInlineDSLLibrary}
     */
    public static class Builder extends AbstractBuilder<LegendLSPInlineDSLExtension, LegendLSPInlineDSLLibrary>
    {
        private Builder()
        {
        }

        /**
         * Add the extension to the builder and return the builder. For more information, see
         * {@link LegendLSPInlineDSLLibrary.Builder#addExtension}.
         *
         * @param extension extension to add
         * @return this builder
         * @see LegendLSPInlineDSLLibrary.Builder#addExtension
         */
        public Builder withExtension(LegendLSPInlineDSLExtension extension)
        {
            addExtension(extension);
            return this;
        }

        /**
         * Add all the given extensions to the builder and return the builder. For more information, see
         * {@link LegendLSPInlineDSLLibrary.Builder#addExtensions}.
         *
         * @param extensions extensions to add
         * @return this builder
         * @see LegendLSPInlineDSLLibrary.Builder#addExtensions
         */
        public Builder withExtensions(LegendLSPInlineDSLExtension... extensions)
        {
            addExtensions(extensions);
            return this;
        }

        /**
         * Add all the given extensions to the builder and return the builder. For more information, see
         * {@link LegendLSPInlineDSLLibrary.Builder#addExtensions}.
         *
         * @param extensions extensions to add
         * @return this builder
         * @see LegendLSPInlineDSLLibrary.Builder#addExtensions
         */
        public Builder withExtensions(Iterable<? extends LegendLSPInlineDSLExtension> extensions)
        {
            addExtensions(extensions);
            return this;
        }

        /**
         * Add all extensions found in the given class loader. For more information, see
         * {@link LegendLSPInlineDSLLibrary.Builder#addExtensionsFrom}.
         *
         * @param classLoader class loader to load extensions from
         * @return this builder
         * @see LegendLSPInlineDSLLibrary.Builder#addExtensionsFrom
         */
        public Builder withExtensionsFrom(ClassLoader classLoader)
        {
            addExtensionsFrom(classLoader);
            return this;
        }

        @Override
        protected LegendLSPInlineDSLLibrary build(Map<String, LegendLSPInlineDSLExtension> extensionIndex)
        {
            return new LegendLSPInlineDSLLibrary(extensionIndex);
        }

        @Override
        protected Class<LegendLSPInlineDSLExtension> getExtensionClass()
        {
            return LegendLSPInlineDSLExtension.class;
        }
    }
}
