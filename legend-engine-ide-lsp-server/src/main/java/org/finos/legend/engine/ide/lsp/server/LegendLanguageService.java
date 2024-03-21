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

package org.finos.legend.engine.ide.lsp.server;

import java.time.Instant;
import java.util.Collections;
import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.features.LegendTDSRequestHandler;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.server.service.FunctionTDSRequest;
import org.finos.legend.engine.ide.lsp.server.service.LegendLanguageServiceContract;

public class LegendLanguageService implements LegendLanguageServiceContract
{
    private final LegendLanguageServer server;

    public LegendLanguageService(LegendLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public CompletableFuture<LegendExecutionResult> legendTDSRequest(FunctionTDSRequest request)
    {
        Instant start = Instant.now();
        return this.server.supplyPossiblyAsync(() ->
        {
            LegendExecutionResult result;
            LegendServerGlobalState globalState = this.server.getGlobalState();
            String uri = request.getUri();
            int sectionNum = request.getSectionNum();
            String entity = request.getEntity();
            DocumentState docState = globalState.getDocumentState(uri);
            if (docState == null)
            {
                this.server.logWarningToClient("Cannot get TDS request result for " + uri + ": not open in language server");
                return LegendExecutionResult.errorResult(new Exception("Cannot get TDS request result for " + uri + ": not open in language server"), "", entity, null);
            }

            try
            {
                LegendTDSRequestHandler handler = globalState.findFeatureThatImplements(LegendTDSRequestHandler.class).findAny().orElseThrow(() -> new RuntimeException("Could not execute legend TDS request for entity " + entity + " in section " + sectionNum + " of " + uri + ": no extension found"));
                SectionState sectionState = docState.getSectionState(sectionNum);
                result = handler.executeLegendTDSRequest(sectionState, entity, request.getRequest(), request.getInputParameters());
            }
            catch (Throwable e)
            {
                this.server.logInfoToClient(e.getMessage());
                String message = "TDS request execution for entity " + entity + " in section " + sectionNum + " of " + uri + " failed.";
                result = LegendExecutionResult.errorResult(new Exception(message, e), message, entity, null);
            }
            this.server.logInfoToClient(result.getMessage());
            return result;
        }).whenComplete((r, t) -> this.server.fireEvent("TDSRequest", start, Collections.emptyMap(), t));
    }

    @Override
    public CompletableFuture<String> replClasspath()
    {
        return this.server.supplyPossiblyAsync(() ->
        {
            String classpath = System.getProperty("java.class.path");
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader instanceof URLClassLoader)
            {
                URLClassLoader urlClassLoader = (URLClassLoader) contextClassLoader;

                if ("legend-lsp".equals(urlClassLoader.getName()))
                {
                    classpath = classpath + File.pathSeparator + Arrays.stream(urlClassLoader.getURLs())
                            .map(x ->
                            {
                                try
                                {
                                    return Path.of(x.toURI()).toAbsolutePath().toString();
                                }
                                catch (Exception e)
                                {
                                    throw new RuntimeException(e);
                                }
                            })
                            .collect(Collectors.joining(File.pathSeparator));
                }
            }

            return classpath;
        });
    }
}
