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

package org.finos.legend.engine.ide.lsp.extension.diagram;

import org.finos.legend.engine.ide.lsp.extension.AbstractSectionParserLSPGrammarExtension;
import org.finos.legend.engine.ide.lsp.extension.CommandConsumer;
import org.finos.legend.engine.ide.lsp.extension.execution.LegendCommandType;
import org.finos.legend.engine.ide.lsp.extension.state.SectionState;
import org.finos.legend.engine.language.pure.grammar.from.DiagramParserExtension;
import org.finos.legend.engine.protocol.pure.v1.model.PackageableElement;
import org.finos.legend.engine.protocol.pure.v1.model.packageableElement.diagram.Diagram;

public class DiagramLSPGrammarExtension extends AbstractSectionParserLSPGrammarExtension
{

    private static final String EDIT_DIAGRAM_COMMAND_ID = "legend.show.diagram";
    private static final String EDIT_DIAGRAM_COMMAND_TITLE = "View/Edit Diagram";

    public DiagramLSPGrammarExtension()
    {
        super(DiagramParserExtension.NAME, new DiagramParserExtension());
    }

    @Override
    protected void collectCommands(SectionState sectionState, PackageableElement element, CommandConsumer consumer)
    {
        super.collectCommands(sectionState, element, consumer);
        if (element instanceof Diagram)
        {
            Diagram diagram = (Diagram) element;
            consumer.accept(EDIT_DIAGRAM_COMMAND_ID, EDIT_DIAGRAM_COMMAND_TITLE, diagram.sourceInformation, LegendCommandType.CLIENT);
        }
    }
}
