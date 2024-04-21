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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
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
     * Apply the given consumer to each document state. No particular order is guaranteed.
     * Implementation could do this in parallel
     *
     * @param consumer document state consumer, needs to be threadsafe
     * @return future to track when this completes
     */
    CompletableFuture<Void> forEachDocumentStateParallel(Consumer<? super DocumentState> consumer);

    /**
     * Apply the given function to each document state, collecting its result in a list. No particular order is guaranteed.
     * Implementation could do this in parallel.
     *
     * @param func function to apply to each document state, needs to be threadsafe
     * @return future to with the collected results
     */
    <RESULT> CompletableFuture<List<RESULT>> collectFromEachDocumentState(Function<? super DocumentState, List<RESULT>> func);

    <RESULT> CompletableFuture<List<RESULT>> collectFromEachDocumentSectionState(BiFunction<? super DocumentState, ? super SectionState, List<RESULT>> func);

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
}
