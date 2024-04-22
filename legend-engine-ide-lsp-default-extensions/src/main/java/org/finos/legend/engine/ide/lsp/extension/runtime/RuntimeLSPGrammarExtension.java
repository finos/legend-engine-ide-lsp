/*
 * Copyright 2024 Goldman Sachs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.legend.engine.ide.lsp.extension.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.lazy.CompositeIterable;
import org.finos.legend.engine.ide.lsp.extension.AbstractLegacyParserLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.connection.ConnectionLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.language.pure.grammar.from.runtime.RuntimeParser;
import org.finos.legend.engine.protocol.pure.v1.model.context.PackageableElementPointer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.Connection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.ConnectionStores;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.EngineRuntime;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.PackageableRuntime;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.Runtime;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.RuntimePointer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.runtime.StoreConnections;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

/**
 * Extension for the Runtime grammar.
 */
public class RuntimeLSPGrammarExtension extends AbstractLegacyParserLSPGrammarExtension
{
    private static final List<String> KEYWORDS = List.of("Runtime", "import", "mappings", "connections");

    private static final ImmutableList<String> BOILERPLATE_SUGGESTIONS = Lists.immutable.with(
            "Runtime package::path::runtimeName\n" +
                "{\n" +
                "  mappings:\n" +
                "  [\n" +
                "    package::path::mapping1,\n" +
                "    package::path::mapping2\n" +
                "  ];\n" +
                "  connections:\n" +
                "  [\n" +
                "    package::path::store1:\n" +
                "    [\n" +
                "    connection_1: package::path::connection1\n" +
                "    ]\n" +
                "  ];\n" +
                "}\n"
    );

    public RuntimeLSPGrammarExtension()
    {
        super(RuntimeParser.newInstance(PureGrammarParserExtensions.fromAvailableExtensions()));
    }

    @Override
    public Iterable<? extends String> getKeywords()
    {
        return KEYWORDS;
    }

    @Override
    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        String codeLine = section.getSection().getLineUpTo(location);
        List<LegendCompletion> legendCompletions = Lists.mutable.empty();

        if (codeLine.isEmpty())
        {
            BOILERPLATE_SUGGESTIONS.collect(s -> new LegendCompletion("Runtime boilerplate", s.replaceAll("\n",System.lineSeparator())), legendCompletions);
        }

        return CompositeIterable.with(legendCompletions, this.computeCompletionsForSupportedTypes(section, location, Set.of("Runtime")));
    }

    @Override
    protected Collection<LegendReferenceResolver> getReferenceResolvers(SectionState section, PackageableElement packageableElement, Optional<CoreInstance> coreInstance)
    {
        if (!(packageableElement instanceof PackageableRuntime))
        {
            return List.of();
        }

        return this.getRuntimeReferences(((PackageableRuntime) packageableElement).runtimeValue, section.getDocumentState().getGlobalState()).collect(Collectors.toList());
    }

    public Stream<LegendReferenceResolver> getRuntimeReferences(Runtime runtime, GlobalState state)
    {
        if (runtime instanceof EngineRuntime)
        {
            return this.toEngineRuntimeReferences((EngineRuntime) runtime, state);
        }

        if (runtime instanceof RuntimePointer)
        {
            return Stream.of(this.toRuntimePointerReference((RuntimePointer) runtime));
        }

        return Stream.empty();
    }

    private Stream<LegendReferenceResolver> toEngineRuntimeReferences(EngineRuntime engineRuntime, GlobalState state)
    {
        return Stream.concat(this.toMappingReferences(engineRuntime.mappings),
                        Stream.concat(this.toStoreConnectionReferences(engineRuntime.connections, state),
                                this.toConnectionStoreReferences(engineRuntime.connectionStores, state)));
    }

    private LegendReferenceResolver toRuntimePointerReference(RuntimePointer runtimePointer)
    {
        return LegendReferenceResolver.newReferenceResolver(
                runtimePointer.sourceInformation,
                x -> x.resolveRuntime(runtimePointer.runtime, runtimePointer.sourceInformation)
        );
    }

    private Stream<LegendReferenceResolver> toMappingReferences(List<PackageableElementPointer> mappings)
    {
        return mappings.stream()
                .map(this::toMappingReference);
    }

    private LegendReferenceResolver toMappingReference(PackageableElementPointer packageableElementPointer)
    {
        return LegendReferenceResolver.newReferenceResolver(
                packageableElementPointer.sourceInformation,
                x -> x.resolveMapping(packageableElementPointer.path, packageableElementPointer.sourceInformation)
        );
    }

    private Stream<LegendReferenceResolver> toStoreConnectionReferences(List<StoreConnections> storeConnections, GlobalState state)
    {
        return storeConnections.stream()
                .flatMap(storeConnection ->
                {
                    LegendReferenceResolver storeReference = this.toStoreReference(storeConnection.store);
                    Stream<LegendReferenceResolver> connectionReferences = storeConnection.storeConnections
                            .stream()
                            .flatMap(identifiedConnection -> this.toConnectionReferences(identifiedConnection.connection, state));
                    return Stream.concat(Stream.of(storeReference), connectionReferences);
                });
    }

    private Stream<LegendReferenceResolver> toConnectionStoreReferences(List<ConnectionStores> connectionStores, GlobalState state)
    {
        return connectionStores.stream()
                .flatMap(connectionStore ->
                {
                    Stream<LegendReferenceResolver> connectionReferences = this.toConnectionReferences(connectionStore.connectionPointer, state);
                    Stream<LegendReferenceResolver> storeReferences = connectionStore.storePointers
                            .stream()
                            .map(packageableElementPointer -> this.toStoreReference(packageableElementPointer));
                    return Stream.concat(connectionReferences, storeReferences);
                });

    }

    private LegendReferenceResolver toStoreReference(PackageableElementPointer packageableElementPointer)
    {
        return LegendReferenceResolver.newReferenceResolver(
                packageableElementPointer.sourceInformation,
                x -> x.resolveStore(packageableElementPointer.path, packageableElementPointer.sourceInformation)
        );
    }

    private Stream<LegendReferenceResolver> toConnectionReferences(Connection connection, GlobalState state)
    {
        return state.findGrammarExtensionThatImplements(ConnectionLSPGrammarExtension.class)
                .flatMap(x -> x.getConnectionReferences(connection, state));
    }
}
