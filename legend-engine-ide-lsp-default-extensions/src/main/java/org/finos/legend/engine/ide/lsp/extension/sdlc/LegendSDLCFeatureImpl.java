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

package org.finos.legend.engine.ide.lsp.extension.sdlc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.collections.api.block.function.Function0;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.block.factory.Comparators;
import org.eclipse.collections.impl.set.sorted.mutable.TreeSortedSet;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.engine.ide.lsp.extension.LegendEntity;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.features.LegendSDLCFeature;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposer;
import org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposerContext;
import org.finos.legend.engine.protocol.functionActivator.metamodel.FunctionActivator;
import org.finos.legend.engine.protocol.pure.v1.ProtocolToClassifierPathLoader;
import org.finos.legend.engine.protocol.pure.v1.extension.PureProtocolExtensionLoader;
import org.finos.legend.engine.protocol.pure.v1.model.context.PureModelContextData;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.store.Store;
import org.finos.legend.engine.shared.core.ObjectMapperFactory;
import org.finos.legend.engine.shared.core.api.grammar.RenderStyle;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.protocol.pure.v1.PureEntitySerializer;
import org.finos.legend.sdlc.serialization.DefaultJsonEntitySerializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

public class LegendSDLCFeatureImpl implements LegendSDLCFeature
{
    private final PureEntitySerializer pureEntitySerializer = new PureEntitySerializer();
    private final DefaultJsonEntitySerializer jsonEntitySerializer = new DefaultJsonEntitySerializer();
    private final  ObjectMapper objectMapper = ObjectMapperFactory.getNewStandardObjectMapperWithPureProtocolExtensionSupports();
    private final PureGrammarComposer pureGrammarComposer = PureGrammarComposer.newInstance(PureGrammarComposerContext.Builder.newInstance().withRenderStyle(RenderStyle.PRETTY).build());

