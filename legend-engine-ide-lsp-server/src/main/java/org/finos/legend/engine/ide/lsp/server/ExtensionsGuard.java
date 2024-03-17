// Copyright 2024 Goldman Sachs
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

package org.finos.legend.engine.ide.lsp.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPExtensionLoader;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPFeature;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ExtensionsGuard
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionsGuard.class);
    private final Iterable<LegendLSPGrammarExtension> providedGrammarExtensions;
    private final LegendLanguageServer server;
    private volatile ClassLoader classLoader;
    private volatile LegendLSPGrammarLibrary grammars;

    private volatile Collection<LegendLSPFeature> features;

    public ExtensionsGuard(LegendLanguageServer server, LegendLSPGrammarLibrary providedGrammarExtensions)
    {
        this.server = server;
        this.grammars = providedGrammarExtensions;
        this.providedGrammarExtensions = providedGrammarExtensions.getExtensions();
    }

    public synchronized void initialize(ClassLoader classLoader)
    {
        this.server.logInfoToClient("Initializing grammar extensions");

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        try
        {
            if (this.classLoader != null && this.classLoader instanceof Closeable)
            {
                ((Closeable) this.classLoader).close();
            }

            Thread.currentThread().setContextClassLoader(classLoader);

            List<LegendLSPFeature> features = new ArrayList<>();
            List<LegendLSPGrammarExtension> grammars = new ArrayList<>();

            ServiceLoader.load(LegendLSPExtensionLoader.class, classLoader).forEach(x ->
            {
                LOGGER.debug("Loading extensions using: {}", x.getClass());
                x.loadLegendLSPGrammarExtensions(classLoader).forEach(grammars::add);
                x.loadLegendLSPFeatureExtensions(classLoader).forEach(features::add);
            });

            this.grammars = LegendLSPGrammarLibrary.builder().withExtensions(this.providedGrammarExtensions).withExtensions(grammars).build();
            this.features = Collections.unmodifiableList(features);
            this.server.logInfoToClient("Grammar extensions available: " + String.join(", ", this.grammars.getExtensionNames()));
            this.server.logInfoToClient("Feature extensions available: " + this.features.stream().map(LegendLSPFeature::description).collect(Collectors.joining(", ")));

            this.classLoader = classLoader;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    public LegendLSPGrammarLibrary getGrammars()
    {
        return this.grammars;
    }

    Runnable wrapOnClasspath(Runnable command)
    {
        return () ->
        {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            try
            {
                Thread.currentThread().setContextClassLoader(this.classLoader);
                command.run();
            }
            finally
            {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
        };
    }

    <T> Supplier<T> wrapOnClasspath(Supplier<T> command)
    {
        return () ->
        {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            try
            {
                Thread.currentThread().setContextClassLoader(this.classLoader);
                return command.get();
            }
            finally
            {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
        };
    }

    public Collection<LegendLSPFeature> getFeatures()
    {
        return this.features;
    }
}
