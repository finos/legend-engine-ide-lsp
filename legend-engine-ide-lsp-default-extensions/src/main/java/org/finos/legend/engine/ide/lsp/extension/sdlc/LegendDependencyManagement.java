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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.finos.legend.engine.ide.lsp.extension.features.LegendVirtualFileSystemContentInitializer;
import org.finos.legend.sdlc.domain.model.entity.Entity;
import org.finos.legend.sdlc.protocol.pure.v1.PureEntitySerializer;
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

        StringWriter pureGrammarWriter = new StringWriter();
        pureGrammarWriter.write("// READ ONLY (sourced from workspace dependencies)\n\n");

        try (EntityLoader loader = EntityLoader.newEntityLoader(LegendDependencyManagement.class.getClassLoader()))
        {
            PureEntitySerializer serializer = new PureEntitySerializer();

            loader.getAllEntities().sorted(Comparator.comparing(Entity::getPath)).forEach(entity ->
            {
                try
                {
                    String grammar = serializer.serializeToString(entity);
                    if (!grammar.startsWith("###"))
                    {
                        grammar = "###Pure\n" + grammar;
                    }
                    pureGrammarWriter.write(grammar);
                }
                catch (Exception e)
                {
                    pureGrammarWriter.append("/* Failed to load grammar for dependency element: ").append(entity.getPath()).write("\n");
                    e.printStackTrace(new PrintWriter(pureGrammarWriter, true));
                    pureGrammarWriter.append("*/");
                }

                pureGrammarWriter.write("\n");
            });
        }
        catch (Exception e)
        {
            pureGrammarWriter.append("/* Failed to load dependencies").write("\n");
            e.printStackTrace(new PrintWriter(pureGrammarWriter, true));
            pureGrammarWriter.append("*/\n");
        }

        return List.of(LegendVirtualFileSystemContentInitializer.newVirtualFile(Path.of("dependencies.pure"), pureGrammarWriter.toString()));
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