    @Override
    public String entityJsonToPureText(String entityJson)
    {
        try
        {
            Entity entity = this.jsonEntitySerializer.deserialize(entityJson);
            return this.pureEntitySerializer.serializeToString(entity);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Map<Path, String> convertToOneElementPerFile(Path rootFolder, DocumentState documentState)
    {
        Map<Path, String> fileToContentMap = new HashMap<>();

        Comparator<LegendEntity> comparator = Comparators.<LegendEntity, String>byFunction(x -> x.getLocation().getDocumentId())
                .thenComparing(x -> x.getLocation().getTextInterval().getStart());

        TreeSortedSet<LegendEntity> legendEntitiesSorted = TreeSortedSet.newSetWith(comparator);

        documentState.forEachSectionState(x ->
        {
            x.getExtension().getDiagnostics(x).forEach(d ->
            {
                if (d.getSource().equals(LegendDiagnostic.Source.Parser) && d.getKind().equals(LegendDiagnostic.Kind.Error))
                {
                    throw new IllegalStateException("Unable to refactor document " + documentState.getDocumentId() + " given the parser errors (ie. " + d.getMessage() + ").  Fix and try again!");
                }
            });
            x.getExtension().getEntities(x).forEach(legendEntitiesSorted::add);
        });

        Iterator<LegendEntity> iterator = legendEntitiesSorted.iterator();
        LegendEntity prevEntity = null;

        while (iterator.hasNext())
        {
            LegendEntity entity = iterator.next();
            int startLine;
            if (prevEntity == null)
            {
                startLine = 0;
            }
            else
            {
                startLine = prevEntity.getLocation().getTextInterval().getEnd().getLine() + 1;
            }

            if (prevEntity != null && entity.getLocation().getTextInterval().getStart().getLine() < startLine)
            {
                throw new UnsupportedOperationException("Refactoring elements that are defined on the same line not supported.  Element '" + entity.getPath() + "' defined next to previous element '" + prevEntity.getPath() + "'");
            }

            SectionState sectionState = documentState.getSectionStateAtLine(entity.getLocation().getTextInterval().getStart().getLine());
            LegendLSPGrammarExtension extension = sectionState.getExtension();

            int endLine = iterator.hasNext() ? entity.getLocation().getTextInterval().getEnd().getLine() : documentState.getLineCount() - 1;

            String entityContent = documentState
                    .getLines(startLine, endLine)
                    .replace("###Pure", "")
                    .trim();

            if (!extension.getName().equals("Pure") && !entityContent.contains("###" + extension.getName()))
            {
                entityContent = "###" + extension.getName() + "\n" + entityContent;
            }

            Path path = this.pureEntitySerializer.filePathForEntity(
                    Entity.newEntity(entity.getPath(), entity.getClassifierPath(), entity.getContent()),
                    rootFolder
            );

            fileToContentMap.put(path, entityContent);

            prevEntity = entity;
        }

        return fileToContentMap;
    }

    @Override
    public Map.Entry<String, String> contentToPureText(Map<String, ?> content)
    {
        PackageableElement packageableElement = this.objectMapper.convertValue(content, PackageableElement.class);
        String pureText = this.pureGrammarComposer.renderPureModelContextData(PureModelContextData.newBuilder().withElement(packageableElement).build());
        if (pureText.startsWith("###"))
        {
            pureText = pureText.substring(pureText.indexOf('\n') + 1);
        }
        return Pair.of(packageableElement.getPath(), pureText.strip());
    }

    @Override
    public String getClassifierPathMap()
    {
        Map<Class<? extends PackageableElement>, String> classifierPathMap = ProtocolToClassifierPathLoader.getProtocolClassToClassifierMap();
        Map<String, String> result = Maps.mutable.empty();
        PureProtocolExtensionLoader.extensions().forEach(extension ->
                LazyIterate.flatCollect(extension.getExtraProtocolSubTypeInfoCollectors(), Function0::value).forEach(info ->
                {
                    info.getSubTypes().forEach(subType ->
                    {
                        if (PackageableElement.class.isAssignableFrom(subType.getOne()) && classifierPathMap.containsKey(subType.getOne()))
                        {
                            if (result.containsKey(subType.getTwo()))
                            {
                                // ignore duplications
                                return;
                            }
                            result.put(subType.getTwo(), classifierPathMap.get(subType.getOne()));
                        }
                    });
                }));
        return "[" +
                result.entrySet().stream()
                        .map(entry -> "{\"type\":\"" + entry.getKey() + "\",\"classifierPath\":\"" + entry.getValue() + "\"}")
                        .collect(Collectors.joining(",")) +
                "]";
    }

    @Override
    public SubtypeInfoResult getSubtypeInfo()
    {
        SubtypeInfoResult result = new SubtypeInfoResult();
        MutableSet<String> storeSubtypes = Sets.mutable.empty();
        PureProtocolExtensionLoader.extensions().forEach(extension ->
                LazyIterate.flatCollect(extension.getExtraProtocolSubTypeInfoCollectors(), Function0::value).forEach(info ->
                {
                    info.getSubTypes().forEach(subType ->
                    {
                        if (Store.class.isAssignableFrom(subType.getOne()))
                        {
                            if (storeSubtypes.contains(subType.getTwo()))
                            {
                                // ignore duplications
                                return;
                            }
                            storeSubtypes.add(subType.getTwo());
                        }
                    });
                }));
        result.storeSubtypes = storeSubtypes.toList();

        MutableSet<String> functionActivatorSubtypes = Sets.mutable.empty();
        PureProtocolExtensionLoader.extensions().forEach(extension ->
                LazyIterate.flatCollect(extension.getExtraProtocolSubTypeInfoCollectors(), Function0::value).forEach(info ->
                {
                    info.getSubTypes().forEach(subType ->
                    {
                        if (FunctionActivator.class.isAssignableFrom(subType.getOne()))
                        {
                            if (functionActivatorSubtypes.contains(subType.getTwo()))
                            {
                                // ignore duplications
                                return;
                            }
                            functionActivatorSubtypes.add(subType.getTwo());
                        }
                    });
                }));
        result.functionActivatorSubtypes = functionActivatorSubtypes.toList();
        return result;
    }
}
