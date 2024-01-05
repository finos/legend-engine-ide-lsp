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
import java.util.function.Supplier;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarLibrary;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPInlineDSLExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPInlineDSLLibrary;

class ExtensionsGuard
{
    private final Iterable<LegendLSPGrammarExtension> providedGrammarExtensions;
    private final Iterable<LegendLSPInlineDSLExtension> providedInlineDSLs;
    private final LegendLanguageServer server;
    private volatile ClassLoader classLoader;
    private volatile LegendLSPGrammarLibrary grammars;
    private volatile LegendLSPInlineDSLLibrary inlineDSLs;

    public ExtensionsGuard(LegendLanguageServer server, LegendLSPGrammarLibrary providedGrammarExtensions, LegendLSPInlineDSLLibrary providedInlineDSLs)
    {
        this.server = server;
        this.grammars = providedGrammarExtensions;
        this.inlineDSLs = providedInlineDSLs;
        this.providedGrammarExtensions = providedGrammarExtensions.getExtensions();
        this.providedInlineDSLs = providedInlineDSLs.getExtensions();
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

            this.grammars = LegendLSPGrammarLibrary.builder().withExtensions(this.providedGrammarExtensions).withExtensionsFrom(classLoader).build();
            this.inlineDSLs = LegendLSPInlineDSLLibrary.builder().withExtensions(this.providedInlineDSLs).withExtensionsFrom(classLoader).build();

            this.server.logInfoToClient("Grammar extensions available: " + String.join(", ", this.grammars.getExtensionNames()));
            this.server.logInfoToClient("Inline DSL extensions available: " + String.join(", ", this.inlineDSLs.getExtensionNames()));

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

    public LegendLSPInlineDSLLibrary getInlineDSLs()
    {
        return this.inlineDSLs;
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
}
