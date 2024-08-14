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

import java.lang.reflect.ParameterizedType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.engine.ide.lsp.extension.declaration.LegendDeclaration;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendExecutionResult;
import org.finos.legend.engine.ide.lsp.extension.reference.LegendReference;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.ide.lsp.extension.text.TextPosition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class AbstractLSPGrammarExtensionTest<T extends LegendLSPGrammarExtension>
{
    public static final String DOC_ID_FOR_TEXT = "file.pure";
    private static StateForTestFactory stateForTestFactory;
    protected T extension;

    @BeforeEach
    public void loadExtensionToUse()
    {
        Class<? extends LegendLSPGrammarExtension> tType = (Class<? extends LegendLSPGrammarExtension>) ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        List<LegendLSPGrammarExtension> extensions = stateForTestFactory.getLegendLSPGrammarExtensions().stream().filter(tType::isInstance).collect(Collectors.toList());
        Assertions.assertEquals(1, extensions.size(), "Expect single extension for given DSL");
        this.extension = (T) extensions.get(0);
    }

    @BeforeAll
    static void beforeAll()
    {
        stateForTestFactory = new StateForTestFactory();
    }

    @Test
    public void testDiagnostics_emptySection()
    {
        String code = "###" + this.extension.getName();
        testDiagnostics(code);
    }

    @Test
    public void testDiagnostics_emptyFile()
    {
        testDiagnostics("");
    }

    protected void testGetName(String expectedName)
    {
        Assertions.assertEquals(expectedName, this.extension.getName());
    }

    protected void testGetDeclarations(String code, LegendDeclaration... expectedDeclarations)
    {
        Comparator<LegendDeclaration> cmp = Comparator.comparing(x -> x.getLocation().getTextInterval().getStart());
        MutableList<LegendDeclaration> expected = Lists.mutable.with(expectedDeclarations).sortThis(cmp);
        MutableList<LegendDeclaration> actual = Lists.mutable.<LegendDeclaration>withAll(this.extension.getDeclarations(stateForTestFactory.newSectionState(DOC_ID_FOR_TEXT, code))).sortThis(cmp);
        Assertions.assertEquals(expected, actual);
    }

    protected void testDiagnostics(String code, LegendDiagnostic... expectedDiagnostics)
    {
        Comparator<LegendDiagnostic> cmp = Comparator.comparing(x -> x.getLocation().getTextInterval().getStart());
        MutableList<LegendDiagnostic> expected = Lists.mutable.with(expectedDiagnostics).sortThis(cmp);
        MutableList<LegendDiagnostic> actual = Lists.mutable.<LegendDiagnostic>withAll(this.extension.getDiagnostics(stateForTestFactory.newSectionState(DOC_ID_FOR_TEXT, code))).sortThis(cmp);
        Assertions.assertEquals(expected, actual);
    }

    protected void testDiagnostics(MutableMap<String, String> files, String expectedDocId, LegendDiagnostic... expectedDiagnostics)
    {
        Comparator<LegendDiagnostic> cmp = Comparator.comparing(x -> x.getLocation().getTextInterval().getStart());
        MutableList<LegendDiagnostic> expected = Lists.mutable.with(expectedDiagnostics).sortThis(cmp);
        MutableList<SectionState> sectionStates = stateForTestFactory.newSectionStates(files);
        SectionState inputSectionState = sectionStates.detect(s -> expectedDocId.equals(s.getDocumentState().getDocumentId()));
        MutableList<LegendDiagnostic> actual = Lists.mutable.<LegendDiagnostic>withAll(this.extension.getDiagnostics(inputSectionState)).sortThis(cmp);
        Assertions.assertEquals(expected, actual);
    }

    protected void testReferenceLookup(MutableMap<String, String> files, String docId, TextPosition position, LegendReference expected, String assertMessage)
    {
        MutableList<SectionState> sectionStates = stateForTestFactory.newSectionStates(files);
        SectionState inputSectionState = sectionStates.detect(s -> docId.equals(s.getDocumentState().getDocumentId()));
        Optional<LegendReference> actual = inputSectionState.getExtension().getLegendReference(inputSectionState, position);
        Assertions.assertEquals(Optional.ofNullable(expected), actual, assertMessage);
    }

    protected Iterable<? extends LegendExecutionResult> testCommand(String code, String entityPath, String command)
    {
        SectionState sectionState = stateForTestFactory.newSectionState(DOC_ID_FOR_TEXT, code);
        return this.extension.execute(sectionState, entityPath, command, Maps.fixedSize.empty());
    }

    protected SectionState newSectionState(String docId, String text)
    {
        return stateForTestFactory.newSectionState(docId, text);
    }

    protected MutableList<SectionState> newSectionStates(MutableMap<String, String> files)
    {
        return stateForTestFactory.newSectionStates(files);
    }

}
