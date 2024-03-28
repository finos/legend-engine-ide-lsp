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

import java.nio.file.Path;
import java.util.List;
import org.finos.legend.engine.ide.lsp.extension.features.LegendVirtualFileSystemContentInitializer;
import org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposer;
import org.finos.legend.engine.language.pure.grammar.to.PureGrammarComposerContext;
import org.finos.legend.engine.shared.core.api.grammar.RenderStyle;
import org.finos.legend.sdlc.protocol.pure.v1.PureModelContextDataBuilder;
import org.finos.legend.sdlc.serialization.EntityLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LegendDependencyManagement implements LegendVirtualFileSystemContentInitializer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LegendDependencyManagement.class);
    private final List<LegendVirtualFile> dependenciesVirtualFiles;

    public LegendDependencyManagement()
    {
        this.dependenciesVirtualFiles = loadDependenciesVirtualFiles();
    }

    private static List<LegendVirtualFile> loadDependenciesVirtualFiles()
    {
        LOGGER.info("Processing dependencies from classpath");
        try (EntityLoader loader = EntityLoader.newEntityLoader(LegendDependencyManagement.class.getClassLoader()))
        {
            PureModelContextDataBuilder fromClasspathBuilder = PureModelContextDataBuilder.newBuilder();
            fromClasspathBuilder.addEntitiesIfPossible(loader.getAllEntities());
            PureGrammarComposer pureComposer = PureGrammarComposer.newInstance(PureGrammarComposerContext.Builder.newInstance()
                    .withRenderStyle(RenderStyle.PRETTY)
                    .build());
            String pureGrammar = pureComposer.renderPureModelContextData(fromClasspathBuilder.build());
            return List.of(
                    LegendVirtualFileSystemContentInitializer.newVirtualFile(
                            Path.of("dependencies.pure"),
                            String.format("// READ ONLY (sourced from workspace dependencies)%n%n") + pureGrammar
                    )
            );
        }
        catch (Exception e)
        {
            LOGGER.warn("Failed to load dependencies from classpath", e);
            return List.of();
        }
    }

    @Override
    public String description()
    {
        return "Handles Legend SDLC features";
    }

    @Override
    public List<LegendVirtualFile> getVirtualFilePureGrammars()
    {
        return this.dependenciesVirtualFiles;
    }
}
