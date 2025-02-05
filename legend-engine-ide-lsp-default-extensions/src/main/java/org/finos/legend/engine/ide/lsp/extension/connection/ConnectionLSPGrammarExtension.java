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

package org.finos.legend.engine.ide.lsp.extension.connection;

import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.engine.ide.lsp.extension.AbstractLegacyParserLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendReferenceResolver;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.state.GlobalState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.finos.legend.engine.language.pure.grammar.from.connection.ConnectionParser;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensionLoader;
import org.finos.legend.engine.language.pure.grammar.from.extension.PureGrammarParserExtensions;
import org.finos.legend.engine.protocol.pure.m3.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.Connection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.ConnectionPointer;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.ConnectionVisitor;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.connection.PackageableConnection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.modelToModel.connection.JsonModelConnection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.modelToModel.connection.ModelChainConnection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.modelToModel.connection.ModelConnection;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.modelToModel.connection.XmlModelConnection;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

/**
 * Extension for the Connection grammar.
 */
public class ConnectionLSPGrammarExtension extends AbstractLegacyParserLSPGrammarExtension
{
    private final ListIterable<String> keywords;

    public ConnectionLSPGrammarExtension()
    {
        super(ConnectionParser.newInstance(PureGrammarParserExtensions.fromAvailableExtensions()));
        this.keywords = findKeywords();
    }

    @Override
    public Iterable<? extends String> getKeywords()
    {
        return this.keywords;
    }

    @Override
    public Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        return this.computeCompletionsForSupportedTypes(section, location, this.keywords.toSet());
    }

    private static ListIterable<String> findKeywords()
    {
        MutableSet<String> keywords = Sets.mutable.empty();
        PureGrammarParserExtensionLoader.extensions().forEach(ext -> ext.getExtraConnectionParsers().forEach(p -> keywords.add(p.getConnectionTypeName())));
        return Lists.immutable.withAll(keywords);
    }

    @Override
    protected Stream<Optional<LegendReferenceResolver>> getReferenceResolvers(SectionState section, PackageableElement packageableElement, Optional<CoreInstance> coreInstance)
    {
        if (!(packageableElement instanceof PackageableConnection))
        {
            return Stream.empty();
        }

        return this.getConnectionReferences(((PackageableConnection) packageableElement).connectionValue, section.getDocumentState().getGlobalState());
    }

    public Stream<Optional<LegendReferenceResolver>> getConnectionReferences(Connection connection, GlobalState state)
    {
        Stream<Optional<LegendReferenceResolver>> connectionReferences = connection.accept(new ConnectionVisitor<>()
        {
            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(Connection connection)
            {
                return state.findGrammarExtensionThatImplements(ConnectionLSPGrammarProvider.class)
                        .flatMap(x -> x.getConnectionReferences(connection, state));
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(ConnectionPointer connectionPointer)
            {
                return Stream.of(LegendReferenceResolver.newReferenceResolver(connectionPointer.sourceInformation, c -> c.resolveConnection(connectionPointer.connection, connectionPointer.sourceInformation)));
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(ModelConnection modelConnection)
            {
                return Stream.empty();
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(JsonModelConnection jsonModelConnection)
            {
                return Stream.of(LegendReferenceResolver.newReferenceResolver(jsonModelConnection.classSourceInformation, c -> c.resolveClass(jsonModelConnection._class, jsonModelConnection.classSourceInformation)));
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(XmlModelConnection xmlModelConnection)
            {
                return Stream.of(LegendReferenceResolver.newReferenceResolver(xmlModelConnection.classSourceInformation, c -> c.resolveClass(xmlModelConnection._class, xmlModelConnection.classSourceInformation)));
            }

            @Override
            public Stream<Optional<LegendReferenceResolver>> visit(ModelChainConnection modelChainConnection)
            {
                // TODO: Refactor ModelChainConnection to contain List<PackageableElementPointer> in order to reference the mappings
                return Stream.empty();
            }
        });

        return connectionReferences;
    }
}
