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

public class TestTextTools
{
    @Test
    public void testIndexLines_string()
    {
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines(""));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines(" "));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\n"));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\n\t"));
        Assertions.assertArrayEquals(new int[]{0, 2}, TextTools.indexLines("\r\n"));
        Assertions.assertArrayEquals(new int[]{0, 2}, TextTools.indexLines("\r\na"));
        Assertions.assertArrayEquals(new int[]{0, 1, 2}, TextTools.indexLines("\n\n"));
        Assertions.assertArrayEquals(new int[]{0, 2, 4}, TextTools.indexLines("\r\n\r\n"));
        Assertions.assertArrayEquals(new int[]{0, 4, 11, 13, 19, 23, 24, 49}, TextTools.indexLines("the\nquick\r\n\r\nbrown\rfox\r\rjumped over the lazy dog\n"));
    }

    @Test
    public void testIndexLines_string_ignoreEmptyFinalLine()
    {
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("", true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines(" ", true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\n", true));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\n\t", true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\r\n", true));
        Assertions.assertArrayEquals(new int[]{0, 2}, TextTools.indexLines("\r\na", true));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\n\n", true));
        Assertions.assertArrayEquals(new int[]{0, 2}, TextTools.indexLines("\r\n\r\n", true));
        Assertions.assertArrayEquals(new int[]{0, 4, 11, 13, 19, 23, 24}, TextTools.indexLines("the\nquick\r\n\r\nbrown\rfox\r\rjumped over the lazy dog\n", true));
    }

    @Test
    public void testIndexLines_region()
    {
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("", 0, 0));

        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines(" ", 0, 0));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines(" ", 0, 1));

        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\n", 0, 0));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\n", 0, 1));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\n", 1, 1));

        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\n\t", 0, 0));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\n\t", 0, 1));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\n\t", 0, 2));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\n\t", 1, 1));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\n\t", 1, 2));

        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\r\n", 0, 0));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\r\n", 0, 1));
        Assertions.assertArrayEquals(new int[]{0, 2}, TextTools.indexLines("\r\n", 0, 2));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\r\n", 1, 1));
        Assertions.assertArrayEquals(new int[]{1, 2}, TextTools.indexLines("\r\n", 1, 2));

        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\r\na", 0, 0));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\r\na", 0, 1));
        Assertions.assertArrayEquals(new int[]{0, 2}, TextTools.indexLines("\r\na", 0, 2));
        Assertions.assertArrayEquals(new int[]{0, 2}, TextTools.indexLines("\r\na", 0, 3));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\r\na", 1, 1));
        Assertions.assertArrayEquals(new int[]{1, 2}, TextTools.indexLines("\r\na", 1, 2));
        Assertions.assertArrayEquals(new int[]{1, 2}, TextTools.indexLines("\r\na", 1, 3));
        Assertions.assertArrayEquals(new int[]{2}, TextTools.indexLines("\r\na", 2, 2));
        Assertions.assertArrayEquals(new int[]{2}, TextTools.indexLines("\r\na", 2, 3));

        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\n\n", 0, 0));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\n\n", 0, 1));
        Assertions.assertArrayEquals(new int[]{0, 1, 2}, TextTools.indexLines("\n\n", 0, 2));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\n\n", 1, 1));
        Assertions.assertArrayEquals(new int[]{1, 2}, TextTools.indexLines("\n\n", 1, 2));
        Assertions.assertArrayEquals(new int[]{2}, TextTools.indexLines("\n\n", 2, 2));

        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\r\n\r\n", 0, 0));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\r\n\r\n", 0, 1));
        Assertions.assertArrayEquals(new int[]{0, 2}, TextTools.indexLines("\r\n\r\n", 0, 2));
        Assertions.assertArrayEquals(new int[]{0, 2, 3}, TextTools.indexLines("\r\n\r\n", 0, 3));
        Assertions.assertArrayEquals(new int[]{0, 2, 4}, TextTools.indexLines("\r\n\r\n", 0, 4));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\r\n\r\n", 1, 1));
        Assertions.assertArrayEquals(new int[]{1, 2}, TextTools.indexLines("\r\n\r\n", 1, 2));
        Assertions.assertArrayEquals(new int[]{1, 2, 3}, TextTools.indexLines("\r\n\r\n", 1, 3));
        Assertions.assertArrayEquals(new int[]{1, 2, 4}, TextTools.indexLines("\r\n\r\n", 1, 4));
        Assertions.assertArrayEquals(new int[]{2}, TextTools.indexLines("\r\n\r\n", 2, 2));
        Assertions.assertArrayEquals(new int[]{2, 3}, TextTools.indexLines("\r\n\r\n", 2, 3));
        Assertions.assertArrayEquals(new int[]{2, 4}, TextTools.indexLines("\r\n\r\n", 2, 4));
        Assertions.assertArrayEquals(new int[]{3}, TextTools.indexLines("\r\n\r\n", 3, 3));
        Assertions.assertArrayEquals(new int[]{3, 4}, TextTools.indexLines("\r\n\r\n", 3, 4));
        Assertions.assertArrayEquals(new int[]{4}, TextTools.indexLines("\r\n\r\n", 4, 4));

        String qbf = "the\nquick\r\n\r\nbrown\rfox\r\rjumped over the lazy dog\n";
        Assertions.assertArrayEquals(new int[]{0, 4, 11, 13, 19, 23, 24, 49}, TextTools.indexLines(qbf, 0, qbf.length()));
        Assertions.assertArrayEquals(new int[]{1, 4, 11, 13, 19, 23, 24, 49}, TextTools.indexLines(qbf, 1, qbf.length()));
        Assertions.assertArrayEquals(new int[]{2, 4, 11, 13, 19, 23, 24, 49}, TextTools.indexLines(qbf, 2, qbf.length()));
        Assertions.assertArrayEquals(new int[]{3, 4, 11, 13, 19, 23, 24, 49}, TextTools.indexLines(qbf, 3, qbf.length()));
        Assertions.assertArrayEquals(new int[]{4, 11, 13, 19, 23, 24, 49}, TextTools.indexLines(qbf, 4, qbf.length()));
        Assertions.assertArrayEquals(new int[]{4, 11, 13, 19, 23, 24, 49}, TextTools.indexLines(qbf, 4, 49));
        Assertions.assertArrayEquals(new int[]{4, 11, 13, 19, 23, 24}, TextTools.indexLines(qbf, 4, 48));
        Assertions.assertArrayEquals(new int[]{13}, TextTools.indexLines(qbf, 13, 18));
    }

    @Test
    public void testIndexLines_region_ignoreEmptyFinalLine()
    {
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("", 0, 0, true));

        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines(" ", 0, 0, true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines(" ", 0, 1, true));

        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\n", 0, 0, true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\n", 0, 1, true));
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\n", 1, 1, true));

        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\n\t", 0, 0, true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\n\t", 0, 1, true));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\n\t", 0, 2, true));
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\n\t", 1, 1, true));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\n\t", 1, 2, true));

        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\r\n", 0, 0, true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\r\n", 0, 1, true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\r\n", 0, 2, true));
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\r\n", 1, 1, true));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\r\n", 1, 2, true));

        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\r\na", 0, 0, true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\r\na", 0, 1, true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\r\na", 0, 2, true));
        Assertions.assertArrayEquals(new int[]{0, 2}, TextTools.indexLines("\r\na", 0, 3, true));
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\r\na", 1, 1, true));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\r\na", 1, 2, true));
        Assertions.assertArrayEquals(new int[]{1, 2}, TextTools.indexLines("\r\na", 1, 3, true));
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\r\na", 2, 2, true));
        Assertions.assertArrayEquals(new int[]{2}, TextTools.indexLines("\r\na", 2, 3, true));

        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\n\n", 0, 0, true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\n\n", 0, 1, true));
        Assertions.assertArrayEquals(new int[]{0, 1}, TextTools.indexLines("\n\n", 0, 2, true));
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\n\n", 1, 1, true));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\n\n", 1, 2, true));
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\n\n", 2, 2, true));

        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\r\n\r\n", 0, 0, true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\r\n\r\n", 0, 1, true));
        Assertions.assertArrayEquals(new int[]{0}, TextTools.indexLines("\r\n\r\n", 0, 2, true));
        Assertions.assertArrayEquals(new int[]{0, 2}, TextTools.indexLines("\r\n\r\n", 0, 3, true));
        Assertions.assertArrayEquals(new int[]{0, 2}, TextTools.indexLines("\r\n\r\n", 0, 4, true));
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\r\n\r\n", 1, 1, true));
        Assertions.assertArrayEquals(new int[]{1}, TextTools.indexLines("\r\n\r\n", 1, 2, true));
        Assertions.assertArrayEquals(new int[]{1, 2}, TextTools.indexLines("\r\n\r\n", 1, 3, true));
        Assertions.assertArrayEquals(new int[]{1, 2}, TextTools.indexLines("\r\n\r\n", 1, 4, true));
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\r\n\r\n", 2, 2, true));
        Assertions.assertArrayEquals(new int[]{2}, TextTools.indexLines("\r\n\r\n", 2, 3, true));
        Assertions.assertArrayEquals(new int[]{2}, TextTools.indexLines("\r\n\r\n", 2, 4, true));
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\r\n\r\n", 3, 3, true));
        Assertions.assertArrayEquals(new int[]{3}, TextTools.indexLines("\r\n\r\n", 3, 4, true));
        Assertions.assertArrayEquals(new int[]{}, TextTools.indexLines("\r\n\r\n", 4, 4, true));

        String qbf = "the\nquick\r\n\r\nbrown\rfox\r\rjumped over the lazy dog\n";
        Assertions.assertArrayEquals(new int[]{0, 4, 11, 13, 19, 23, 24}, TextTools.indexLines(qbf, 0, qbf.length(), true));
        Assertions.assertArrayEquals(new int[]{1, 4, 11, 13, 19, 23, 24}, TextTools.indexLines(qbf, 1, qbf.length(), true));
        Assertions.assertArrayEquals(new int[]{2, 4, 11, 13, 19, 23, 24}, TextTools.indexLines(qbf, 2, qbf.length(), true));
        Assertions.assertArrayEquals(new int[]{3, 4, 11, 13, 19, 23, 24}, TextTools.indexLines(qbf, 3, qbf.length(), true));
        Assertions.assertArrayEquals(new int[]{4, 11, 13, 19, 23, 24}, TextTools.indexLines(qbf, 4, qbf.length(), true));
        Assertions.assertArrayEquals(new int[]{4, 11, 13, 19, 23, 24}, TextTools.indexLines(qbf, 4, 49, true));
        Assertions.assertArrayEquals(new int[]{4, 11, 13, 19, 23, 24}, TextTools.indexLines(qbf, 4, 48, true));
        Assertions.assertArrayEquals(new int[]{13}, TextTools.indexLines(qbf, 13, 18, true));
    }

    @Test
    public void testCountLines_string()
    {
        Assertions.assertEquals(1, TextTools.countLines(""));
        Assertions.assertEquals(2, TextTools.countLines("\n"));
        Assertions.assertEquals(2, TextTools.countLines("\r\n"));
        Assertions.assertEquals(3, TextTools.countLines("\n\n"));
        Assertions.assertEquals(3, TextTools.countLines("\r\r"));
        Assertions.assertEquals(3, TextTools.countLines("\r\n\r\n"));

        Assertions.assertEquals(1, TextTools.countLines("the quick brown fox jumped over the lazy dog"));
        Assertions.assertEquals(2, TextTools.countLines("the quick brown fox\njumped over the lazy dog"));
        Assertions.assertEquals(2, TextTools.countLines("the quick brown fox\r\njumped over the lazy dog"));
        Assertions.assertEquals(3, TextTools.countLines("the quick brown fox\n\njumped over the lazy dog"));
        Assertions.assertEquals(3, TextTools.countLines("the quick brown fox\r\n\r\njumped over the lazy dog"));
    }

    @Test
    public void testCountLines_string_ignoreEmptyFinalLine()
    {
        Assertions.assertEquals(0, TextTools.countLines("", true));
        Assertions.assertEquals(1, TextTools.countLines("\n", true));
        Assertions.assertEquals(1, TextTools.countLines("\r\n", true));
        Assertions.assertEquals(2, TextTools.countLines("\n\n", true));
        Assertions.assertEquals(2, TextTools.countLines("\r\r", true));
        Assertions.assertEquals(2, TextTools.countLines("\r\n\r\n", true));

        Assertions.assertEquals(1, TextTools.countLines("the quick brown fox jumped over the lazy dog", true));
        Assertions.assertEquals(2, TextTools.countLines("the quick brown fox\njumped over the lazy dog", true));
        Assertions.assertEquals(2, TextTools.countLines("the quick brown fox\r\njumped over the lazy dog", true));
        Assertions.assertEquals(3, TextTools.countLines("the quick brown fox\n\njumped over the lazy dog", true));
        Assertions.assertEquals(3, TextTools.countLines("the quick brown fox\r\n\r\njumped over the lazy dog", true));
        Assertions.assertEquals(2, TextTools.countLines("the quick brown fox\njumped over the lazy dog\n", true));
        Assertions.assertEquals(2, TextTools.countLines("the quick brown fox\r\njumped over the lazy dog\r\n", true));
        Assertions.assertEquals(3, TextTools.countLines("the quick brown fox\n\njumped over the lazy dog\n", true));
        Assertions.assertEquals(3, TextTools.countLines("the quick brown fox\r\n\r\njumped over the lazy dog\r\n", true));
    }

    @Test
    public void testCountLines_region()
    {
        Assertions.assertEquals(1, TextTools.countLines("", 0, 0));

        Assertions.assertEquals(2, TextTools.countLines("\n", 0, 1));
        Assertions.assertEquals(1, TextTools.countLines("\n", 0, 0));
        Assertions.assertEquals(1, TextTools.countLines("\n", 1, 1));

        Assertions.assertEquals(2, TextTools.countLines("\r\n", 0, 2));
        Assertions.assertEquals(2, TextTools.countLines("\r\n", 0, 1));
        Assertions.assertEquals(2, TextTools.countLines("\r\n", 1, 2));
        Assertions.assertEquals(1, TextTools.countLines("\r\n", 1, 1));
        Assertions.assertEquals(1, TextTools.countLines("\r\n", 2, 2));

        Assertions.assertEquals(3, TextTools.countLines("\n\n", 0, 2));
        Assertions.assertEquals(2, TextTools.countLines("\n\n", 0, 1));
        Assertions.assertEquals(2, TextTools.countLines("\n\n", 1, 2));
        Assertions.assertEquals(1, TextTools.countLines("\n\n", 1, 1));
        Assertions.assertEquals(1, TextTools.countLines("\n\n", 2, 2));

        Assertions.assertEquals(3, TextTools.countLines("\r\n\r\n", 0, 4));
        Assertions.assertEquals(3, TextTools.countLines("\r\n\r\n", 1, 4));
        Assertions.assertEquals(3, TextTools.countLines("\r\n\r\n", 0, 3));
        Assertions.assertEquals(3, TextTools.countLines("\r\n\r\n", 1, 3));
        Assertions.assertEquals(2, TextTools.countLines("\r\n\r\n", 2, 4));
        Assertions.assertEquals(2, TextTools.countLines("\r\n\r\n", 3, 4));
        Assertions.assertEquals(1, TextTools.countLines("\r\n\r\n", 4, 4));

        String qbf = "the quick brown fox\r\n\r\njumped over the lazy dog\r\n";
        Assertions.assertEquals(4, TextTools.countLines(qbf, 0, qbf.length()));
        Assertions.assertEquals(3, TextTools.countLines(qbf, 0, 45));
        Assertions.assertEquals(3, TextTools.countLines(qbf, 0, 46));
        Assertions.assertEquals(1, TextTools.countLines(qbf, 0, 19));
        Assertions.assertEquals(1, TextTools.countLines(qbf, 23, 45));
        Assertions.assertEquals(1, TextTools.countLines(qbf, 19, 19));
        Assertions.assertEquals(3, TextTools.countLines(qbf, 16, 29));
        Assertions.assertEquals(3, TextTools.countLines(qbf, 19, 29));
        Assertions.assertEquals(3, TextTools.countLines(qbf, 20, 29));
        Assertions.assertEquals(2, TextTools.countLines(qbf, 21, 29));
    }

    @Test
    public void testCounteLines_region_ignoreEmptyFinalLine()
    {
        Assertions.assertEquals(0, TextTools.countLines("", 0, 0, true));

        Assertions.assertEquals(1, TextTools.countLines("\n", 0, 1, true));
        Assertions.assertEquals(0, TextTools.countLines("\n", 0, 0, true));
        Assertions.assertEquals(0, TextTools.countLines("\n", 1, 1, true));

        Assertions.assertEquals(1, TextTools.countLines("\r\n", 0, 2, true));
        Assertions.assertEquals(1, TextTools.countLines("\r\n", 0, 1, true));
        Assertions.assertEquals(1, TextTools.countLines("\r\n", 1, 2, true));
        Assertions.assertEquals(0, TextTools.countLines("\r\n", 1, 1, true));
        Assertions.assertEquals(0, TextTools.countLines("\r\n", 2, 2, true));

        Assertions.assertEquals(2, TextTools.countLines("\n\n", 0, 2, true));
        Assertions.assertEquals(1, TextTools.countLines("\n\n", 0, 1, true));
        Assertions.assertEquals(1, TextTools.countLines("\n\n", 1, 2, true));
        Assertions.assertEquals(0, TextTools.countLines("\n\n", 2, 2, true));

        Assertions.assertEquals(2, TextTools.countLines("\r\n\r\n", 0, 4, true));
        Assertions.assertEquals(2, TextTools.countLines("\r\n\r\n", 1, 4, true));
        Assertions.assertEquals(2, TextTools.countLines("\r\n\r\n", 0, 3, true));
        Assertions.assertEquals(2, TextTools.countLines("\r\n\r\n", 1, 3, true));
        Assertions.assertEquals(1, TextTools.countLines("\r\n\r\n", 2, 4, true));
        Assertions.assertEquals(1, TextTools.countLines("\r\n\r\n", 3, 4, true));
        Assertions.assertEquals(0, TextTools.countLines("\r\n\r\n", 4, 4, true));

        String qbf = "the quick brown fox\r\n\r\njumped over the lazy dog\r\n";
        Assertions.assertEquals(3, TextTools.countLines(qbf, 0, qbf.length(), true));
        Assertions.assertEquals(3, TextTools.countLines(qbf, 0, 45, true));
        Assertions.assertEquals(3, TextTools.countLines(qbf, 0, 46, true));
        Assertions.assertEquals(1, TextTools.countLines(qbf, 0, 19, true));
        Assertions.assertEquals(1, TextTools.countLines(qbf, 23, 45, true));
        Assertions.assertEquals(0, TextTools.countLines(qbf, 19, 19, true));
        Assertions.assertEquals(3, TextTools.countLines(qbf, 16, 29, true));
        Assertions.assertEquals(3, TextTools.countLines(qbf, 19, 29, true));
        Assertions.assertEquals(3, TextTools.countLines(qbf, 20, 29, true));
        Assertions.assertEquals(2, TextTools.countLines(qbf, 21, 29, true));
    }

    @Test
    public void testIndexOfNonWhitespace()
    {
        Assertions.assertEquals(-1, TextTools.indexOfNonWhitespace("", 0, 0));

        String horizontal = "horizontal whitespace: \t\u00a0\u1680\u180e\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u202f\u205f\u3000";
        Assertions.assertEquals(-1, TextTools.indexOfNonWhitespace(horizontal, 22, horizontal.length()));
        Assertions.assertEquals(11, TextTools.indexOfNonWhitespace(horizontal, 10, horizontal.length()));
        Assertions.assertEquals(-1, TextTools.indexOfNonWhitespace(horizontal, 10, 11));
        Assertions.assertEquals(0, TextTools.indexOfNonWhitespace(horizontal, 0, horizontal.length()));
        Assertions.assertEquals(0, TextTools.indexOfNonWhitespace(horizontal, 0, 1));
        Assertions.assertEquals(-1, TextTools.indexOfNonWhitespace(horizontal, 0, 0));

        String vertical = "vertical whitespace:\n\u000B\f\r\u0085\u2028\u2029";
        Assertions.assertEquals(-1, TextTools.indexOfNonWhitespace(vertical, 20, vertical.length()));
        Assertions.assertEquals(9, TextTools.indexOfNonWhitespace(vertical, 8, vertical.length()));
        Assertions.assertEquals(-1, TextTools.indexOfNonWhitespace(vertical, 8, 9));
        Assertions.assertEquals(0, TextTools.indexOfNonWhitespace(vertical, 0, vertical.length()));
        Assertions.assertEquals(0, TextTools.indexOfNonWhitespace(vertical, 0, 1));
        Assertions.assertEquals(-1, TextTools.indexOfNonWhitespace(vertical, 0, 0));

        String mixed = horizontal + vertical;
        Assertions.assertEquals(horizontal.length(), TextTools.indexOfNonWhitespace(mixed, 22, mixed.length()));
        Assertions.assertEquals(-1, TextTools.indexOfNonWhitespace(mixed, horizontal.length() + 20, mixed.length()));
    }

    @Test
    public void testIndexOfWhitespace()
    {
        Assertions.assertEquals(-1, TextTools.indexOfWhitespace("", 0, 0));

        String horizontal = "horizontal whitespace: \t\u00a0\u1680\u180e\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u202f\u205f\u3000";
        Assertions.assertEquals(10, TextTools.indexOfWhitespace(horizontal, 0, horizontal.length()));
        Assertions.assertEquals(22, TextTools.indexOfWhitespace(horizontal, 11, horizontal.length()));
        for (int i = 22; i < horizontal.length(); i++)
        {
            Assertions.assertEquals(i, TextTools.indexOfWhitespace(horizontal, i, horizontal.length()));
        }

        String vertical = "vertical whitespace:\n\u000B\f\r\u0085\u2028\u2029";
        Assertions.assertEquals(8, TextTools.indexOfWhitespace(vertical, 0, vertical.length()));
        Assertions.assertEquals(20, TextTools.indexOfWhitespace(vertical, 9, vertical.length()));
        for (int i = 20; i < vertical.length(); i++)
        {
            Assertions.assertEquals(i, TextTools.indexOfWhitespace(vertical, i, vertical.length()));
        }

        String mixed = horizontal + vertical;
        Assertions.assertEquals(10, TextTools.indexOfWhitespace(mixed, 0, mixed.length()));
        Assertions.assertEquals(22, TextTools.indexOfWhitespace(mixed, 11, mixed.length()));
        Assertions.assertEquals(horizontal.length() + 8, TextTools.indexOfWhitespace(mixed, horizontal.length(), mixed.length()));
        Assertions.assertEquals(horizontal.length() + 20, TextTools.indexOfWhitespace(mixed, horizontal.length() + 9, mixed.length()));
    }

    @Test
    public void testIsBlank()
    {
        assertIsBlank("", 0, 0);

        String horizontal = "horizontal whitespace: \t\u00a0\u1680\u180e\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u202f\u205f\u3000";
        assertIsNotBlank(horizontal, 0, horizontal.length());
        assertIsNotBlank(horizontal, 21, 23);
        assertIsBlank(horizontal, 22, horizontal.length());
        assertIsBlank(horizontal, 23, 27);

        String vertical = "vertical whitespace:\n\u000B\f\r\u0085\u2028\u2029";
        assertIsNotBlank(vertical, 0, vertical.length());
        assertIsNotBlank(vertical, 19, 21);
        assertIsBlank(vertical, 20, vertical.length());
        assertIsBlank(vertical, 23, 27);

        String mixed = horizontal + vertical;
        assertIsNotBlank(mixed, 0, mixed.length());
        assertIsBlank(mixed, 22, horizontal.length());
        assertIsNotBlank(mixed, horizontal.length(), mixed.length());
        assertIsBlank(mixed, horizontal.length() + 20, mixed.length());

        String qbf = "the quick\tbrown\nfox\rjumps over the lazy dog";
        for (int i = 0; i < qbf.length(); i++)
        {
            assertIsBlank(qbf, i, i);
        }
        assertIsNotBlank(qbf, 0, qbf.length());
        assertIsNotBlank(qbf, 0, 4);
        assertIsNotBlank(qbf, 2, 4);
        assertIsBlank(qbf, 3, 4);
        assertIsBlank(qbf, 9, 10);
        assertIsBlank(qbf, 15, 16);
        assertIsBlank(qbf, 19, 20);
    }

    private void assertIsNotBlank(String string, int start, int end)
    {
        Assertions.assertFalse(TextTools.isBlank(string, start, end));
    }

    private void assertIsBlank(String string, int start, int end)
    {
        Assertions.assertTrue(TextTools.isBlank(string, start, end), () -> "isBlank(\"" + string + "\", " + start + ", " + end + "); non-whitespace index: " + TextTools.indexOfNonWhitespace(string, start, end));
    }
}
