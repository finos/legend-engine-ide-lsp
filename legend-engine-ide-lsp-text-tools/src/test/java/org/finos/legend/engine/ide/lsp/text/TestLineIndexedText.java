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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestLineIndexedText
{
    @Test
    public void testEmpty()
    {
        String text = "";
        LineIndexedText indexedText = LineIndexedText.index(text);
        Assertions.assertSame(text, indexedText.getText());
        Assertions.assertEquals(1, indexedText.getLineCount());
        assertValidLineColumnInterval(indexedText);
    }

    @Test
    public void testSingleLine()
    {
        String text = "A single line of text.";
        LineIndexedText indexedText = LineIndexedText.index(text);
        Assertions.assertSame(text, indexedText.getText());
        Assertions.assertEquals(1, indexedText.getLineCount());

        Assertions.assertEquals(0, indexedText.getLineStart(0));
        Assertions.assertEquals(text.length(), indexedText.getLineEnd(0));
        Assertions.assertEquals(text.length(), indexedText.getLineLength(0));
        Assertions.assertEquals(text, indexedText.getLine(0));

        Assertions.assertEquals(0, indexedText.getIndex(0, 0));
        Assertions.assertEquals(5, indexedText.getIndex(0, 5));
        Assertions.assertEquals(text.length() - 1, indexedText.getIndex(0, text.length() - 1));

        Assertions.assertEquals(0, indexedText.getLineNumber(0));
        Assertions.assertEquals(0, indexedText.getLineNumber(5));
        Assertions.assertEquals(0, indexedText.getLineNumber(text.length() - 1));

        Assertions.assertEquals(text, indexedText.getLines(0, 0));

        Assertions.assertEquals("A", indexedText.getInterval(0, 0, 0, 0));
        Assertions.assertEquals("single", indexedText.getInterval(0, 2, 0, 7));
        Assertions.assertEquals("text", indexedText.getInterval(0, 17, 0, 20));
        Assertions.assertEquals("text.", indexedText.getInterval(0, 17, 0, 21));
        Assertions.assertEquals(text, indexedText.getInterval(0, 0, 0, text.length() - 1));

        assertValidLineColumnInterval(indexedText);
    }

    @Test
    public void testMultiLine()
    {
        String line0 = "Multiple lines of text\n";
        String line1 = "with different types\r\n";
        String line2 = "of line breaks\n";
        String line3 = "\n";
        String line4 = "with blank line in the middle";
        String[] lines = {line0, line1, line2, line3, line4};
        String text = line0 + line1 + line2 + line3 + line4;
        int line1Start = line0.length();
        int line2Start = line1Start + line1.length();
        int line3Start = line2Start + line2.length();
        int line4Start = line3Start + line3.length();

        LineIndexedText indexedText = LineIndexedText.index(text);
        Assertions.assertSame(text, indexedText.getText());
        Assertions.assertEquals(5, indexedText.getLineCount());

        assertLine(indexedText, 0, 0, line0);
        assertLine(indexedText, 1, line1Start, line1);
        assertLine(indexedText, 2, line2Start, line2);
        assertLine(indexedText, 3, line3Start, line3);
        assertLine(indexedText, 4, line4Start, line4);

        Assertions.assertEquals(0, indexedText.getIndex(0, 0));
        Assertions.assertEquals(line0.length() + 3, indexedText.getIndex(1, 3));
        Assertions.assertEquals(line0.length() + line1.length() + line2.length() + line3.length() + 13, indexedText.getIndex(4, 13));

        Assertions.assertEquals(line0 + line1, indexedText.getLines(0, 1));
        Assertions.assertEquals(line1, indexedText.getLines(1, 1));
        Assertions.assertEquals(line1 + line2, indexedText.getLines(1, 2));
        Assertions.assertEquals(line1 + line2 + line3, indexedText.getLines(1, 3));
        Assertions.assertEquals(line1 + line2 + line3 + line4, indexedText.getLines(1, 4));
        Assertions.assertEquals(line2 + line3 + line4, indexedText.getLines(2, 4));

        Assertions.assertEquals(" of text\nwith different", indexedText.getInterval(0, 14, 1, 13));
        Assertions.assertEquals(" of text\nwith different types\r\nof li", indexedText.getInterval(0, 14, 2, 4));
        Assertions.assertEquals("\n", indexedText.getInterval(3, 0, 3, 0));
        Assertions.assertEquals(text, indexedText.getInterval(0, 0, 4, line4.length() - 1));

        assertValidLineColumnInterval(indexedText);
    }

    @Test
    public void testEmptyLastLine()
    {
        String line0 = "text with an empty\n";
        String line1 = "last line\n";
        String text = line0 + line1;
        LineIndexedText indexedText = LineIndexedText.index(text);
        Assertions.assertSame(text, indexedText.getText());
        Assertions.assertEquals(3, indexedText.getLineCount());

        assertLine(indexedText, 0, 0, line0);
        assertLine(indexedText, 1, line0.length(), line1);
        assertLine(indexedText, 2, line0.length() + line1.length(), "");

        Assertions.assertEquals(line0 + line1, indexedText.getLines(0, 1));
        Assertions.assertEquals(line1, indexedText.getLines(1, 1));
        Assertions.assertEquals(line1, indexedText.getLines(1, 2));
        Assertions.assertEquals(line0 + line1, indexedText.getLines(0, 2));

        Assertions.assertEquals(5, indexedText.getIndex(0, 5));
        Assertions.assertEquals(line0.length() + 3, indexedText.getIndex(1, 3));
        Assertions.assertEquals(line0.length() + 8, indexedText.getIndex(1, 8));

        Assertions.assertEquals("with an empty\nla", indexedText.getInterval(0, 5, 1, 1));
        Assertions.assertEquals("last line", indexedText.getInterval(1, 0, 1, 8));
        Assertions.assertEquals("last line\n", indexedText.getInterval(1, 0, 1, 9));
        Assertions.assertEquals(text, indexedText.getInterval(0, 0, 1, line1.length() - 1));

        assertValidLineColumnInterval(indexedText);
    }

    private void assertLine(LineIndexedText indexedText, int lineNum, int lineStart, String lineText)
    {
        Assertions.assertEquals(lineText, indexedText.getLine(lineNum), () -> "line " + lineNum);
        Assertions.assertEquals(lineStart, indexedText.getLineStart(lineNum), () -> "line " + lineNum);
        Assertions.assertEquals(lineStart + lineText.length(), indexedText.getLineEnd(lineNum), () -> "line " + lineNum);
        for (int i = 0; i < lineText.length(); i++)
        {
            int col = i;
            int index = lineStart + i;
            Assertions.assertEquals(lineNum, indexedText.getLineNumber(index), () -> "index " + index);
            Assertions.assertEquals(index, indexedText.getIndex(lineNum, col), () -> "line " + lineNum + " col " + col);
        }
    }

    private void assertValidLineColumnInterval(LineIndexedText indexedText)
    {
        // valid line
        Assertions.assertFalse(indexedText.isValidLine(-1));
        Assertions.assertFalse(indexedText.isValidLine(indexedText.getLineCount()));
        for (int line = 0; line < indexedText.getLineCount(); line++)
        {
            Assertions.assertTrue(indexedText.isValidLine(line), Integer.toString(line));
        }

        // valid column
        for (int line = 0; line < indexedText.getLineCount(); line++)
        {
            int length = indexedText.getLineLength(line);
            Assertions.assertFalse(indexedText.isValidColumn(line, -1));
            Assertions.assertFalse(indexedText.isValidColumn(line, length));
            for (int col = 0; col < length; col++)
            {
                Assertions.assertTrue(indexedText.isValidColumn(line, col), line + ":" + col);
            }
        }

        // valid line interval
        for (int startLine = 0; startLine < indexedText.getLineCount(); startLine++)
        {
            Assertions.assertFalse(indexedText.isValidInterval(-1, startLine));
            Assertions.assertFalse(indexedText.isValidInterval(startLine, indexedText.getLineCount()));
            for (int endLine = 0; endLine < indexedText.getLineCount(); endLine++)
            {
                Assertions.assertEquals(startLine <= endLine, indexedText.isValidInterval(startLine, endLine), startLine + "-" + endLine);
            }
        }

        // valid column interval
        for (int line = 0; line < indexedText.getLineCount(); line++)
        {
            int length = indexedText.getLineLength(line);
            Assertions.assertFalse(indexedText.isValidInterval(line, -1, 0));
            Assertions.assertFalse(indexedText.isValidInterval(line, Math.max(1, length - 1), 0));
            Assertions.assertFalse(indexedText.isValidInterval(line, 0, length));
            for (int startCol = 0; startCol < length; startCol++)
            {
                for (int endCol = 0; startCol < length; startCol++)
                {
                    Assertions.assertEquals(startCol <= endCol, indexedText.isValidInterval(line, startCol, endCol), line + ":" + startCol + "-" + endCol);
                }
            }
        }

        // valid interval
        Assertions.assertFalse(indexedText.isValidInterval(-1, 0, 0, 0));
        Assertions.assertFalse(indexedText.isValidInterval(0, 0, indexedText.getLineCount(), 0));
        for (int startLine = 0; startLine < indexedText.getLineCount(); startLine++)
        {
            int startLineLength = indexedText.getLineLength(startLine);
            for (int endLine = 0; endLine < indexedText.getLineCount(); endLine++)
            {
                int endLineLength = indexedText.getLineLength(endLine);
                for (int startCol = 0; startCol < startLineLength; startCol++)
                {
                    for (int endCol = 0; endCol < endLineLength; endCol++)
                    {
                        boolean valid = (startLine < endLine) || ((startLine == endLine) && (startCol <= endCol));
                        Assertions.assertEquals(valid, indexedText.isValidInterval(startLine, startCol, endLine, endCol));
                    }
                }
            }
        }

        // valid index
        Assertions.assertFalse(indexedText.isValidIndex(-1));
        for (int i = 0; i < indexedText.getText().length(); i++)
        {
            Assertions.assertTrue(indexedText.isValidIndex(i), Integer.toString(i));
        }
        Assertions.assertFalse(indexedText.isValidIndex(indexedText.getText().length()));
    }
}
