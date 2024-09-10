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

package org.finos.legend.engine.ide.lsp.extension.state;

import java.util.Collection;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPFeature;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;

/**
 * Global state, allowing access to the state for all documents.
 */
public interface GlobalState extends State
{
    /**
     * Get the state for the document with the given id. Returns null if it cannot find such a state, including if there
     * is no such document.
     *
     * @param id document id
     * @return document state or null
     */
    DocumentState getDocumentState(String id);

    /**
     * Apply the given consumer to each document state. No particular order is guaranteed.
     *
     * @param consumer document state consumer
     */
    void forEachDocumentState(Consumer<? super DocumentState> consumer);

    /**
     * List of available grammar extensions.  This is useful for extensions that need to dispatch to other extensions
     * for further processing
     *
     * @return Collection of available extensions
     */
    Collection<LegendLSPGrammarExtension> getAvailableGrammarExtensions();

    /**
     * Some extensions can sub-specialize and implement other interfaces.
     * <p></p>
     * This iterates through available extensions, and return those that implement the given type
     *
     * @param type the sub-specialization class of the extension to look for
     * @param <T>  The type to look for
     * @return Stream of extensions that are instances of the given type
     */
    default <T> Stream<? extends T> findGrammarExtensionThatImplements(Class<T> type)
    {
        return this.getAvailableGrammarExtensions()
                .stream()
                .filter(type::isInstance)
                .map(type::cast);
    }

    /**
     * List of available features.
     *
     * @return Collection of available features
     */
    Collection<LegendLSPFeature> getAvailableLegendLSPFeatures();

    /**
     * This iterates through available features, and return those that implement the given type
     *
     * @param type the sub-specialization class of the feature to look for
     * @param <T>  The type to look for
     * @return Stream of features that are instances of the given type
     */
    default <T> Stream<? extends T> findFeatureThatImplements(Class<T> type)
    {
        return this.getAvailableLegendLSPFeatures()
                .stream()
                .filter(type::isInstance)
                .map(type::cast);
    }

    default ForkJoinPool getForkJoinPool()
    {
        return new ForkJoinPool(1, x ->
        {
            ForkJoinWorkerThread forkJoinWorkerThread = new ForkJoinWorkerThread(x)
            {
            };
            forkJoinWorkerThread.setContextClassLoader(Thread.currentThread().getContextClassLoader());
            return forkJoinWorkerThread;
        }, null,  false);
    }

    /**
     * Get the value of a setting. Returns null if the setting has no value.
     *
     * @param key setting key
     * @return setting value or null
     */
    String getSetting(String key);
}
