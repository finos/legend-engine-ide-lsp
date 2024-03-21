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

package org.finos.legend.engine.ide.lsp.server.integration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceDiagnosticParams;
import org.eclipse.lsp4j.WorkspaceDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceFullDocumentDiagnosticReport;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
// all tests should finish but in case of some uncaught deadlock, timeout whole test
public class TestLegendLanguageServerIntegration
{
    @RegisterExtension
    static LegendLanguageServerIntegrationExtension extension = new LegendLanguageServerIntegrationExtension();

    @Test
    void testUnknownGrammar() throws Exception
    {
        String content = "###HelloGrammar\n" +
                "Hello abc::abc\n" +
                "{\n" +
                "  abc: 1\n" +
                "}\n";

        Path pureFile = extension.addToWorkspace("hello.pure", content);

        DocumentDiagnosticReport diagnosticReport = extension.futureGet(extension.getServer().getTextDocumentService().diagnostic(new DocumentDiagnosticParams(new TextDocumentIdentifier(pureFile.toUri().toString()))));
        Assertions.assertNotNull(diagnosticReport.getRelatedFullDocumentDiagnosticReport());
        Assertions.assertEquals("full", diagnosticReport.getRelatedFullDocumentDiagnosticReport().getKind());
        Assertions.assertEquals(1, diagnosticReport.getRelatedFullDocumentDiagnosticReport().getItems().size());
        Diagnostic diagnostic = diagnosticReport.getRelatedFullDocumentDiagnosticReport().getItems().get(0);
        Assertions.assertEquals("Parser", diagnostic.getSource());
        Assertions.assertTrue(diagnostic.getMessage().startsWith("Unknown grammar: HelloGrammar"));
    }

    // repeat to test for race conditions, thread dead-locks, etc
    @RepeatedTest(value = 10, failureThreshold = 1)
    void testWorkspaceSymbols() throws Exception
    {
        extension.addToWorkspace("file1.pure", "###Pure\n" +
                "Class abc::abc\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n" +
                "Class abc::abc2\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n" +
                "Class abc::abc3\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        extension.addToWorkspace("file2.pure", "###Pure\n" +
                "Class xyz::abc\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n" +
                "Class xyz::abc2\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n" +
                "Class xyz::abc3\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        List<? extends WorkspaceSymbol> symbols = extension.futureGet(extension.getServer().getWorkspaceService().symbol(new WorkspaceSymbolParams(""))).getRight();
        Assertions.assertNotNull(symbols);

        Set<String> symbolNames = symbols.stream().map(WorkspaceSymbol::getName).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("abc::abc", "abc::abc2", "abc::abc3", "xyz::abc", "xyz::abc2", "xyz::abc3"), symbolNames);

        List<? extends WorkspaceSymbol> symbolsFiltered1 = extension.futureGet(extension.getServer().getWorkspaceService().symbol(new WorkspaceSymbolParams("xyz"))).getRight();
        Set<String> symbolNamesFiltered1 = symbolsFiltered1.stream().map(WorkspaceSymbol::getName).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("xyz::abc", "xyz::abc2", "xyz::abc3"), symbolNamesFiltered1);

