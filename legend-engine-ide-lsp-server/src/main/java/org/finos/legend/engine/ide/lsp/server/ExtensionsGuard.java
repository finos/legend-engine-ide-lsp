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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPGrammarLibrary;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPInlineDSLExtension;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPInlineDSLLibrary;

class ExtensionsGuard
{
    private final Executor executor;
    private final boolean async;
    private final Iterable<LegendLSPGrammarExtension> providedGrammarExtensions;
    private final Iterable<LegendLSPInlineDSLExtension> providedInlineDSLs;
    private ClassLoader classLoader;
    private LegendLSPGrammarLibrary grammars;
    private LegendLSPInlineDSLLibrary inlineDSLs;

    public ExtensionsGuard(boolean async, Executor executor, LegendLSPGrammarLibrary providedGrammarExtensions, LegendLSPInlineDSLLibrary providedInlineDSLs)
    {
        this.async = async;
        this.executor = executor;
        this.providedGrammarExtensions = providedGrammarExtensions.getExtensions();
        this.providedInlineDSLs = providedInlineDSLs.getExtensions();
    }

    public void init(ClassLoader classLoader)
    {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        try
        {
            if (this.classLoader != null && this.classLoader instanceof Closeable)
            {
                ((Closeable) this.classLoader).close();
            }

            this.classLoader = classLoader;

            Thread.currentThread().setContextClassLoader(classLoader);

            this.grammars = LegendLSPGrammarLibrary.builder().withExtensions(this.providedGrammarExtensions).withExtensionsFrom(classLoader).build();
            this.inlineDSLs = LegendLSPInlineDSLLibrary.builder().withExtensions(this.providedInlineDSLs).withExtensionsFrom(classLoader).build();
        }
        catch (Throwable e)
        {
            throw new RuntimeException(e);
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

    <T> CompletableFuture<T> supplyPossiblyAsync_internal(Supplier<T> work)
    {
        Supplier<T> supplier = this.wrapOnClasspath(work);

        if (this.async)
        {
            return (this.executor == null) ?
                    CompletableFuture.supplyAsync(supplier) :
                    CompletableFuture.supplyAsync(supplier, this.executor);
        }

        return CompletableFuture.completedFuture(supplier.get());
    }

    CompletableFuture<?> runPossiblyAsync_internal(Runnable work)
    {
        Runnable runnable = this.wrapOnClasspath(work);

        if (this.async)
        {
            return (this.executor == null) ?
                    CompletableFuture.runAsync(runnable) :
                    CompletableFuture.runAsync(runnable, this.executor);
        }

        runnable.run();
        return CompletableFuture.completedFuture(null);
    }

    private Runnable wrapOnClasspath(Runnable command)
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

    private <T> Supplier<T> wrapOnClasspath(Supplier<T> command)
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
