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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.finos.legend.engine.ide.lsp.extension.completion.LegendCompletion;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommand;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTest;
import org.finos.legend.engine.ide.lsp.extension.test.LegendTestExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.text.TextLocation;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;

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

    /**
     * Return the completion suggestion base on section and location
     * @param section grammar section state where completion triggered
     * @param location location where completion triggered
     * @return Completion suggestion contextual to section and location
     */
    default Iterable<? extends LegendCompletion> getCompletions(SectionState section, TextPosition location)
    {
        return Collections.emptyList();
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
     * @param executableArgs executable Arguments
     * @return execution results
     */
    default Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs)
    {
        return Collections.emptyList();
    }

    /**
     * If the given text position refers to another element, this returns the location where the reference (needs to include the text position),
     * and the location where the referenced is defined, enabling navigation from one to the other. If a reference cannot be resolved, an empty optional should be returned.
     *
     * @param sectionState grammar section state
     * @param textPosition the position to evaluate if a reference exists
     * @return Optional with the reference, if one exists, empty otherwise
     */
    default Optional<LegendReference> getLegendReference(SectionState sectionState, TextPosition textPosition)
    {
        return Optional.empty();
    }
    
    /**
     * Execute a Legend command on an entity in a section.
     *
     * @param section    grammar section state
     * @param entityPath entity path
     * @param commandId  command id
     * @param executableArgs executable Arguments
     * @param inputParameters input Parameters
     * @return execution results
     */
    default Iterable<? extends LegendExecutionResult> execute(SectionState section, String entityPath, String commandId, Map<String, String> executableArgs, Map<String, Object> inputParameters)
    {
        return Collections.emptyList();
    }

    default List<LegendTest> testCases(SectionState section)
    {
        return Collections.emptyList();
    }

    default List<LegendTestExecutionResult> executeTests(SectionState section, TextLocation location, String testId, Set<String> excludedTestIds)
    {
        return Collections.emptyList();
    }
}
