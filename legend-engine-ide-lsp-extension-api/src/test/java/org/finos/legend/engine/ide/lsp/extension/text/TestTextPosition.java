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

package org.finos.legend.engine.ide.lsp.extension.text;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestTextPosition
{
    @Test
    public void testValidation()
    {
        IllegalArgumentException e1 = Assertions.assertThrows(IllegalArgumentException.class, () -> TextPosition.newPosition(-1, 0));
        Assertions.assertEquals("Invalid line: -1", e1.getMessage());

        IllegalArgumentException e2 = Assertions.assertThrows(IllegalArgumentException.class, () -> TextPosition.newPosition(0, -1));
        Assertions.assertEquals("Invalid column: -1", e2.getMessage());
    }

    @Test
    public void testIsBefore()
    {
        TextPosition pos0_3 = TextPosition.newPosition(0, 3);
        TextPosition pos1_2 = TextPosition.newPosition(1, 2);
        TextPosition pos1_3 = TextPosition.newPosition(1, 3);
        TextPosition pos2_0 = TextPosition.newPosition(2, 0);

        Assertions.assertFalse(pos0_3.isBefore(pos0_3));
        Assertions.assertTrue(pos0_3.isBefore(pos1_2));
        Assertions.assertTrue(pos0_3.isBefore(pos1_3));
        Assertions.assertTrue(pos0_3.isBefore(pos2_0));

        Assertions.assertFalse(pos1_2.isBefore(pos0_3));
        Assertions.assertFalse(pos1_2.isBefore(pos1_2));
        Assertions.assertTrue(pos1_2.isBefore(pos1_3));
        Assertions.assertTrue(pos1_2.isBefore(pos2_0));

        Assertions.assertFalse(pos1_3.isBefore(pos0_3));
        Assertions.assertFalse(pos1_3.isBefore(pos1_2));
        Assertions.assertFalse(pos1_3.isBefore(pos1_3));
        Assertions.assertTrue(pos1_3.isBefore(pos2_0));

        Assertions.assertFalse(pos2_0.isBefore(pos0_3));
        Assertions.assertFalse(pos2_0.isBefore(pos1_2));
        Assertions.assertFalse(pos2_0.isBefore(pos1_3));
        Assertions.assertFalse(pos2_0.isBefore(pos2_0));
    }

    @Test
    public void testIsAfter()
    {
        TextPosition pos0_3 = TextPosition.newPosition(0, 3);
        TextPosition pos1_2 = TextPosition.newPosition(1, 2);
        TextPosition pos1_3 = TextPosition.newPosition(1, 3);
        TextPosition pos2_0 = TextPosition.newPosition(2, 0);

        Assertions.assertFalse(pos0_3.isAfter(pos0_3));
        Assertions.assertFalse(pos0_3.isAfter(pos1_2));
        Assertions.assertFalse(pos0_3.isAfter(pos1_3));
        Assertions.assertFalse(pos0_3.isAfter(pos2_0));

        Assertions.assertTrue(pos1_2.isAfter(pos0_3));
        Assertions.assertFalse(pos1_2.isAfter(pos1_2));
        Assertions.assertFalse(pos1_2.isAfter(pos1_3));
        Assertions.assertFalse(pos1_2.isAfter(pos2_0));

        Assertions.assertTrue(pos1_3.isAfter(pos0_3));
        Assertions.assertTrue(pos1_3.isAfter(pos1_2));
        Assertions.assertFalse(pos1_3.isAfter(pos1_3));
        Assertions.assertFalse(pos1_3.isAfter(pos2_0));

        Assertions.assertTrue(pos2_0.isAfter(pos0_3));
        Assertions.assertTrue(pos2_0.isAfter(pos1_2));
        Assertions.assertTrue(pos2_0.isAfter(pos1_3));
        Assertions.assertFalse(pos2_0.isAfter(pos2_0));
    }

    @Test
    public void testEquals()
    {
        TextPosition pos0_3 = TextPosition.newPosition(0, 3);
        TextPosition pos1_2 = TextPosition.newPosition(1, 2);
        TextPosition pos1_3 = TextPosition.newPosition(1, 3);
        TextPosition pos2_0 = TextPosition.newPosition(2, 0);

        Assertions.assertTrue(pos0_3.equals(pos0_3));
        Assertions.assertTrue(pos0_3.equals(TextPosition.newPosition(0, 3)));
        Assertions.assertFalse(pos0_3.equals(pos1_2));
        Assertions.assertFalse(pos0_3.equals(pos1_3));
        Assertions.assertFalse(pos0_3.equals(pos2_0));

        Assertions.assertFalse(pos1_2.equals(pos0_3));
        Assertions.assertTrue(pos1_2.equals(pos1_2));
        Assertions.assertTrue(pos1_2.equals(TextPosition.newPosition(1, 2)));
        Assertions.assertFalse(pos1_2.equals(pos1_3));
        Assertions.assertFalse(pos1_2.equals(pos2_0));

        Assertions.assertFalse(pos1_3.equals(pos0_3));
        Assertions.assertFalse(pos1_3.equals(pos1_2));
        Assertions.assertTrue(pos1_3.equals(pos1_3));
        Assertions.assertTrue(pos1_3.equals(TextPosition.newPosition(1, 3)));
        Assertions.assertFalse(pos1_3.equals(pos2_0));

        Assertions.assertFalse(pos2_0.equals(pos0_3));
        Assertions.assertFalse(pos2_0.equals(pos1_2));
        Assertions.assertFalse(pos2_0.equals(pos1_3));
        Assertions.assertTrue(pos2_0.equals(pos2_0));
        Assertions.assertTrue(pos2_0.equals(TextPosition.newPosition(2, 0)));
    }

    @Test
    public void testCompare()
    {
        TextPosition pos0_3 = TextPosition.newPosition(0, 3);
        TextPosition pos1_2 = TextPosition.newPosition(1, 2);
        TextPosition pos1_3 = TextPosition.newPosition(1, 3);
        TextPosition pos2_0 = TextPosition.newPosition(2, 0);

        assertCompare(0, pos0_3, pos0_3);
        assertCompare(-1, pos0_3, pos1_2);
        assertCompare(-1, pos0_3, pos1_3);
        assertCompare(-1, pos0_3, pos2_0);

        assertCompare(1, pos1_2, pos0_3);
        assertCompare(0, pos1_2, pos1_2);
        assertCompare(-1, pos1_2, pos1_3);
        assertCompare(-1, pos1_2, pos2_0);

        assertCompare(1, pos1_3, pos0_3);
        assertCompare(1, pos1_3, pos1_2);
        assertCompare(0, pos1_3, pos1_3);
        assertCompare(-1, pos1_3, pos2_0);

        assertCompare(1, pos2_0, pos0_3);
        assertCompare(1, pos2_0, pos1_2);
        assertCompare(1, pos2_0, pos1_3);
        assertCompare(0, pos2_0, pos2_0);
    }

    private void assertCompare(int expected, TextPosition pos1, TextPosition pos2)
    {
        Assertions.assertEquals(expected, TextPosition.compare(pos1, pos2), () -> "TextPosition.compare(" + pos1 + ", " + pos2 + ") == " + expected);
        Assertions.assertEquals(expected, pos1.compareTo(pos2), () -> pos1 + ".compareTo(" + pos2 + ") == " + expected);
    }

    @Test
    public void testToString()
    {
        Assertions.assertEquals("0:0", TextPosition.newPosition(0, 0).toString(true));
        Assertions.assertEquals("TextPosition{line=0 col=0}", TextPosition.newPosition(0, 0).toString(false));

        Assertions.assertEquals("25:17", TextPosition.newPosition(25, 17).toString(true));
        Assertions.assertEquals("TextPosition{line=25 col=17}", TextPosition.newPosition(25, 17).toString(false));
    }

    @Test
    public void testToCompactString()
    {
        Assertions.assertEquals("0:0", TextPosition.newPosition(0, 0).toCompactString());
        Assertions.assertEquals("0:0", TextPosition.newPosition(0, 0).toCompactString(0, 0));
        Assertions.assertEquals("1:0", TextPosition.newPosition(0, 0).toCompactString(1, 0));
        Assertions.assertEquals("0:1", TextPosition.newPosition(0, 0).toCompactString(0, 1));
        Assertions.assertEquals("1:1", TextPosition.newPosition(0, 0).toCompactString(1, 1));

        Assertions.assertEquals("25:17", TextPosition.newPosition(25, 17).toString(true));
        Assertions.assertEquals("25:17", TextPosition.newPosition(25, 17).toCompactString());
        Assertions.assertEquals("25:17", TextPosition.newPosition(25, 17).toCompactString(0, 0));
        Assertions.assertEquals("26:17", TextPosition.newPosition(25, 17).toCompactString(1, 0));
        Assertions.assertEquals("25:18", TextPosition.newPosition(25, 17).toCompactString(0, 1));
        Assertions.assertEquals("26:18", TextPosition.newPosition(25, 17).toCompactString(1, 1));
    }
}
