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

package org.finos.legend.engine.ide.lsp.text;

import org.finos.legend.engine.ide.lsp.extension.text.GrammarSection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestGrammarSectionIndex
{
    @Test
    public void testBlank()
    {
        assertParses("", section("Pure", 0, 0));
        assertParses("     ", section("Pure", 0, 0));
        assertParses("\n\r\n\t     \n", section("Pure", 0, 3));
    }

    @Test
    public void testSingleEmptySection()
    {
        assertParses("###Pure", section("Pure", 0, 0));
        assertParses("\n###Pure", section("Pure", 1, 1));

        assertParses("###Mapping\n", section("Mapping", 0, 1));
        assertParses("###Mapping\r\n", section("Mapping", 0, 1));

        assertParses("###Relational\r\n", section("Relational", 0, 1));
        assertParses("\r\n\t\r\n   ###Relational\r\n\r\n", section("Relational", 2, 4));
    }

    @Test
    public void testImplicitPureSection()
    {
        assertParses(
                "Class model::domain::MyClass {}",
                section("Pure", 0, 0));
        assertParses(
                "Class model::domain::MyClass {}\n",
                section("Pure", 0, 1));
        assertParses(
                "Class model::domain::MyClass\n" +
                        "{\n" +
                        "    name:String[1]\n" +
                        "}",
                section("Pure", 0, 3));
        assertParses(
                "Class model::domain::MyClass\n" +
                        "{\n" +
                        "    name:String[1]\n" +
                        "}\n",
                section("Pure", 0, 4));
        assertParses(
                "Class model::domain::MyClass\r\n" +
                        "{\r\n" +
                        "    name:String[1]\r\n" +
                        "}\r\n",
                section("Pure", 0, 4));
        assertParses(
                "Class model::domain::MyClass\r\n" +
                        "{\r" +
                        "    name:String[1]\r\n" +
                        "}\n",
                section("Pure", 0, 4));
    }

    @Test
    public void testExplicitSection()
    {
        assertParses(
                "###Pure\n" +
                        "Class model::domain::MyClass {}",
                section("Pure", 0, 1));
        assertParses(
                "###Pure\r\n" +
                        "Class model::domain::MyClass {}\r\n",
                section("Pure", 0, 2));
        assertParses(
                "###Pure\n" +
                        "\n" +
                        "Class model::domain::MyClass\n" +
                        "{\n" +
                        "    name:String[1]\n" +
                        "}\n",
                section("Pure", 0, 6));
        assertParses(
                "\r\n" +
                        "\t\r\n" +
                        "    \r\n" +
                        "\t\t###Pure\r\n" +
                        "\r\n" +
                        "Class model::domain::MyClass\r\n" +
                        "{\r\n" +
                        "    name:String[1]\r\n" +
                        "}\r\n",
                section("Pure", 3, 9));

        assertParses(
                "###Mapping\n" +
                        "Mapping mappings::MyMapping\n" +
                        "(\n" +
                        ")",
                section("Mapping", 0, 3));
        assertParses(
                "###Mapping\n" +
                        "Mapping mappings::MyMapping\n" +
                        "(\n" +
                        ")\n",
                section("Mapping", 0, 4));
        assertParses(
                "\r\n" +
                        "\n" +
                        "###Mapping\r\n" +
                        "Mapping mappings::MyMapping\n" +
                        "(\r\n" +
                        "\t\n" +
                        ")\r\n",
                section("Mapping", 2, 7));
    }

    @Test
    public void testMultiSection()
    {
        assertParses(
                "Class model::domain::MyFirstClass\n" +
                        "{\n" +
                        "}\n" +
                        "\n" +
                        "###Pure\n" +
                        "\n" +
                        "Class model::domain::MySecondClass\n" +
                        "{\n" +
                        "    name:String[1]\n" +
                        "}\n",
                section("Pure", 0, 3),
                section("Pure", 4, 10));
        assertParses(
                "// Comment at the start of the file\n" +
                        "// Second line of the comment\n" +
                        "\n" +
                        "###Pure\n" +
                        "\n" +
                        "Class model::domain::MyClass\n" +
                        "{\n" +
                        "    name:String[1]\n" +
                        "}\n",
                section("Pure", 0, 2),
                section("Pure", 3, 9));

        assertParses(
                "\n" +
                        "Class model::domain::MyFirstClass\n" +
                        "{\n" +
                        "}\n" +
                        "\n" +
                        "###Mapping\r\n" +
                        "\r\n" +
                        "Mapping mappings::MyMapping\r\n" +
                        "(\r\n" +
                        ")\r\n" +
                        "\r\n" +
                        "###Pure\n" +
                        "\n" +
                        "Class model::domain::MySecondClass\n" +
                        "{\n" +
                        "    name:String[1]\n" +
                        "}\n",
                section("Pure", 0, 4),
                section("Mapping", 5, 10),
                section("Pure", 11, 17));
    }

    @Test
    public void testMultiEmptySections()
    {
        assertParses(
                "###Pure\n" +
                        "###Mapping",
                section("Pure", 0, 0),
                section("Mapping", 1, 1));
        assertParses(
                "###Pure\n" +
                        "###Mapping\n",
                section("Pure", 0, 0),
                section("Mapping", 1, 2));
        assertParses(
                "###Pure\r\n" +
                        "###Mapping\r\n",
                section("Pure", 0, 0),
                section("Mapping", 1, 2));
    }

    private void assertParses(String text, Section... sections)
    {
        LineIndexedText indexedText = LineIndexedText.index(text);
        GrammarSectionIndex index = GrammarSectionIndex.parse(text);
        Assertions.assertEquals(index, GrammarSectionIndex.parse(indexedText));
        Assertions.assertEquals(sections.length, index.getSectionCount());
        Assertions.assertEquals(sections.length, index.getSections().size());
        for (int i = 0; i < index.getSectionCount(); i++)
        {
            GrammarSection actualSection = index.getSection(i);
            Section expectedSection = sections[i];

            Assertions.assertEquals(
                    List.of(expectedSection.grammar, expectedSection.startLine, expectedSection.endLine),
                    List.of(actualSection.getGrammar(), actualSection.getStartLine(), actualSection.getEndLine()));

            Assertions.assertEquals(indexedText.getLines(expectedSection.startLine, expectedSection.endLine), actualSection.getText());

            for (int line = actualSection.getStartLine(), end = actualSection.getEndLine(); line <= end; line++)
            {
                Assertions.assertEquals(i, index.getSectionNumberAtLine(line), "section " + i + " line " + line);
                Assertions.assertSame(actualSection, index.getSectionAtLine(line), "section " + i + " line " + line);
            }
        }
    }

    private static Section section(String grammar, int startLine, int endLine)
    {
        return new Section(grammar, startLine, endLine);
    }

    private static class Section
    {
        private final String grammar;
        private final int startLine;
        private final int endLine;

        private Section(String grammar, int startLine, int endLine)
        {
            this.grammar = grammar;
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }
}
