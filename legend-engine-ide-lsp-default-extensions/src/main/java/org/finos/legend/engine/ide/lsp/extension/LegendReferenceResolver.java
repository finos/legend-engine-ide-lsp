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

package org.finos.legend.engine.ide.lsp.extension;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.Locatable;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.CompileContext;
import org.finos.legend.engine.language.pure.compiler.toPureGraph.SourceInformationHelper;
import org.finos.legend.engine.protocol.pure.m3.SourceInformation;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegendReferenceResolver implements Locatable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendReferenceResolver.class);

    private final TextLocation location;
    private final Function<CompileContext, CoreInstance> gotoResolver;

    private LegendReferenceResolver(TextLocation location, Function<CompileContext, CoreInstance> gotoResolver)
    {
        this.location = Objects.requireNonNull(location);
        this.gotoResolver = Objects.requireNonNull(gotoResolver);
    }

    public static Stream<LegendReference> toLegendReference(SectionState sectionState, Stream<Optional<LegendReferenceResolver>> referenceResolvers, CompileContext compileContext)
    {
        return referenceResolvers.filter(Optional::isPresent)
            .map(Optional::get)
            .map(reference ->
            {
                Optional<CoreInstance> referenced = reference.goToReferenced(compileContext);
                return referenced.map(x ->
                {
                    SourceInformation sourceInfo = SourceInformationHelper.fromM3SourceInformation(x.getSourceInformation());
                    if (AbstractLSPGrammarExtension.isValidSourceIdAndSourceInfo(sectionState, sourceInfo))
                    {
                        TextLocation declarationLocation = SourceInformationUtil.toLocation(sourceInfo);
                        if (!reference.getLocation().equals(declarationLocation))
                        {
                            return LegendReference.builder()
                                    .withLocation(reference.getLocation())
                                    .withDeclarationLocation(declarationLocation)
                                    .build();
                        }
                    }

                    return null;
                });
            })
            .filter(Optional::isPresent)
            .map(Optional::get);
    }

    @Override
    public TextLocation getLocation()
    {
        return location;
    }

    public Optional<CoreInstance> goToReferenced(CompileContext compileContext)
    {
        try
        {
            return Optional.ofNullable(this.gotoResolver.apply(compileContext));
        }
        catch (Exception e)
        {
            LOGGER.warn("Error resolving reference at {}", this.location, e);
            return Optional.empty();
        }
    }

    public static Optional<LegendReferenceResolver> newReferenceResolver(SourceInformation location, Function<CompileContext, CoreInstance> gotoResolver)
    {
        if (!SourceInformationUtil.isValidSourceInfo(location))
        {
            return Optional.empty();
        }

        return Optional.of(new LegendReferenceResolver(SourceInformationUtil.toLocation(location), gotoResolver));
    }

    public static Optional<LegendReferenceResolver> newReferenceResolver(org.finos.legend.pure.m4.coreinstance.SourceInformation location, CoreInstance coreInstance)
    {
        return LegendReferenceResolver.newReferenceResolver(SourceInformationHelper.fromM3SourceInformation(location), x -> coreInstance);
    }

    public static Optional<LegendReferenceResolver> newReferenceResolver(SourceInformation location, CoreInstance coreInstance)
    {
        return LegendReferenceResolver.newReferenceResolver(location, x -> coreInstance);
    }
}