        List<? extends WorkspaceSymbol> symbolsFiltered2 = extension.futureGet(extension.getServer().getWorkspaceService().symbol(new WorkspaceSymbolParams("abc2"))).getRight();
        Set<String> symbolNamesFiltered2 = symbolsFiltered2.stream().map(WorkspaceSymbol::getName).collect(Collectors.toSet());
        Assertions.assertEquals(Set.of("abc::abc2", "xyz::abc2"), symbolNamesFiltered2);
    }

    // repeat to test for race conditions, thread dead-locks, etc
    @RepeatedTest(value = 10, failureThreshold = 1)
    void testWorkspaceDiagnostic() throws Exception
    {
        // define class
        Path pureFile1 = extension.addToWorkspace("file1.pure", "###Pure\n" +
                "Class abc::abc\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        // extend class
        Path pureFile2 = extension.addToWorkspace("file2.pure", "###Pure\n" +
                "Class xyz::abc extends abc::abc\n" +
                "{\n" +
                "  xyz: String[1];\n" +
                "}\n");

        // no diagnostics
        List<WorkspaceDocumentDiagnosticReport> items = extension.futureGet(extension.getServer().getWorkspaceService().diagnostic(new WorkspaceDiagnosticParams(List.of()))).getItems();
        Assertions.assertEquals(
                Set.of(pureFile1.toUri().toString(), pureFile2.toUri().toString()),
                items.stream()
                        .map(WorkspaceDocumentDiagnosticReport::getWorkspaceFullDocumentDiagnosticReport)
                        .map(WorkspaceFullDocumentDiagnosticReport::getUri)
                        .collect(Collectors.toSet())
        );

        List<Diagnostic> diagnostics = items.stream()
                .map(WorkspaceDocumentDiagnosticReport::getWorkspaceFullDocumentDiagnosticReport)
                .map(WorkspaceFullDocumentDiagnosticReport::getItems)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        Assertions.assertTrue(diagnostics.isEmpty(), "Expected no diagnostic but got: " + diagnostics
                .stream()
                .map(Diagnostic::getMessage)
                .collect(Collectors.joining())
        );

        // rename extended class, should lead to compile failure
        extension.changeWorkspaceFile(pureFile1, "###Pure\n" +
                "Class abc::abcNewName\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        List<WorkspaceDocumentDiagnosticReport> itemsAfterChange = extension.futureGet(extension.getServer().getWorkspaceService().diagnostic(new WorkspaceDiagnosticParams(List.of()))).getItems();
        Assertions.assertEquals(2, itemsAfterChange.size());
        itemsAfterChange.sort(Comparator.comparing(x -> x.getWorkspaceFullDocumentDiagnosticReport().getUri()));

        WorkspaceFullDocumentDiagnosticReport diagnosticReport1 = itemsAfterChange.get(0).getWorkspaceFullDocumentDiagnosticReport();
        Assertions.assertNotNull(diagnosticReport1);
        Assertions.assertEquals(pureFile1.toUri().toString(), diagnosticReport1.getUri());
        Assertions.assertTrue(diagnosticReport1.getItems().isEmpty());

        WorkspaceFullDocumentDiagnosticReport diagnosticReport2 = itemsAfterChange.get(1).getWorkspaceFullDocumentDiagnosticReport();
        Assertions.assertNotNull(diagnosticReport2);
        Assertions.assertEquals(pureFile2.toUri().toString(), diagnosticReport2.getUri());
        Assertions.assertEquals(1, diagnosticReport2.getItems().size());
        Assertions.assertEquals("Compiler", diagnosticReport2.getItems().get(0).getSource());
        Assertions.assertEquals("Can't find type 'abc::abc'", diagnosticReport2.getItems().get(0).getMessage());

        // revert rename on extended class, should fix the compile failure
        extension.changeWorkspaceFile(pureFile1, "###Pure\n" +
                "Class abc::abc\n" +
                "{\n" +
                "  abc: String[1];\n" +
                "}\n");

        // no diagnostics
        List<WorkspaceDocumentDiagnosticReport> itemsAfterFix = extension.futureGet(extension.getServer().getWorkspaceService().diagnostic(new WorkspaceDiagnosticParams(List.of()))).getItems();
        List<Diagnostic> diagnosticsFixed = itemsAfterFix.stream()
                .map(WorkspaceDocumentDiagnosticReport::getWorkspaceFullDocumentDiagnosticReport)
                .map(WorkspaceFullDocumentDiagnosticReport::getItems)
                .flatMap(List::stream)
                .collect(Collectors.toList());
        Assertions.assertTrue(diagnosticsFixed.isEmpty());
    }

    @Test
    void testReplStartWithGivenClasspath() throws Exception
    {
        String classpath = extension.futureGet(extension.getServer().getLegendLanguageService().replClasspath());

        ProcessBuilder processBuilder = new ProcessBuilder(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "org.finos.legend.engine.ide.lsp.server.LegendREPLTerminal"
        );
        processBuilder.environment().put("CLASSPATH", classpath);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = null;
        try
        {
            process = processBuilder.start();
            Assertions.assertTrue(process.isAlive());
            read(process.getInputStream(), "Ready!");
        }
        finally
        {
            if (process != null)
            {
                process.destroy();
                process.onExit().join();
            }
        }
    }

    private static void read(InputStream replOutputConsole, String untilToken) throws IOException
    {
        StringBuilder output = new StringBuilder();
        while (!output.toString().contains(untilToken))
        {
            int read = replOutputConsole.read();
            if (read != -1)
            {
                System.err.print((char) read);
                output.append((char) read);
            }
            else
            {
                Assertions.fail("Did not found token and stream closed...");
            }

        }
    }
}
