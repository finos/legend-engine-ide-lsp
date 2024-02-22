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

package org.finos.legend.engine.ide.lsp.utils;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.finos.legend.engine.ide.lsp.extension.diagnostic.LegendDiagnostic;
import org.finos.legend.engine.ide.lsp.extension.text.TextInterval;

public final class LegendToLSPUtilities
{
    private LegendToLSPUtilities()
    {

    }

    public static Range toRange(TextInterval interval)
    {
        // Range end position is exclusive, whereas TextInterval end is inclusive
        return newRange(interval.getStart().getLine(), interval.getStart().getColumn(),
                interval.getEnd().getLine(), interval.getEnd().getColumn() + 1);
    }

    public static Range newRange(int startLine, int startCol, int endLine, int endCol)
    {
        return new Range(new Position(startLine, startCol), new Position(endLine, endCol));
    }

    public static Diagnostic toDiagnostic(LegendDiagnostic diagnostic)
    {
        return new Diagnostic(LegendToLSPUtilities.toRange(diagnostic.getLocation().getTextInterval()), diagnostic.getMessage(), toDiagnosticSeverity(diagnostic.getKind()), diagnostic.getSource().toString());
    }

    private static DiagnosticSeverity toDiagnosticSeverity(LegendDiagnostic.Kind kind)
    {
        switch (kind)
        {
            case Warning:
            {
                return DiagnosticSeverity.Warning;
            }
            case Information:
            {
                return DiagnosticSeverity.Information;
            }
            case Hint:
            {
                return DiagnosticSeverity.Hint;
            }
            case Error:
            {
                return DiagnosticSeverity.Error;
            }
            default:
            {
                return DiagnosticSeverity.Error;
            }
        }
    }
}
