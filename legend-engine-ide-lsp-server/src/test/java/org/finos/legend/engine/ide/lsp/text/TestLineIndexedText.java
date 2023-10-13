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
    }

    @Test
    public void testMultiLine()
    {
        String line0 = "Multiple lines of text\n";
        String line1 = "with different types\r\n";
        String line2 = "of line breaks\n";
        String line3 = "\n";
        String line4 = "with blank line in the middle";
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

        Assertions.assertEquals(line0 + line1, indexedText.getLines(0, 1));
        Assertions.assertEquals(line1, indexedText.getLines(1, 1));
        Assertions.assertEquals(line1 + line2, indexedText.getLines(1, 2));
        Assertions.assertEquals(line1 + line2 + line3, indexedText.getLines(1, 3));
        Assertions.assertEquals(line1 + line2 + line3 + line4, indexedText.getLines(1, 4));
        Assertions.assertEquals(line2 + line3 + line4, indexedText.getLines(2, 4));
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
}
