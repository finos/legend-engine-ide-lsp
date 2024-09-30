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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;
import org.finos.legend.engine.ide.lsp.extension.LegendEntity;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.DocumentState;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTest;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestExecutionResult;
import org.finos.legend.engine.ide.lsp.server.request.LegendEntitiesRequest;
import org.finos.legend.engine.ide.lsp.server.request.LegendJsonToPureRequest;
import org.finos.legend.engine.ide.lsp.server.request.LegendWriteEntityRequest;

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

    @JsonRequest("entities")
    CompletableFuture<List<LegendEntity>> entities(LegendEntitiesRequest request);

    @JsonRequest("jsonToPure")
    CompletableFuture<ApplyWorkspaceEditResponse> jsonEntitiesToPureTextWorkspaceEdits(LegendJsonToPureRequest request);

    /**
     * Traverse all the documents, and propose workspace edits to convert to one element per file.
     * The process will skip files that already contain a single entity and the file name match expected name.
     * As result of this, multiple files could be deleted, created, and updated, but all the document content on these
     * will still be preserved, including formatting and comments.
     * <p>
     * For more details on the conversion, see {@link org.finos.legend.engine.ide.lsp.extension.features.LegendSDLCFeature#convertToOneElementPerFile(Path, DocumentState)}
     * <p>
     * @return A completable future that will finish once the refactoring is completes or fails.
     */
    @JsonRequest("oneEntityPerFileRefactoring")
    CompletableFuture<Void> oneEntityPerFileRefactoring();

    @JsonRequest("writeEntity")
    CompletableFuture<Void> writeEntity(LegendWriteEntityRequest request);

    @JsonRequest("getClassifierPathMap")
    CompletableFuture<String> getClassifierPathMap();
}
