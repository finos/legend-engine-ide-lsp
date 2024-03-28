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

package org.finos.legend.engine.ide.lsp.server.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTest;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestExecutionResult;

@JsonSegment("legend")
public interface LegendLanguageServiceContract
{
    @JsonRequest("TDSRequest")
    CompletableFuture<LegendExecutionResult> legendTDSRequest(FunctionTDSRequest rq);

    @JsonRequest("replClasspath")
    CompletableFuture<String> replClasspath();

    @JsonRequest("testCases")
    CompletableFuture<List<LegendTest>> testCases();

    @JsonRequest("executeTests")
    CompletableFuture<List<LegendTestExecutionResult>> executeTests(ExecuteTestRequest rq);

    @JsonRequest("legendVirtualFile")
    CompletableFuture<String> loadLegendVirtualFile(String uri);

}
