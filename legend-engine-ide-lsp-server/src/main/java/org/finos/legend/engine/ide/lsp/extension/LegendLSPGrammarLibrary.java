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
 * Legend LSP extension library for top level grammar extensions.
 */
public class LegendLSPGrammarLibrary extends LegendLSPExtensionLibrary<LegendLSPGrammarExtension>
{
    private LegendLSPGrammarLibrary(Map<String, LegendLSPGrammarExtension> extensions)
    {
        super(extensions);
    }

    /**
     * Get an unmodifiable set of all the grammars in the library. This is equivalent to {@link #getExtensionNames()}.
     *
     * @return unmodifiable set of grammars
     */
    public Set<String> getGrammars()
    {
        return getExtensionNames();
    }

    /**
     * Create a new library from the extensions found in the current thread's context class loader. An exception will be
     * thrown if any two extensions have the same name.
     *
     * @return extension library
     */
    public static LegendLSPGrammarLibrary fromCurrentClassLoader()
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
    public static LegendLSPGrammarLibrary fromClassLoader(ClassLoader classLoader)
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
    public static LegendLSPGrammarLibrary fromExtensions(LegendLSPGrammarExtension... extensions)
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
    public static LegendLSPGrammarLibrary fromExtensions(Iterable<? extends LegendLSPGrammarExtension> extensions)
    {
        return builder().withExtensions(extensions).build();
    }

    /**
     * Return a new library builder.
     *
     * @return builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Builder for {@link LegendLSPGrammarLibrary}
     */
    public static class Builder extends AbstractBuilder<LegendLSPGrammarExtension, LegendLSPGrammarLibrary>
    {
        private Builder()
        {
        }

        /**
         * Add the extension to the builder and return the builder. For more information, see
         * {@link LegendLSPGrammarLibrary.Builder#addExtension}.
         *
         * @param extension extension to add
         * @return this builder
         * @see LegendLSPGrammarLibrary.Builder#addExtension
         */
        public Builder withExtension(LegendLSPGrammarExtension extension)
        {
            addExtension(extension);
            return this;
        }

        /**
         * Add all the given extensions to the builder and return the builder. For more information, see
         * {@link LegendLSPGrammarLibrary.Builder#addExtensions}.
         *
         * @param extensions extensions to add
         * @return this builder
         * @see LegendLSPGrammarLibrary.Builder#addExtensions
         */
        public Builder withExtensions(LegendLSPGrammarExtension... extensions)
        {
            addExtensions(extensions);
            return this;
        }

        /**
         * Add all the given extensions to the builder and return the builder. For more information, see
         * {@link LegendLSPGrammarLibrary.Builder#addExtensions}.
         *
         * @param extensions extensions to add
         * @return this builder
         * @see LegendLSPGrammarLibrary.Builder#addExtensions
         */
        public Builder withExtensions(Iterable<? extends LegendLSPGrammarExtension> extensions)
        {
            addExtensions(extensions);
            return this;
        }

        /**
         * Add all extensions found in the given class loader. For more information, see
         * {@link LegendLSPGrammarLibrary.Builder#addExtensionsFrom}.
         *
         * @param classLoader class loader to load extensions from
         * @return this builder
         * @see LegendLSPGrammarLibrary.Builder#addExtensionsFrom
         */
        public Builder withExtensionsFrom(ClassLoader classLoader)
        {
            addExtensionsFrom(classLoader);
            return this;
        }

        @Override
        protected LegendLSPGrammarLibrary build(Map<String, LegendLSPGrammarExtension> extensionIndex)
        {
            return new LegendLSPGrammarLibrary(extensionIndex);
        }

        @Override
        protected Class<LegendLSPGrammarExtension> getExtensionClass()
        {
            return LegendLSPGrammarExtension.class;
        }
    }
}
