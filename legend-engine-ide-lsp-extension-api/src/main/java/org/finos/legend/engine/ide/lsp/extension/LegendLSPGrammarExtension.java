// Copyright 2023 Goldman Sachs
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

package org.finos.legend.engine.ide.lsp.extension;

import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommand;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;

import java.util.Collections;
import java.util.Map;

/**
 * An LSP extension representing a Legend Engine top level grammar.
 */
public interface LegendLSPGrammarExtension extends LegendLSPExtension
{
    /**
     * Initialize the section state.
     *
     * @param section grammar section state
     */
    default void initialize(SectionState section)
    {
    }

    /**
     * Return the Legend declarations for the given section.
     *
     * @param section grammar section state
     * @return Legend declarations
     */
    default Iterable<? extends LegendDeclaration> getDeclarations(SectionState section)
    {
        return Collections.emptyList();
    }

    /**
     * Return the Legend diagnostics for the given section.
     *
     * @param section grammar section state
     * @return Legend diagnostics
     */
    default Iterable<? extends LegendDiagnostic> getDiagnostics(SectionState section)
    {
        return Collections.emptyList();
    }

    default LegendCompletion getCompletions(String completionTrigger)
    {
        return new LegendCompletion("");
    }

    /**
     * Return the Legend commands for the given section.
     *
     * @param section grammar section state
     * @return Legend commands
     */
    default Iterable<? extends LegendCommand> getCommands(SectionState section)
    {
        return Collections.emptyList();
    }

    /**
     * Execute a Legend command on an entity in a section.
     *
     * @param section    grammar section state
     * @param entityPath entity path
     * @param commandId  command id
     * @return execution results
     */
    default Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs)
    {
        return Collections.emptyList();
    }
}
